package com.novaterm.core.mcp.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityPolicyTest {

    // ── Origin validation ────────────────────────────────────

    @Test
    fun `localhost IPv4 is allowed`() {
        assertTrue(SecurityPolicy.isAllowedOrigin("127.0.0.1"))
    }

    @Test
    fun `localhost IPv6 short is allowed`() {
        assertTrue(SecurityPolicy.isAllowedOrigin("::1"))
    }

    @Test
    fun `localhost IPv6 full is allowed`() {
        assertTrue(SecurityPolicy.isAllowedOrigin("0:0:0:0:0:0:0:1"))
    }

    @Test
    fun `localhost string is allowed`() {
        assertTrue(SecurityPolicy.isAllowedOrigin("localhost"))
    }

    @Test
    fun `remote IP is blocked`() {
        assertFalse(SecurityPolicy.isAllowedOrigin("192.168.1.100"))
    }

    @Test
    fun `public IP is blocked`() {
        assertFalse(SecurityPolicy.isAllowedOrigin("8.8.8.8"))
    }

    // ── Blocked commands ─────────────────────────────────────

    @Test
    fun `rm -rf root is blocked`() {
        assertTrue(SecurityPolicy.isBlockedCommand("rm -rf /"))
    }

    @Test
    fun `rm -rf wildcard is blocked`() {
        assertTrue(SecurityPolicy.isBlockedCommand("rm -rf /*"))
    }

    @Test
    fun `fork bomb is blocked`() {
        assertTrue(SecurityPolicy.isBlockedCommand(":() { :|:& }; :"))
    }

    @Test
    fun `fork bomb compact is blocked`() {
        assertTrue(SecurityPolicy.isBlockedCommand(":(){ :|:&};:"))
    }

    @Test
    fun `mkfs is blocked`() {
        assertTrue(SecurityPolicy.isBlockedCommand("mkfs.ext4 /dev/sda"))
    }

    @Test
    fun `normal command is allowed`() {
        assertFalse(SecurityPolicy.isBlockedCommand("ls -la"))
    }

    @Test
    fun `apt install is allowed`() {
        assertFalse(SecurityPolicy.isBlockedCommand("apt install nodejs"))
    }

    @Test
    fun `rm with specific path is allowed`() {
        assertFalse(SecurityPolicy.isBlockedCommand("rm -rf /tmp/test"))
    }

    // ── Blocked paths ────────────────────────────────────────

    @Test
    fun `system path is blocked`() {
        assertTrue(SecurityPolicy.isBlockedPath("/system/bin/sh"))
    }

    @Test
    fun `proc path is blocked`() {
        assertTrue(SecurityPolicy.isBlockedPath("/proc/self/maps"))
    }

    @Test
    fun `vendor path is blocked`() {
        assertTrue(SecurityPolicy.isBlockedPath("/vendor/lib"))
    }

    @Test
    fun `home path is allowed`() {
        assertFalse(SecurityPolicy.isBlockedPath("/data/data/com.nvterm/files/home/test.txt"))
    }

    @Test
    fun `storage path is allowed`() {
        assertFalse(SecurityPolicy.isBlockedPath("/storage/emulated/0/Download/file.txt"))
    }
}
