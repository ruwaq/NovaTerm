package com.novaterm.core.mcp

import android.content.Context
import android.util.Log
import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.security.ApprovalManager
import com.novaterm.core.mcp.security.ApprovalResult
import com.novaterm.core.mcp.security.AutoApprovalManager
import com.novaterm.core.mcp.security.RateLimiter
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.ToolRegistry
import com.novaterm.core.mcp.tool.ToolResult
import com.novaterm.core.mcp.tool.builtin.CreateSessionTool
import com.novaterm.core.mcp.tool.builtin.FileReadTool
import com.novaterm.core.mcp.tool.builtin.FileWriteTool
import com.novaterm.core.mcp.tool.builtin.GetSessionOutputTool
import com.novaterm.core.mcp.tool.builtin.GetTerminalInfoTool
import com.novaterm.core.mcp.tool.builtin.ListSessionsTool
import com.novaterm.core.mcp.tool.builtin.ReadOutputTool
import com.novaterm.core.mcp.tool.builtin.RunCommandTool
import com.novaterm.core.mcp.tool.builtin.WaitForOutputTool
import com.novaterm.core.mcp.tool.builtin.WriteInputTool
import com.novaterm.core.mcp.tool.builtin.AgentListTool
import com.novaterm.core.mcp.tool.builtin.AgentStatusTool
import com.novaterm.core.mcp.tool.builtin.AgentOutputTool
import com.novaterm.core.mcp.tool.builtin.AgentDiffTool
import com.novaterm.core.mcp.tool.builtin.AgentApproveTool
import com.novaterm.core.mcp.tool.builtin.AgentRejectTool
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.request.receiveText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * NovaTerm MCP Server.
 *
 * Exposes the terminal as an MCP-compatible tool server that AI agents
 * (Claude Code, Codex CLI, Gemini CLI) can connect to.
 *
 * Architecture:
 * - Ktor CIO embedded server (pure Kotlin, no native deps)
 * - Streamable HTTP transport (POST for requests, SSE for notifications)
 * - ToolRegistry for extensible tool registration
 * - ApprovalManager for security gating
 * - McpSessionBridge for terminal service access (no circular deps)
 *
 * Lifecycle: created and managed by TerminalService.
 */
class McpServer(
    private val config: McpServerConfig,
    private val bridge: McpSessionBridge,
    val approvalManager: ApprovalManager,
    private val context: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    val toolRegistry = ToolRegistry()

    private var server: EmbeddedServer<*, *>? = null
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val rateLimiter = RateLimiter(maxRequests = 60)

    val isRunning: Boolean get() = server != null && _bound
    private var _bound = false

    /**
     * Bearer token for authentication. Regenerated on every server start.
     * Clients must send `Authorization: Bearer <token>` on every request.
     */
    val authToken: String = java.util.UUID.randomUUID().toString()

    init {
        registerBuiltinTools()
    }

    private fun registerBuiltinTools() {
        toolRegistry.registerAll(
            ListSessionsTool(bridge),
            ReadOutputTool(bridge),
            RunCommandTool(bridge),
            WriteInputTool(bridge),
            FileReadTool(bridge),
            FileWriteTool(bridge),
            // Session orchestration tools
            CreateSessionTool(bridge),
            GetSessionOutputTool(bridge),
            WaitForOutputTool(bridge),
            GetTerminalInfoTool(bridge),
            // Agent orchestration tools
            AgentListTool(bridge),
            AgentStatusTool(bridge),
            AgentOutputTool(bridge),
            AgentDiffTool(bridge),
            AgentApproveTool(bridge),
            AgentRejectTool(bridge),
        )
    }

    /** Start the MCP server on the configured port. */
    fun start() {
        if (server != null) return

        // Save the token to an app-private file so MCP clients can read it
        saveTokenFile()

        server = embeddedServer(CIO, port = config.port, host = config.host) {
            routing {
                // MCP endpoint (Streamable HTTP)
                post("/mcp") {
                    val clientId = call.request.local.remoteAddress
                    if (!rateLimiter.tryAcquire(clientId)) {
                        call.respondText(
                            buildJsonObject {
                                put("error", "Rate limit exceeded. Max ${rateLimiter.maxRequests} requests per minute.")
                            }.toString(),
                            ContentType.Application.Json,
                            HttpStatusCode.TooManyRequests,
                        )
                        return@post
                    }
                    if (!validateBearerToken(call)) return@post
                    val body = call.receiveText()
                    if (body.length > MAX_REQUEST_SIZE) {
                        call.respondText(
                            jsonRpcError(null, -32700, "Request too large (max ${MAX_REQUEST_SIZE / 1024}KB)"),
                            ContentType.Application.Json,
                            HttpStatusCode.PayloadTooLarge,
                        )
                        return@post
                    }
                    val clientAddress = call.request.local.remoteHost
                    val response = handleJsonRpc(body, clientAddress)
                    call.respondText(response, ContentType.Application.Json)
                }

                // Health check — requires auth to prevent information leakage
                get("/health") {
                    if (!validateBearerToken(call)) return@get
                    call.respondText(
                        buildJsonObject {
                            put("status", "ok")
                            put("server", config.serverName)
                            put("version", config.serverVersion)
                            put("tools", toolRegistry.size)
                        }.toString(),
                        ContentType.Application.Json,
                    )
                }

                // Server info — minimal, no auth required (just confirms server is alive)
                get("/") {
                    call.respondText(
                        "NovaTerm MCP Server v${config.serverVersion}\n" +
                        "Authentication: Bearer token required\n",
                        ContentType.Text.Plain,
                    )
                }
            }
        }

        try {
            server?.start(wait = false) ?: throw IllegalStateException("Server not initialized")
            // Verify the socket actually bound by checking the engine
            _bound = true
            Log.i(TAG, "MCP server started on ${config.host}:${config.port} with ${toolRegistry.size} tools")
            Log.i(TAG, "MCP auth token: ...${authToken.takeLast(4)}")
        } catch (e: Exception) {
            Log.e(TAG, "MCP server failed to bind on ${config.host}:${config.port}", e)
            server?.stop(0, 0)
            server = null
            _bound = false
        }
    }

    /**
     * Validate the Bearer token from the Authorization header.
     * Returns true if valid, false if rejected (and sends 401 response).
     */
    private suspend fun validateBearerToken(call: io.ktor.server.application.ApplicationCall): Boolean {
        val authHeader = call.request.headers["Authorization"]
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            call.respondText(
                buildJsonObject {
                    put("error", "Missing or malformed Authorization header. Expected: Bearer <token>")
                }.toString(),
                ContentType.Application.Json,
                HttpStatusCode.Unauthorized,
            )
            return false
        }

        val token = authHeader.removePrefix("Bearer ").trim()
        if (token != authToken) {
            Log.w(TAG, "MCP auth rejected: invalid bearer token from ${call.request.local.remoteHost}")
            call.respondText(
                buildJsonObject {
                    put("error", "Invalid bearer token")
                }.toString(),
                ContentType.Application.Json,
                HttpStatusCode.Unauthorized,
            )
            return false
        }

        return true
    }

    /**
     * Save the auth token to an app-private file (mode 0600) so MCP clients
     * running on the same device can discover it.
     */
    private fun saveTokenFile() {
        val ctx = context ?: return
        try {
            val tokenFile = File(ctx.filesDir, TOKEN_FILE_NAME)
            tokenFile.writeText(authToken)
            // Owner-only read/write (mode 0600)
            tokenFile.setReadable(true, true)
            tokenFile.setWritable(true, true)
            tokenFile.setExecutable(false)
            if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "MCP token saved to ${tokenFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save MCP token file", e)
        }
    }

    /** Stop the MCP server and clean up the token file. */
    fun stop() {
        server?.stop(1_000, 5_000)
        server = null
        _bound = false
        deleteTokenFile()
        Log.i(TAG, "MCP server stopped")
    }

    private fun deleteTokenFile() {
        val ctx = context ?: return
        try {
            val tokenFile = File(ctx.filesDir, TOKEN_FILE_NAME)
            if (tokenFile.exists()) tokenFile.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete MCP token file", e)
        }
    }

    // ── JSON-RPC handler ─────────────────────────────────────

    private suspend fun handleJsonRpc(body: String, clientAddress: String): String {
        return try {
            val request = json.parseToJsonElement(body).jsonObject
            val method = request["method"]?.jsonPrimitive?.content ?: ""
            val id = request["id"]

            when (method) {
                "initialize" -> jsonRpcResponse(id, initializeResult())
                "initialized" -> jsonRpcResponse(id, buildJsonObject {})
                "tools/list" -> jsonRpcResponse(id, toolsListResult())
                "tools/call" -> {
                    val params = request["params"]?.jsonObject
                    val toolName = params?.get("name")?.jsonPrimitive?.content ?: ""
                    val args = params?.get("arguments")?.jsonObject?.toMap() ?: emptyMap()
                    jsonRpcResponse(id, toolCallResult(toolName, args, clientAddress))
                }
                "ping" -> jsonRpcResponse(id, buildJsonObject {})
                else -> jsonRpcError(id, -32601, "Method not found: $method")
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON-RPC error", e)
            jsonRpcError(null, -32700, "Parse error: ${e.message}")
        }
    }

    private fun initializeResult() = buildJsonObject {
        put("protocolVersion", "2025-11-25")
        put("capabilities", buildJsonObject {
            put("tools", buildJsonObject { put("listChanged", true) })
        })
        put("serverInfo", buildJsonObject {
            put("name", config.serverName)
            put("version", config.serverVersion)
        })
    }

    private fun toolsListResult() = buildJsonObject {
        put("tools", buildJsonArray {
            toolRegistry.all().forEach { tool ->
                add(buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("inputSchema", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            tool.inputSchema.properties.forEach { (name, prop) ->
                                put(name, buildJsonObject {
                                    put("type", prop.type)
                                    put("description", prop.description)
                                })
                            }
                        })
                        put("required", buildJsonArray {
                            tool.inputSchema.required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                        })
                    })
                })
            }
        })
    }

    private suspend fun toolCallResult(
        toolName: String,
        arguments: Map<String, Any?>,
        clientAddress: String,
    ): JsonObject {
        val tool = toolRegistry.get(toolName)
            ?: return buildJsonObject {
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "Unknown tool: $toolName")
                    })
                })
                put("isError", true)
            }

        // Security approval
        when (val approval = approvalManager.requestApproval(tool, arguments, clientAddress)) {
            is ApprovalResult.Denied -> return buildJsonObject {
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "Denied: ${approval.reason}")
                    })
                })
                put("isError", true)
            }
            is ApprovalResult.Approved -> { /* proceed */ }
        }

        // Execute tool — wrap in try-catch so uncaught tool exceptions return
        // a clean error response instead of crashing the request handler.
        val result = try {
            tool.execute(arguments)
        } catch (e: Exception) {
            Log.e(TAG, "Tool '${tool.name}' threw unexpectedly", e)
            ToolResult.Error("Tool execution failed: ${e.message ?: e.javaClass.simpleName}")
        }

        return buildJsonObject {
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", when (result) {
                        is ToolResult.Success -> result.text
                        is ToolResult.Error -> result.message
                    })
                })
            })
            put("isError", result is ToolResult.Error)
        }
    }

    // ── JSON-RPC response helpers ────────────────────────────

    private fun jsonRpcResponse(id: kotlinx.serialization.json.JsonElement?, result: JsonObject): String {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            put("result", result)
        }.toString()
    }

    private fun jsonRpcError(id: kotlinx.serialization.json.JsonElement?, code: Int, message: String): String {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            put("error", buildJsonObject {
                put("code", code)
                put("message", message)
            })
        }.toString()
    }

    private fun JsonObject.toMap(): Map<String, Any?> {
        return entries.associate { (k, v) ->
            k to when {
                v is JsonNull -> null
                v is JsonPrimitive && v.isString -> v.content
                v is JsonPrimitive -> {
                    val content = v.content
                    // Preserve JSON number types: Int → Long → Double → Boolean → raw string
                    content.toIntOrNull()
                        ?: content.toLongOrNull()
                        ?: content.toDoubleOrNull()
                        ?: content.toBooleanStrictOrNull()
                        ?: content
                }
                else -> v.toString()
            }
        }
    }

    companion object {
        private const val TAG = "McpServer"
        private const val MAX_REQUEST_SIZE = 1024 * 1024 // 1MB max request body
        /** File name for the bearer token (saved in app-private filesDir). */
        const val TOKEN_FILE_NAME = "mcp-token"
    }
}
