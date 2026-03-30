package com.novaterm.core.mcp.tool

import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ToolRegistryTest {

    private fun fakeTool(name: String, risk: RiskLevel = RiskLevel.SAFE) = object : McpTool {
        override val name = name
        override val description = "Test tool"
        override val inputSchema = InputSchema(emptyMap())
        override val riskLevel = risk
        override suspend fun execute(arguments: Map<String, Any?>) = ToolResult.Success("ok")
    }

    @Test
    fun `register and retrieve tool`() {
        val registry = ToolRegistry()
        val tool = fakeTool("test_tool")
        registry.register(tool)
        assertEquals(tool, registry.get("test_tool"))
    }

    @Test
    fun `get returns null for unknown tool`() {
        val registry = ToolRegistry()
        assertNull(registry.get("nonexistent"))
    }

    @Test
    fun `all returns registered tools`() {
        val registry = ToolRegistry()
        registry.register(fakeTool("a"))
        registry.register(fakeTool("b"))
        registry.register(fakeTool("c"))
        assertEquals(3, registry.all().size)
        assertEquals(3, registry.size)
    }

    @Test
    fun `registerAll adds multiple tools`() {
        val registry = ToolRegistry()
        registry.registerAll(fakeTool("x"), fakeTool("y"))
        assertEquals(2, registry.size)
    }

    @Test
    fun `unregister removes tool`() {
        val registry = ToolRegistry()
        registry.register(fakeTool("removable"))
        assertNotNull(registry.get("removable"))
        registry.unregister("removable")
        assertNull(registry.get("removable"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate registration throws`() {
        val registry = ToolRegistry()
        registry.register(fakeTool("dup"))
        registry.register(fakeTool("dup"))
    }

    @Test
    fun `unregister nonexistent returns null`() {
        val registry = ToolRegistry()
        assertNull(registry.unregister("ghost"))
    }
}
