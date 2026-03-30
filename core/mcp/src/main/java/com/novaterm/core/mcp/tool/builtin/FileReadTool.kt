package com.novaterm.core.mcp.tool.builtin

import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.security.SecurityPolicy
import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.PropertySchema
import com.novaterm.core.mcp.tool.ToolResult

/** Read file contents from the terminal's filesystem. */
class FileReadTool(
    private val bridge: McpSessionBridge,
) : McpTool {

    override val name = "file_read"
    override val description = "Read the contents of a file. Paths are relative to home directory unless absolute."
    override val riskLevel = RiskLevel.SAFE

    override val inputSchema = InputSchema(
        properties = mapOf(
            "path" to PropertySchema("string", "File path (absolute or relative to ~)"),
        ),
        required = listOf("path"),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val rawPath = arguments["path"] as? String
            ?: return ToolResult.Error("Missing required parameter: path")

        // Resolve relative paths against home
        val path = if (rawPath.startsWith("/")) rawPath
        else if (rawPath.startsWith("~/")) bridge.homePath() + rawPath.removePrefix("~")
        else bridge.homePath() + "/" + rawPath

        if (SecurityPolicy.isBlockedPath(path)) {
            return ToolResult.Error("Path blocked by security policy: $path")
        }

        val content = bridge.readFile(path)
            ?: return ToolResult.Error("File not found or not readable: $path")

        return ToolResult.Success(content)
    }
}
