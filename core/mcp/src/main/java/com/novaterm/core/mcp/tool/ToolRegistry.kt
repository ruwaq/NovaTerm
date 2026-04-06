package com.novaterm.core.mcp.tool

import android.util.Log

/**
 * Extensible registry for MCP tools.
 *
 * Tools can be registered at startup (built-in) or dynamically
 * at runtime (plugins, user scripts). Thread-safe via synchronized.
 */
class ToolRegistry {

    private val tools = LinkedHashMap<String, McpTool>()

    /** Register a tool. Throws if name is already taken. */
    @Synchronized
    fun register(tool: McpTool) {
        require(tool.name !in tools) { "Tool '${tool.name}' already registered" }
        tools[tool.name] = tool
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "Registered tool: ${tool.name} (${tool.riskLevel})")
    }

    /** Register multiple tools at once. */
    @Synchronized
    fun registerAll(vararg toolList: McpTool) {
        toolList.forEach { register(it) }
    }

    /** Unregister a tool by name. Returns the removed tool or null. */
    @Synchronized
    fun unregister(name: String): McpTool? = tools.remove(name)

    /** Get a tool by name. */
    @Synchronized
    fun get(name: String): McpTool? = tools[name]

    /** Get all registered tools (snapshot). */
    @Synchronized
    fun all(): List<McpTool> = tools.values.toList()

    /** Number of registered tools. */
    val size: Int @Synchronized get() = tools.size

    companion object {
        private const val TAG = "McpToolRegistry"
    }
}
