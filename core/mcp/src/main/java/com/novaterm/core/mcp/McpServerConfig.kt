package com.novaterm.core.mcp

/**
 * Configuration for the MCP server.
 *
 * All fields have safe defaults. The server is opt-in (disabled by default).
 * Bind to localhost only — remote access requires explicit ADB port forwarding
 * or network configuration by the user.
 */
data class McpServerConfig(
    val enabled: Boolean = false,
    val port: Int = DEFAULT_PORT,
    val host: String = DEFAULT_HOST,
    val serverName: String = "novaterm-mcp",
    val serverVersion: String = "0.1.0",
    val requireApproval: Boolean = true,
) {
    companion object {
        const val DEFAULT_PORT = 8080
        const val DEFAULT_HOST = "127.0.0.1"
    }
}
