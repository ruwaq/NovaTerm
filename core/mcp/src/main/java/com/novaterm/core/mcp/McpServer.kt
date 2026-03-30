package com.novaterm.core.mcp

import android.util.Log
import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.security.ApprovalManager
import com.novaterm.core.mcp.security.ApprovalResult
import com.novaterm.core.mcp.security.AutoApprovalManager
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.ToolRegistry
import com.novaterm.core.mcp.tool.ToolResult
import com.novaterm.core.mcp.tool.builtin.FileReadTool
import com.novaterm.core.mcp.tool.builtin.FileWriteTool
import com.novaterm.core.mcp.tool.builtin.ListSessionsTool
import com.novaterm.core.mcp.tool.builtin.ReadOutputTool
import com.novaterm.core.mcp.tool.builtin.RunCommandTool
import com.novaterm.core.mcp.tool.builtin.WriteInputTool
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    val toolRegistry = ToolRegistry()
    var approvalManager: ApprovalManager = AutoApprovalManager()
        private set

    private var server: EmbeddedServer<*, *>? = null
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    val isRunning: Boolean get() = server != null

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
        )
    }

    /** Start the MCP server on the configured port. */
    fun start() {
        if (server != null) return

        server = embeddedServer(CIO, port = config.port, host = config.host) {
            routing {
                // MCP endpoint (Streamable HTTP)
                post("/mcp") {
                    val body = call.receiveText()
                    val clientAddress = call.request.local.remoteAddress
                    val response = handleJsonRpc(body, clientAddress)
                    call.respondText(response, ContentType.Application.Json)
                }

                // Health check
                get("/health") {
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

                // Server info
                get("/") {
                    call.respondText(
                        "NovaTerm MCP Server v${config.serverVersion}\n" +
                        "Endpoint: POST /mcp\n" +
                        "Health: GET /health\n" +
                        "Tools: ${toolRegistry.size}\n",
                        ContentType.Text.Plain,
                    )
                }
            }
        }.start(wait = false)

        Log.i(TAG, "MCP server started on ${config.host}:${config.port} with ${toolRegistry.size} tools")
    }

    /** Stop the MCP server. */
    fun stop() {
        server?.stop(1_000, 5_000)
        server = null
        Log.i(TAG, "MCP server stopped")
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

        // Execute tool
        val result = tool.execute(arguments)

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
                v is kotlinx.serialization.json.JsonPrimitive && v.isString -> v.content
                v is kotlinx.serialization.json.JsonPrimitive -> v.content.toIntOrNull() ?: v.content.toLongOrNull() ?: v.content
                else -> v.toString()
            }
        }
    }

    companion object {
        private const val TAG = "McpServer"
    }
}
