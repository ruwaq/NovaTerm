package com.novaterm.core.common.contract

/**
 * Contract for locating and configuring the shell executable.
 * Separates shell discovery from session management.
 */
interface ShellProvider {

    fun findShell(): String

    fun buildEnvironment(extraVars: Map<String, String> = emptyMap()): Array<String>

    fun defaultWorkingDirectory(): String
}
