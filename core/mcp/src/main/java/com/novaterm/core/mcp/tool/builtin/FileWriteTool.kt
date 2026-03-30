package com.novaterm.core.mcp.tool.builtin

import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.security.SecurityPolicy
import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.PropertySchema
import com.novaterm.core.mcp.tool.ToolResult

/** Write content to a file on the terminal's filesystem. */
class FileWriteTool(
    private val bridge: McpSessionBridge,
) : McpTool {

    override val name = "file_write"
    override val description = "Write content to a file. Creates the file if it doesn't exist. Paths relative to ~."
    override val riskLevel = RiskLevel.DANGEROUS

    override val inputSchema = InputSchema(
        properties = mapOf(
            "path" to PropertySchema("string", "File path (absolute or relative to ~)"),
            "content" to PropertySchema("string", "Content to write to the file"),
        ),
        required = listOf("path", "content"),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val rawPath = arguments["path"] as? String
            ?: return ToolResult.Error("Missing required parameter: path")
        val content = arguments["content"] as? String
            ?: return ToolResult.Error("Missing required parameter: content")

        val path = if (rawPath.startsWith("/")) rawPath
        else if (rawPath.startsWith("~/")) bridge.homePath() + rawPath.removePrefix("~")
        else bridge.homePath() + "/" + rawPath

        if (SecurityPolicy.isBlockedPath(path)) {
            return ToolResult.Error("Path blocked by security policy: $path")
        }

        val success = bridge.writeFile(path, content)
        return if (success) {
            ToolResult.Success("Written ${content.length} bytes to $path")
        } else {
            ToolResult.Error("Failed to write to $path")
        }
    }
}
