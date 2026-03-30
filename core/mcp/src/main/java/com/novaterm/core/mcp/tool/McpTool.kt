package com.novaterm.core.mcp.tool

import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel

/**
 * Contract for a registerable MCP tool.
 *
 * Tools are the primary way AI agents interact with NovaTerm.
 * Each tool has a name, description, JSON Schema for input,
 * a risk level for security approval, and an execute function.
 *
 * New tools can be added by implementing this interface and
 * registering with [ToolRegistry].
 */
interface McpTool {

    /** Unique tool name (e.g., "run_command"). Must be snake_case. */
    val name: String

    /** Human-readable description shown to the AI agent. */
    val description: String

    /** JSON Schema defining the tool's input parameters. */
    val inputSchema: InputSchema

    /** Risk level determines whether user approval is needed. */
    val riskLevel: RiskLevel

    /** Execute the tool with the given arguments. */
    suspend fun execute(arguments: Map<String, Any?>): ToolResult
}

/** Structured input schema for MCP tool registration. */
data class InputSchema(
    val properties: Map<String, PropertySchema>,
    val required: List<String> = emptyList(),
)

data class PropertySchema(
    val type: String,
    val description: String,
    val enum: List<String>? = null,
    val default: Any? = null,
)

/** Result of a tool execution. */
sealed interface ToolResult {
    /** Successful result with text content. */
    data class Success(val text: String) : ToolResult

    /** Error result. */
    data class Error(val message: String) : ToolResult
}
