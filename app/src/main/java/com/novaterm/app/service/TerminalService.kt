package com.novaterm.app.service

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.novaterm.app.BuildConfig
import com.novaterm.app.NovaTermApp
import com.novaterm.app.R
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import com.novaterm.app.receiver.BootReceiver

/**
 * Data class to hold UI state that needs persistence across sessions
 */
data class TerminalViewModelUIState(
    val currentSessionIndex: Int = 0,
    val sessionNames: Map<Int, String> = emptyMap(),
    val focusedPaneSession: Int = -1
)
import com.novaterm.core.mcp.McpServer
import com.novaterm.core.mcp.McpServerConfig
import com.novaterm.core.mcp.security.ApprovalRequest
import com.novaterm.core.mcp.security.InteractiveApprovalManager
import com.novaterm.core.mcp.security.SecurityPolicy
import com.novaterm.core.session.manager.AndroidShellProvider
import com.novaterm.core.session.persistence.SessionMetadata
import com.novaterm.core.session.persistence.SessionStore
import com.novaterm.core.llm.GemmaEngine
import com.novaterm.core.llm.LlmConfig
import com.novaterm.core.llm.LlmEngine
import com.novaterm.core.llm.ModelCatalog
import com.novaterm.core.llm.ModelManager
import com.novaterm.core.mcp.prediction.PredictionEngine
import com.novaterm.core.session.persistence.db.BlockStore
import com.novaterm.feature.terminal.semantic.SemanticZoneTracker
import com.novaterm.terminal.TerminalEmulator
import com.novaterm.terminal.TerminalSession
import com.novaterm.terminal.TerminalSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Foreground service managing terminal sessions.
 *
 * Manages terminal sessions with [AndroidShellProvider] for shell discovery,
 * [BlockStore] for structured persistence, and [SessionStore] for metadata.
 * Exposes session list as [StateFlow] so the UI layer can observe reactively.
 */
class TerminalService : Service() {

    // ── Binder ─────────────────────────────────────────────

    inner class LocalBinder : Binder() {
        val service: TerminalService get() = this@TerminalService
    }

    private val binder = LocalBinder()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Core delegates ────────────────────────────────────

    private lateinit var shellProvider: AndroidShellProvider
    private lateinit var persistence: SessionPersistenceManager
    lateinit var blockStore: BlockStore
        private set
    lateinit var predictionEngine: PredictionEngine
        private set
    val predictionEngineReady: Boolean get() = ::predictionEngine.isInitialized

    // ── Session state (observable) ─────────────────────────

    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    val sessionCount: Int get() = _sessions.value.size

    /**
     * Per-session screen update callbacks, keyed by session handle (mHandle).
     * Each TerminalView registers its own callback so ALL tabs get updates,
     * not just the last one created.
     */
    private val screenUpdateCallbacks = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()

    /** Register a screen update callback for a specific session.
     *  Immediately invokes the callback to render any buffered content
     *  that arrived before the view was composed (fixes session 2+ freeze).
     *
     *  @param sessionHandle The session handle to register the callback for
     *  @param callback The callback to invoke when screen content changes
     *  @return A cleanup function that should be called when the callback is no longer needed
     */
    fun registerScreenCallback(sessionHandle: String, callback: () -> Unit): () -> Unit {
        val oldCallback = screenUpdateCallbacks[sessionHandle]
        screenUpdateCallbacks[sessionHandle] = callback

        // Force immediate redraw — the session may have produced output
        // before this callback was registered (race between PTY I/O and Compose).
        mainHandler.post(callback)

        // Return a cleanup function
        return {
            screenUpdateCallbacks.remove(sessionHandle)
            oldCallback?.let { oldCallback() } // Invoke old callback one last time before removal
        }
    }

    /** Unregister a screen update callback (e.g., when tab is disposed). */
    fun unregisterScreenCallback(sessionHandle: String) {
        screenUpdateCallbacks.remove(sessionHandle)
    }

    // ── MCP server ──────────────────────────────────────────

    @Volatile private var mcpServer: McpServer? = null
    private val interactiveApproval = InteractiveApprovalManager()

    /** Current MCP auth token (null when server is not running). */
    val mcpAuthToken: String? get() = mcpServer?.authToken

    /** Pending approval request for DANGEROUS tool calls. UI observes this. */
    val mcpApprovalRequest: StateFlow<ApprovalRequest?> = interactiveApproval.pendingRequest

    // ── On-device LLM (optional) ────────────────────────────

    lateinit var modelManager: ModelManager
        private set
    @Volatile var llmEngine: LlmEngine? = null
        private set

    // Synchronization object for LLM engine access
    private val llmSync = Any()
    val llmEnabled = java.util.concurrent.atomic.AtomicBoolean(false)

    // ── Preferences bridge (atomic for cross-thread safety) ──

    val bellEnabled = java.util.concurrent.atomic.AtomicBoolean(true)
    val useRustBackend = java.util.concurrent.atomic.AtomicBoolean(false)
    val mcpEnabled = java.util.concurrent.atomic.AtomicBoolean(false)
    val mcpPort = java.util.concurrent.atomic.AtomicInteger(McpServerConfig.DEFAULT_PORT)
    val scrollbackLines = java.util.concurrent.atomic.AtomicInteger(TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS)

    // ── Semantic zone trackers (per-session, for command completion notifications) ──

    private val zoneTrackers = java.util.concurrent.ConcurrentHashMap<String, SemanticZoneTracker>()

    /** Minimum command duration (ms) to trigger a completion notification. */
    private val commandNotifyThresholdMs = 5_000L

    // ── Rust engine manager (Phase 2) ──────────────────────

    private val rustEngineManager = RustEngineManager()

    /** Get the Rust engine for a session handle, if one exists. */
    fun getRustEngine(sessionHandle: String) = rustEngineManager.getEngine(sessionHandle)

    // ── Lifecycle flag ──────────────────────────────────────

    @Volatile private var isServiceDestroyed = false

    // ── Notification + lock helpers ────────────────────────

    private lateinit var notifications: NotificationHelper
    private lateinit var wakeLocks: WakeLockManager

    val isWakeLockHeld: Boolean get() = wakeLocks.isHeld

    // ── Clipboard ──────────────────────────────────────────

    private val clipboardManager: ClipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // ── Session client callbacks ───────────────────────────

    private val sessionClient = object : TerminalSessionClient {

        override fun onTextChanged(changedSession: TerminalSession) {
            screenUpdateCallbacks[changedSession.mHandle]?.let { callback ->
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    callback()
                } else {
                    mainHandler.post(callback)
                }
            }
        }

        override fun onTitleChanged(changedSession: TerminalSession) {
            _sessions.update { it.toList() }
            onTextChanged(changedSession)
        }

        override fun onSessionFinished(finishedSession: TerminalSession) {
            zoneTrackers.remove(finishedSession.mHandle)
            _sessions.update { current ->
                current.filter { it !== finishedSession }
            }
            updateNotification()
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
            if (!text.isNullOrEmpty()) {
                clipboardManager.setPrimaryClip(
                    ClipData.newPlainText(getString(R.string.app_name), text)
                )
            }
        }

        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(this@TerminalService).toString()
                session?.emulator?.paste(text)
            }
        }

        override fun getClipboardText(): String? {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                return clip.getItemAt(0).coerceToText(this@TerminalService).toString()
            }
            return null
        }

        override fun onBell(session: TerminalSession) {
            if (!bellEnabled.get()) return

            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE))

            val app = application as? NovaTermApp ?: return
            if (!app.appLifecycle.isInForeground.value) {
                notifications.sendBellNotification(session)
            }
        }

        override fun onColorsChanged(session: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}

        override fun onOsc9Notification(session: TerminalSession, text: String?) {
            if (text.isNullOrEmpty()) return
            val app = application as? NovaTermApp ?: return
            if (!app.appLifecycle.isInForeground.value) {
                notifications.sendOsc9Notification(session, text)
            }
        }

        override fun onOsc133SemanticPrompt(session: TerminalSession, params: String?) {
            if (params.isNullOrEmpty()) return
            if (BuildConfig.DEBUG) {
                // Sanitize OSC 133 data before logging
                val sanitizedParams = sanitizeLogMessage(params ?: "")
                Log.d(TAG, "OSC 133: $sanitizedParams (session=${session.mHandle})")
            }

            val marker = params[0]
            val extra = if (params.length > 2 && params[1] == ';') params.substring(2) else null

            val tracker = zoneTrackers.getOrPut(session.mHandle) {
                SemanticZoneTracker().also { t ->
                    t.onBlockCompleteWithDuration = { command, exitCode, durationMs ->
                        onCommandCompleted(session, command, exitCode, durationMs)
                    }
                }
            }

            val emulator = session.emulator ?: return
            val row = emulator.getCursorRow()
            val col = emulator.getCursorCol()
            tracker.onOsc133(marker, extra, row, col)

            if (marker == 'C' && row >= 0) {
                val screen = emulator.getScreen()
                val bRow = if (row > 0) row - 1 else row
                val lineText = screen.getSelectedText(0, bRow, emulator.mColumns, bRow + 1)
                    ?.trim() ?: ""
                val command = lineText.replace(Regex("^[^$#%]*[$#%]\\s*"), "")
                if (command.isNotBlank()) {
                    tracker.setCurrentCommand(command)
                }
            }
        }

        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Shell PID set: ${sanitizeLogMessage(pid.toString())}")
            }
        }

        override fun getTerminalCursorStyle(): Int =
            TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR

        override fun logError(tag: String?, message: String?) {
            Log.e(tag ?: TAG, message ?: "")
        }

        override fun logWarn(tag: String?, message: String?) {
            Log.w(tag ?: TAG, sanitizeLogMessage(message ?: ""))
        }

        override fun logInfo(tag: String?, message: String?) {
            Log.i(tag ?: TAG, sanitizeLogMessage(message ?: ""))
        }

        override fun logDebug(tag: String?, message: String?) {
            if (BuildConfig.DEBUG) {
                Log.d(tag ?: TAG, sanitizeLogMessage(message ?: ""))
            }
        }

        override fun logVerbose(tag: String?, message: String?) {
            if (BuildConfig.DEBUG) {
                Log.v(tag ?: TAG, sanitizeLogMessage(message ?: ""))
            }
        }

        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
            Log.e(tag ?: TAG, sanitizeLogMessage(message ?: ""), e)
        }

        override fun logStackTrace(tag: String?, e: Exception?) {
            Log.e(tag ?: TAG, "Error", e)
        }

        private fun sanitizeLogMessage(message: String): String {
            // Filter sensitive data from logs to prevent accidental exposure
            // Remove API keys
            val sanitized = message
                .replace(Regex("ANTHROPIC_API_KEY=[a-zA-Z0-9_-]{32,}"), "ANTHROPIC_API_KEY=***")
                .replace(Regex("GOOGLE_API_KEY=[a-zA-Z0-9_-]{32,}"), "GOOGLE_API_KEY=***")
                .replace(Regex("OPENAI_API_KEY=[a-zA-Z0-9_-]{32,}"), "OPENAI_API_KEY=***")
                .replace(Regex("OPENROUTER_API_KEY=[a-zA-Z0-9_-]{32,}"), "OPENROUTER_API_KEY=***")
                .replace(Regex("\b[A-Za-z0-9_-]{32,}\b"), "****") // Remove long tokens
                .replace(Regex("\bhttps?://[^\s]{30,}\b"), "****") // Remove long URLs
                .replace(Regex("\b[A-Za-z0-9_-]+@.*\.[A-Za-z]{2,}\b"), "****@****.***") // Remove emails

            // Remove OSC 133 sensitive data from logs
            return sanitized
                .replace(Regex("OSC 133:.*"), "OSC 133: [redacted]")
                .replace(Regex("command.*[a-zA-Z0-9_-]{32,}"), "command: [redacted]")
    }

    // ── Lifecycle ──────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()

        shellProvider = AndroidShellProvider(this, appVersion = com.novaterm.app.BuildConfig.VERSION_NAME)
        val sessionStore = SessionStore(this)
        blockStore = BlockStore(this)
        predictionEngine = PredictionEngine(filesDir).also { it.load() }
        modelManager = ModelManager(this)
        notifications = NotificationHelper(this, { _sessions.value }, { isWakeLockHeld })
        wakeLocks = WakeLockManager(this)

        persistence = SessionPersistenceManager(
            sessionStore = sessionStore,
            sessionsSnapshot = { _sessions.value.toList() },
            shellFinder = { shellProvider.findShell() },
            defaultCwd = { shellProvider.defaultWorkingDirectory() },
            predictionEngine = { if (::predictionEngine.isInitialized) predictionEngine else null },
            mainHandler = mainHandler,
            serviceScope = serviceScope,
            isServiceDestroyed = { isServiceDestroyed },
        )

        startForeground(notifications.foregroundNotificationId, notifications.buildForegroundNotification())

        persistence.startPeriodicSave()
        restoreSessionUIState()
        startMcpServerIfEnabled()

        // Initialize UI state with current values
        persistence.uiStateConsumer.invoke(TerminalViewModelUIState(
            currentSessionIndex = _activeSessionIndex.value,
            sessionNames = _sessionNames.value,
            focusedPaneSession = _focusedPaneSession.value
        ))
    }

    /**
     * Start or restart the MCP server based on current preference state.
     * Safe to call multiple times — stops existing server first.
     */
    fun restoreSessionUIState() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                // Use the persistence manager's restore function
                persistence.restoreUIState { state ->
                    _activeSessionIndex.value = state.currentSessionIndex
                    _sessionNames.value = state.sessionNames
                    _focusedPaneSession.value = state.focusedPaneSession
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore UI state", e)
            }
        }
    }

    @Synchronized
    fun saveSessionUIState(currentSessionIndex: Int, sessionNames: Map<Int, String>, focusedPaneSession: Int) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                // Update the UI state in the persistence manager
                persistence.uiStateConsumer.invoke(TerminalViewModelUIState(
                    currentSessionIndex = currentSessionIndex,
                    sessionNames = sessionNames,
                    focusedPaneSession = focusedPaneSession
                ))

                // Save the UI state immediately
                persistence.saveSessionMetadata()

                // Also save to blockstore for session restoration
                val snapshot = _sessions.value.toList()
                if (snapshot.isNotEmpty() && currentSessionIndex < snapshot.size) {
                    val session = snapshot[currentSessionIndex]
                    blockStore.saveSession(
                        id = "session_${currentSessionIndex}",
                        tabIndex = currentSessionIndex,
                        tabName = sessionNames[currentSessionIndex] ?: session.title ?: "shell",
                        shell = shellProvider.findShell(),
                        cwd = session.cwd ?: shellProvider.defaultWorkingDirectory()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save UI state", e)
            }
        }
    }

    fun startMcpServerIfEnabled() {
        mcpServer?.stop()
        mcpServer = null

        if (!mcpEnabled.get()) return

        try {
            val config = McpServerConfig(
                enabled = true,
                port = mcpPort.get(),
            )
            val home = shellProvider.defaultWorkingDirectory()
            val rootDir = java.io.File(home).parent ?: home
            val bridge = ServiceMcpBridge(
                sessionsFlow = _sessions,
                serviceScope = serviceScope,
                onCreateSession = { cwd ->
                    if (cwd != null) createSessionInDir(cwd) else createSession()
                },
                onRemoveSession = { index -> removeSession(index) },
                defaultCwd = { shellProvider.defaultWorkingDirectory() },
                prefixPath = { "$rootDir/usr" },
            )
            val server = McpServer(config, bridge, interactiveApproval, context = this@TerminalService)
            server.start()
            mcpServer = server
            Log.i(TAG, "MCP server started on port [redacted]")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MCP server (port: [redacted])", e)
        }
    }

    /**
     * Initialize or release the on-device LLM based on user preference.
     * Safe to call multiple times — releases previous engine first.
     */
    fun updateLlmState() {
        llmEngine?.release()
        llmEngine = null

        if (!llmEnabled.get() || !modelManager.isModelReady()) return

        val modelPath = modelManager.getModelPath() ?: return
        val selectedModel = modelManager.getSelectedModel()
        try {
            val config = LlmConfig(
                modelPath = modelPath,
                modelFamily = selectedModel?.family ?: ModelCatalog.ModelFamily.GEMMA4,
            )
            val engine = GemmaEngine(config, context = applicationContext)
            serviceScope.launch {
                try {
                    val success = engine.initialize()
                    if (success) {
                        llmEngine = engine
                        Log.i(TAG, "LLM engine initialized successfully (model: [redacted])")
                    } else {
                        Log.w(TAG, "LLM initialization failed")
                        engine.release()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "LLM initialization error", e)
                    engine.release()
                }
            }
            Log.i(TAG, "LLM engine loading in background...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create LLM engine", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_EXIT -> {
                val snapshot = _sessions.value.toList()
                _sessions.update { emptyList() }
                snapshot.forEach { session ->
                    zoneTrackers.remove(session.mHandle)
                    session.finishIfRunning()
                }
                zoneTrackers.clear()
                rustEngineManager.destroyAll(snapshot)
                releaseLocks()
                stopSelf()
            }
            ACTION_NEW_SESSION -> {
                createSession()
            }
            ACTION_TOGGLE_WAKELOCK -> {
                toggleWakeLock()
            }
            ACTION_RUN_COMMAND -> {
                val command = intent.getStringExtra("command")
                if (!command.isNullOrBlank()) {
                    // Security: validate command before execution
                    if (command.length > MAX_COMMAND_LENGTH) {
                        Log.w(TAG, "RUN_COMMAND: command exceeds max length (${command.length})")
                    } else {
                        // Use the new sanitizer to escape dangerous characters
                        val sanitized = SecurityPolicy.sanitizeCommand(command)
                        if (sanitized == null) {
                            Log.w(TAG, "RUN_COMMAND: command blocked by security policy")
                        } else {
                            val cwd = intent.getStringExtra("cwd")
                            val session = if (!cwd.isNullOrBlank()) {
                                createSessionInDir(cwd)
                            } else {
                                createSession()
                            }
                            if (session != null) {
                                session.write(sanitized + "\n")
                                Log.i(TAG, "RUN_COMMAND: executed '[redacted]' in session [${session.mHandle.take(8)}...]")
                            } else {
                                Log.e(TAG, "RUN_COMMAND: failed to create session")
                            }
                        }
                    }
                }
                }
            }
            ACTION_LAUNCH_PRESET -> {
                val preset = intent.getStringExtra("preset")
                if (!preset.isNullOrBlank()) {
                    createPresetSession(preset)
                } else {
                    Log.w(TAG, "LAUNCH_PRESET: missing preset extra")
                }
            }
            ACTION_WRITE_INPUT -> {
                val input = intent.getStringExtra("input")
                if (!input.isNullOrBlank()) {
                    // Security: strip newlines to prevent terminal injection
                    // from ACTION_SEND shared text. User must press Enter manually.
                    val sanitized = input.replace(Regex("[\r\n]"), " ")
                    val currentSessions = _sessions.value
                    val session = currentSessions.lastOrNull() ?: createSession()
                    if (session != null) {
                        session.write(sanitized)
                        Log.i(TAG, "WRITE_INPUT: wrote ${sanitized.length} chars to session ${session.mHandle}")
                    } else {
                        Log.e(TAG, "WRITE_INPUT: no session available")
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        persistence.saveSessionMetadata()
        Log.i(TAG, "Task removed, session metadata saved")
    }

    override fun onDestroy() {
        isServiceDestroyed = true
        serviceScope.cancel()
        screenUpdateCallbacks.clear()
        mcpServer?.stop()
        mcpServer = null
        llmEngine?.release()
        llmEngine = null
        persistence.stopPeriodicSave()
        persistence.saveSessionMetadata()
        getSystemService(ShortcutManager::class.java)?.removeAllDynamicShortcuts()
        if (::predictionEngine.isInitialized) predictionEngine.save()
        if (::blockStore.isInitialized) blockStore.close()
        val snapshot = _sessions.value.toList()
        snapshot.forEach { session ->
            zoneTrackers.remove(session.mHandle)
            session.finishIfRunning()
        }
        zoneTrackers.clear()
        rustEngineManager.destroyAll(snapshot)
        releaseLocks()
        super.onDestroy()
    }

    // ── Public API ─────────────────────────────────────────

    fun createSessionInDir(cwd: String): TerminalSession? {
        shellProvider.defaultWorkingDirectory()
        val dir = if (java.io.File(cwd).isDirectory) cwd else shellProvider.defaultWorkingDirectory()
        return createSessionInternal(dir)
    }

    fun createSession(): TerminalSession? {
        if (_sessions.value.size >= MAX_SESSIONS) {
            Log.w(TAG, "Max sessions reached ($MAX_SESSIONS) — refusing to create more")
            return null
        }
        return createSessionInternal(shellProvider.defaultWorkingDirectory())
    }

    fun createPresetSession(preset: String): TerminalSession? {
        val command = PRESET_COMMANDS[preset.lowercase()]
        if (command == null) {
            Log.w(TAG, "Unknown preset: $preset")
            return null
        }
        val session = createSession()
        if (session != null) {
            mainHandler.postDelayed({
                if (session.isRunning) {
                    session.write(command + "\n")
                    Log.i(TAG, "LAUNCH_PRESET: started '[redacted]' in session [${session.mHandle.take(8)}...]")
                } else {
                    Log.w(TAG, "LAUNCH_PRESET: session died before command could be written")
                }
            }, PRESET_COMMAND_DELAY_MS)
        } else {
            Log.e(TAG, "LAUNCH_PRESET: failed to create session for preset '$preset'")
        }
        return session
    }

    private fun createSessionInternal(cwd: String): TerminalSession? {
        return try {
            val shellCmd = shellProvider.shellCommand()
            val shell = shellCmd[0]
            val lastSession = _sessions.value.lastOrNull()
            val rows = try { lastSession?.emulator?.mRows?.toString() } catch (_: Exception) { null } ?: DEFAULT_ROWS.toString()
            val cols = try { lastSession?.emulator?.mColumns?.toString() } catch (_: Exception) { null } ?: DEFAULT_COLS.toString()
            val env = shellProvider.buildEnvironment(mapOf("LINES" to rows, "COLUMNS" to cols))

            Log.i(TAG, "Creating session: cmd=[${shellCmd.size}] commands, cwd=$cwd, size=${rows}x${cols}")

            val session = TerminalSession(
                shell,
                cwd,
                shellCmd,
                env,
                scrollbackLines.get(),
                sessionClient,
            )

            Log.i(TAG, "Session created: pid=[redacted] running=${session.isRunning}")

            // Attach Rust VT engine if enabled (Phase 2 dual-run mode)
            if (useRustBackend.get()) {
                rustEngineManager.attachEngine(session)
            }

            _sessions.update { it + session }
            updateNotification()
            updateBootReceiverState()

            session
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create session", e)
            null
        }
    }

    fun removeSession(index: Int) {
        var removedSession: TerminalSession? = null
        _sessions.update { list ->
            if (index !in list.indices) return@update list
            removedSession = list[index]
            list.filterIndexed { i, _ -> i != index }
        }
        val session = removedSession ?: return

        // Clean up callbacks in a finally block to ensure no leaks
        try {
            session.finishIfRunning()
            screenUpdateCallbacks.remove(session.mHandle)
            zoneTrackers.remove(session.mHandle)
            rustEngineManager.detachEngine(session)
            updateNotification()
            updateBootReceiverState()
            if (_sessions.value.isEmpty()) stopSelf()
        } finally {
            // Ensure callback cleanup even if an error occurs
            screenUpdateCallbacks.remove(session.mHandle)
            zoneTrackers.remove(session.mHandle)
        }
    }

    fun toggleWakeLock() {
        wakeLocks.toggle()
        notifications.scheduleUpdate()
    }

    // ── Session persistence — delegated ─────────────────────

    fun getSavedSessions(): List<SessionMetadata> = persistence.getSavedSessions()
    fun clearSavedSessions() = persistence.clearSavedSessions()

    // ── Boot receiver management ────────────────────────────

    private fun updateBootReceiverState() {
        val hasActiveSessions = _sessions.value.isNotEmpty()
        val component = ComponentName(this, BootReceiver::class.java)
        val newState = if (hasActiveSessions) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        packageManager.setComponentEnabledSetting(
            component, newState, PackageManager.DONT_KILL_APP,
        )
    }

    // ── Lock management — delegated to WakeLockManager ──────

    private fun releaseLocks() = wakeLocks.release()

    // ── Notifications — delegated to NotificationHelper ──────

    private fun onCommandCompleted(
        session: TerminalSession,
        command: String,
        exitCode: Int?,
        durationMs: Long,
    ) {
        if (durationMs < commandNotifyThresholdMs) return
        val app = application as? NovaTermApp ?: return
        if (app.appLifecycle.isInForeground.value) return
        notifications.sendCommandCompletionNotification(session, command, exitCode)
    }

    private fun updateNotification() = notifications.scheduleUpdate()

    companion object {
        private const val TAG = "NovaTerm"

        const val ACTION_EXIT = "com.nvterm.action.EXIT"
        const val ACTION_NEW_SESSION = "com.nvterm.action.NEW_SESSION"
        const val ACTION_TOGGLE_WAKELOCK = "com.nvterm.action.TOGGLE_WAKELOCK"
        const val ACTION_RUN_COMMAND = "com.nvterm.action.RUN_COMMAND"
        const val ACTION_WRITE_INPUT = "com.nvterm.action.WRITE_INPUT"
        const val ACTION_FLOAT = "com.nvterm.action.FLOAT"
        const val ACTION_SWITCH_SESSION = "com.nvterm.SWITCH_SESSION"
        const val ACTION_LAUNCH_PRESET = "com.nvterm.LAUNCH_PRESET"
        /** Max sessions to prevent OOM. Each session uses ~2-4MB (PTY + scrollback buffer). */
        private const val MAX_SESSIONS = 8
        /** Max command length accepted via ACTION_RUN_COMMAND intent. */
        private const val MAX_COMMAND_LENGTH = 4096

        /** Delay (ms) before writing a preset command into a newly created session. */
        private const val PRESET_COMMAND_DELAY_MS = 1_000L
        /** Fallback terminal dimensions when no existing session is available. */
        private const val DEFAULT_ROWS = 40
        private const val DEFAULT_COLS = 120

        /** Map of preset names to commands that should be executed in a new session. */
        private val PRESET_COMMANDS = mapOf(
            "claude" to "claude",
            "gemini" to "gemini",
            "aider" to "aider",
            "opencode" to "opencode",
        )
    }
}
