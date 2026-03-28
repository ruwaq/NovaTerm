package com.novaterm.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpResultTest {

    @Test
    fun `success contains value`() {
        val result: OpResult<Int> = OpResult.Success(42)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `failure has null value`() {
        val result: OpResult<Int> = OpResult.Failure("something failed")
        assertNull(result.getOrNull())
    }

    @Test
    fun `getOrThrow returns value on success`() {
        val result: OpResult<String> = OpResult.Success("hello")
        assertEquals("hello", result.getOrThrow())
    }

    @Test
    fun `getOrThrow throws on failure with cause`() {
        val cause = RuntimeException("root cause")
        val result: OpResult<String> = OpResult.Failure("failed", cause)
        val thrown = assertThrows(RuntimeException::class.java) {
            result.getOrThrow()
        }
        assertEquals("root cause", thrown.message)
    }

    @Test
    fun `getOrThrow throws IllegalStateException when no cause`() {
        val result: OpResult<String> = OpResult.Failure("no cause")
        assertThrows(IllegalStateException::class.java) {
            result.getOrThrow()
        }
    }

    @Test
    fun `onSuccess executes callback`() {
        var captured = 0
        OpResult.Success(99).onSuccess { captured = it }
        assertEquals(99, captured)
    }

    @Test
    fun `onSuccess does not execute on failure`() {
        var executed = false
        OpResult.Failure("err").onSuccess { executed = true }
        assertTrue(!executed)
    }

    @Test
    fun `onFailure executes callback`() {
        var capturedError = ""
        OpResult.Failure("bad input").onFailure { msg, _ -> capturedError = msg }
        assertEquals("bad input", capturedError)
    }

    @Test
    fun `onFailure does not execute on success`() {
        var executed = false
        OpResult.Success(1).onFailure { _, _ -> executed = true }
        assertTrue(!executed)
    }

    @Test
    fun `chaining onSuccess and onFailure`() {
        var successValue = 0
        var failureMsg = ""

        val result: OpResult<Int> = OpResult.Success(10)
        result
            .onSuccess { successValue = it }
            .onFailure { msg, _ -> failureMsg = msg }

        assertEquals(10, successValue)
        assertEquals("", failureMsg)
    }

    @Test
    fun `failure preserves cause`() {
        val cause = IllegalArgumentException("bad arg")
        val result = OpResult.Failure("validation failed", cause)
        assertTrue(result is OpResult.Failure)
        assertEquals(cause, (result as OpResult.Failure).cause)
    }
}
