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
import com.novaterm.app.NovaTermApp
import com.novaterm.app.R
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import com.novaterm.app.receiver.BootReceiver
import com.novaterm.core.common.contract.TerminalEngine
import com.novaterm.core.common.model.TerminalDimensions
import com.novaterm.core.mcp.McpServer
import com.novaterm.core.mcp.McpServerConfig
import com.novaterm.core.mcp.bridge.FileEntry
import com.novaterm.core.mcp.bridge.McpSessionBridge
import com.novaterm.core.mcp.bridge.McpSessionInfo
import com.novaterm.core.session.engine.RustEngine
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
    private lateinit var sessionStore: SessionStore
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
     *  that arrived before the view was composed (fixes session 2+ freeze). */
    fun registerScreenCallback(sessionHandle: String, callback: () -> Unit) {
        screenUpdateCallbacks[sessionHandle] = callback
        // Force immediate redraw — the session may have produced output
        // before this callback was registered (race between PTY I/O and Compose).
        mainHandler.post(callback)
    }

    /** Unregister a screen update callback (e.g., when tab is disposed). */
    fun unregisterScreenCallback(sessionHandle: String) {
        screenUpdateCallbacks.remove(sessionHandle)
    }

    // Legacy callback removed — use registerScreenCallback per session instead

    // ── MCP server ──────────────────────────────────────────

    @Volatile private var mcpServer: McpServer? = null

    /** Current MCP auth token (null when server is not running). */
    val mcpAuthToken: String? get() = mcpServer?.authToken

    // ── On-device LLM (optional) ────────────────────────────

    lateinit var modelManager: ModelManager
        private set
    var llmEngine: LlmEngine? = null
        private set
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

    // ── Rust engine instances (Phase 2) ────────────────────

    private val rustEngines = java.util.concurrent.ConcurrentHashMap<String, TerminalEngine>()
    @Volatile private var rustEngineFactory: RustEngine.Factory? = null

    /** Get the Rust engine for a session handle, if one exists. */
    fun getRustEngine(sessionHandle: String): TerminalEngine? = rustEngines[sessionHandle]

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
            // Per-session callback: only invalidates the TerminalView for this session.
            // Use ?.let to avoid race where callback is removed between get and invoke.
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
                // Use the emulator's paste() method which handles bracketed paste mode
                // (DECSET 2004: wraps with ESC[200~ / ESC[201~) and strips C1 controls.
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

            // Send notification when app is in background
            val app = application as? NovaTermApp ?: return
            if (!app.appLifecycle.isInForeground.value) {
                notifications.sendBellNotification(session)
            }
        }

        override fun onColorsChanged(session: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}

        override fun onOsc9Notification(session: TerminalSession, text: String?) {
            if (text.isNullOrEmpty()) return
            // Convert OSC 9 to Android notification (used by Claude Code, Codex CLI)
            val app = application as? NovaTermApp ?: return
            if (!app.appLifecycle.isInForeground.value) {
                notifications.sendOsc9Notification(session, text)
            }
        }

        override fun onOsc133SemanticPrompt(session: TerminalSession, params: String?) {
            if (params.isNullOrEmpty()) return
            Log.d(TAG, "OSC 133: $params (session=${session.mHandle})")

            // Parse marker and optional params (e.g. "D;0" → marker='D', extra="0")
            val marker = params[0]
            val extra = if (params.length > 2 && params[1] == ';') params.substring(2) else null

            // Get or create per-session zone tracker
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

            // Capture command text on C marker: read the line where B was issued
            if (marker == 'C' && row >= 0) {
                val screen = emulator.getScreen()
                // Command is on the line before the output starts (row - 1, or row 0)
                val bRow = if (row > 0) row - 1 else row
                val lineText = screen.getSelectedText(0, bRow, emulator.mColumns, bRow + 1)
                    ?.trim() ?: ""
                // Strip common prompt prefixes ($ , # , % ) to get just the command
                val command = lineText.replace(Regex("^[^$#%]*[$#%]\\s*"), "")
                if (command.isNotBlank()) {
                    tracker.setCurrentCommand(command)
                }
            }
        }

        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
            Log.d(TAG, "Shell PID set: $pid")
        }

        override fun getTerminalCursorStyle(): Int =
            TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR

        override fun logError(tag: String?, message: String?) {
            Log.e(tag ?: TAG, message ?: "")
        }
        override fun logWarn(tag: String?, message: String?) {
            Log.w(tag ?: TAG, message ?: "")
        }
        override fun logInfo(tag: String?, message: String?) {
            Log.i(tag ?: TAG, message ?: "")
        }
        override fun logDebug(tag: String?, message: String?) {
            Log.d(tag ?: TAG, message ?: "")
        }
        override fun logVerbose(tag: String?, message: String?) {
            Log.v(tag ?: TAG, message ?: "")
        }
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
            Log.e(tag ?: TAG, message, e)
        }
        override fun logStackTrace(tag: String?, e: Exception?) {
            Log.e(tag ?: TAG, "Error", e)
        }
    }

    // ── Lifecycle ──────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()

        shellProvider = AndroidShellProvider(this, appVersion = com.novaterm.app.BuildConfig.VERSION_NAME)
        sessionStore = SessionStore(this)
        blockStore = BlockStore(this)
        predictionEngine = PredictionEngine(filesDir).also { it.load() }
        modelManager = ModelManager(this)
        notifications = NotificationHelper(this, { _sessions.value }, { isWakeLockHeld })
        wakeLocks = WakeLockManager(this)

        startForeground(notifications.foregroundNotificationId, notifications.buildForegroundNotification())

        // Periodic session save (every 30s) for crash protection
        startPeriodicSave()

        // Start MCP server if enabled in preferences
        startMcpServerIfEnabled()
    }

    /**
     * Start or restart the MCP server based on current preference state.
     * Safe to call multiple times — stops existing server first.
     */
    fun startMcpServerIfEnabled() {
        mcpServer?.stop()
        mcpServer = null

        if (!mcpEnabled.get()) return

        try {
            val config = McpServerConfig(
                enabled = true,
                port = mcpPort.get(),
            )
            val bridge = ServiceMcpBridge()
            val server = McpServer(config, bridge, context = this@TerminalService)
            server.start()
            mcpServer = server
            Log.i(TAG, "MCP server started on port ${mcpPort.get()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MCP server", e)
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
            val engine = GemmaEngine(config)
            // Initialize on IO dispatcher — only assign llmEngine AFTER successful init
            serviceScope.launch {
                try {
                    val success = engine.initialize()
                    if (success) {
                        llmEngine = engine
                        Log.i(TAG, "LLM engine initialized successfully")
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

    private val saveRunnable = object : Runnable {
        override fun run() {
            if (isServiceDestroyed) return
            // Run on serviceScope (structured concurrency) instead of raw daemon
            // threads that can be killed mid-write and corrupt the database
            serviceScope.launch(Dispatchers.IO) {
                try {
                    if (_sessions.value.isNotEmpty()) {
                        saveSessionMetadata()
                    }
                    if (::predictionEngine.isInitialized) {
                        predictionEngine.save()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Periodic save error", e)
                }
            }

            if (!isServiceDestroyed) {
                mainHandler.postDelayed(this, SAVE_INTERVAL_MS)
            }
        }
    }

    private fun startPeriodicSave() {
        mainHandler.postDelayed(saveRunnable, SAVE_INTERVAL_MS)
    }

    private fun stopPeriodicSave() {
        mainHandler.removeCallbacks(saveRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_EXIT -> {
                val snapshot = _sessions.value.toList()
                _sessions.update { emptyList() }
                snapshot.forEach { session ->
                    zoneTrackers.remove(session.mHandle)
                    rustEngines.remove(session.mHandle)?.destroy()
                    session.setRawByteInterceptor(null)
                    session.setResizeListener(null)
                    session.finishIfRunning()
                }
                zoneTrackers.clear()
                rustEngines.clear()
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
                    val cwd = intent.getStringExtra("cwd")
                    val session = if (!cwd.isNullOrBlank()) {
                        createSessionInDir(cwd)
                    } else {
                        createSession()
                    }
                    if (session != null) {
                        // Write the command followed by newline to execute it
                        session.write(command + "\n")
                        Log.i(TAG, "RUN_COMMAND: executed '${command.take(80)}' in session ${session.mHandle}")
                    } else {
                        Log.e(TAG, "RUN_COMMAND: failed to create session")
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
                    val currentSessions = _sessions.value
                    val session = currentSessions.lastOrNull() ?: createSession()
                    if (session != null) {
                        session.write(input)
                        Log.i(TAG, "WRITE_INPUT: wrote ${input.length} chars to session ${session.mHandle}")
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
        // User swiped app away — save state before Android may kill us
        saveSessionMetadata()
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
        stopPeriodicSave()
        saveSessionMetadata()
        // Clear dynamic shortcuts since no sessions will be active
        getSystemService(ShortcutManager::class.java)?.removeAllDynamicShortcuts()
        if (::predictionEngine.isInitialized) predictionEngine.save()
        if (::blockStore.isInitialized) blockStore.close()
        val snapshot = _sessions.value.toList()
        snapshot.forEach { session ->
            zoneTrackers.remove(session.mHandle)
            rustEngines.remove(session.mHandle)?.destroy()
            session.setRawByteInterceptor(null)
            session.finishIfRunning()
        }
        zoneTrackers.clear()
        rustEngines.clear()
        releaseLocks()
        super.onDestroy()
    }

    // ── Public API ─────────────────────────────────────────

    fun createSessionInDir(cwd: String): TerminalSession? {
        // Ensure home directory exists (initializes first-run if needed)
        shellProvider.defaultWorkingDirectory()
        val dir = if (java.io.File(cwd).isDirectory) cwd else shellProvider.defaultWorkingDirectory()
        return createSessionInternal(dir)
    }

    fun createSession(): TerminalSession? {
        // Limit max sessions to prevent OOM on mobile (each session uses ~2-4MB for PTY + buffers)
        if (_sessions.value.size >= MAX_SESSIONS) {
            Log.w(TAG, "Max sessions reached ($MAX_SESSIONS) — refusing to create more")
            return null
        }
        return createSessionInternal(shellProvider.defaultWorkingDirectory())
    }

    /**
     * Create a new session and immediately execute a preset AI tool command.
     * Supported presets: claude, gemini, aider, opencode.
     */
    fun createPresetSession(preset: String): TerminalSession? {
        val command = PRESET_COMMANDS[preset.lowercase()]
        if (command == null) {
            Log.w(TAG, "Unknown preset: $preset")
            return null
        }
        val session = createSession()
        if (session != null) {
            // Delay to let the shell initialize before writing the command.
            // 1000ms is safe for slow devices; fast devices are unaffected.
            mainHandler.postDelayed({
                if (session.isRunning) {
                    session.write(command + "\n")
                    Log.i(TAG, "LAUNCH_PRESET: started '$command' in session ${session.mHandle}")
                } else {
                    Log.w(TAG, "LAUNCH_PRESET: session died before command could be written")
                }
            }, 1000)
        } else {
            Log.e(TAG, "LAUNCH_PRESET: failed to create session for preset '$preset'")
        }
        return session
    }

    private fun createSessionInternal(cwd: String): TerminalSession? {
        return try {
            val shellCmd = shellProvider.shellCommand()
            val shell = shellCmd[0] // executable (linker64 or shell)
            // Pass terminal dimensions so LINES/COLUMNS are set from launch.
            // The exact size comes from the last known view dimensions;
            // 40x120 are reasonable defaults if view hasn't measured yet.
            val lastSession = _sessions.value.lastOrNull()
            val rows = try { lastSession?.emulator?.mRows?.toString() } catch (_: Exception) { null } ?: "40"
            val cols = try { lastSession?.emulator?.mColumns?.toString() } catch (_: Exception) { null } ?: "120"
            val env = shellProvider.buildEnvironment(mapOf("LINES" to rows, "COLUMNS" to cols))

            Log.i(TAG, "Creating session: cmd=${shellCmd.toList()} cwd=$cwd size=${rows}x${cols}")

            val session = TerminalSession(
                shell,
                cwd,
                shellCmd,
                env,
                scrollbackLines.get(),
                sessionClient,
            )

            Log.i(TAG, "Session created: pid=${session.pid} running=${session.isRunning}")

            // Attach Rust VT engine if enabled (Phase 2 dual-run mode)
            if (useRustBackend.get()) {
                try {
                    val factory = rustEngineFactory ?: RustEngine.Factory().also { rustEngineFactory = it }
                    val engine = factory.create(TerminalDimensions(rows = 24, columns = 80))
                    session.setRawByteInterceptor { data, length ->
                        try {
                            engine.processBytes(data.copyOf(length))
                            // Write back terminal responses (DA, DSR) to PTY
                            val ptyResponse = engine.drainPtyWrites()
                            if (ptyResponse != null) {
                                session.write(ptyResponse, 0, ptyResponse.size)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Rust engine processBytes error", e)
                        }
                    }
                    session.setResizeListener { rows, cols ->
                        try {
                            engine.resize(TerminalDimensions(rows = rows, columns = cols))
                            Log.d(TAG, "Rust engine resized: ${rows}x${cols}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Rust engine resize error", e)
                        }
                    }
                    rustEngines[session.mHandle] = engine
                    Log.i(TAG, "Rust engine attached to session ${session.mHandle}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create Rust engine, running Java-only", e)
                }
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
        // Atomically remove the session from the list to avoid TOCTOU races.
        // Capture the removed session reference inside the update lambda.
        var removedSession: TerminalSession? = null
        _sessions.update { list ->
            if (index !in list.indices) return@update list
            removedSession = list[index]
            list.filterIndexed { i, _ -> i != index }
        }
        val session = removedSession ?: return
        // Clean up callbacks, trackers, and engines AFTER atomic removal
        screenUpdateCallbacks.remove(session.mHandle)
        zoneTrackers.remove(session.mHandle)
        rustEngines.remove(session.mHandle)?.destroy()
        session.setRawByteInterceptor(null)
        session.setResizeListener(null)
        session.finishIfRunning()
        updateNotification()
        updateBootReceiverState()
        if (_sessions.value.isEmpty()) stopSelf()
    }

    fun toggleWakeLock() {
        wakeLocks.toggle()
        notifications.scheduleUpdate()
    }

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

    // ── Session persistence ──────────────────────────────────

    @Synchronized
    private fun saveSessionMetadata() {
        // Snapshot the list inside the synchronized block to prevent
        // concurrent modification if sessions change on another thread.
        val snapshot = _sessions.value.toList()
        val metadata = snapshot.mapIndexed { index, session ->
            SessionMetadata(
                id = index,
                shell = shellProvider.findShell(),
                cwd = session.cwd ?: shellProvider.defaultWorkingDirectory(),
                title = session.title?.takeIf { it.isNotBlank() } ?: "shell",
            )
        }
        sessionStore.save(metadata)
        Log.d(TAG, "Saved ${metadata.size} session(s) metadata")
    }

    /**
     * Checks if there are saved sessions from a previous run.
     * Called by ViewModel to decide whether to offer session restore.
     */
    fun getSavedSessions(): List<SessionMetadata> = sessionStore.load()

    fun clearSavedSessions() = sessionStore.clear()

    // ── Lock management — delegated to WakeLockManager ──────

    private fun releaseLocks() = wakeLocks.release()

    // ── Notifications — delegated to NotificationHelper ──────

    /**
     * Called when a command block completes (OSC 133;D received).
     * Sends a notification if the command took longer than [commandNotifyThresholdMs]
     * and the app is currently in the background.
     */
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

    // ── MCP bridge implementation ───────────────────────────

    /**
     * Bridge between the MCP server and TerminalService internals.
     * Delegates all operations to existing service methods.
     */
    inner class ServiceMcpBridge : McpSessionBridge {

        override val sessions: StateFlow<List<McpSessionInfo>>
            get() {
                // Map TerminalSession list to McpSessionInfo list.
                // We create a derived StateFlow backed by a MutableStateFlow
                // that we keep in sync.
                return _mcpSessions
            }

        private val _mcpSessions = MutableStateFlow<List<McpSessionInfo>>(emptyList())

        init {
            // Keep MCP session list in sync with actual sessions.
            // We use a simple polling approach via the save handler since
            // StateFlow.map returns a Flow, not a StateFlow.
            syncMcpSessions()
        }

        private fun syncMcpSessions() {
            _mcpSessions.value = _sessions.value.mapIndexed { index, session ->
                McpSessionInfo(
                    index = index,
                    title = session.title?.takeIf { it.isNotBlank() } ?: "shell",
                    cwd = session.cwd ?: shellProvider.defaultWorkingDirectory(),
                    isRunning = session.isRunning,
                    pid = session.pid,
                )
            }
        }

        override fun createSession(cwd: String?): Int {
            val session = if (cwd != null) {
                createSessionInDir(cwd)
            } else {
                this@TerminalService.createSession()
            }
            syncMcpSessions()
            return if (session != null) _sessions.value.indexOf(session) else -1
        }

        override fun closeSession(index: Int) {
            removeSession(index)
            syncMcpSessions()
        }

        override fun writeToSession(index: Int, input: String) {
            val sessionList = _sessions.value
            if (index !in sessionList.indices) return
            sessionList[index].write(input)
        }

        override fun readOutput(index: Int, lines: Int): String {
            val sessionList = _sessions.value
            if (index !in sessionList.indices) return ""
            val emulator = sessionList[index].emulator ?: return ""
            val screen = emulator.screen
            val transcript = screen.getTranscriptText()
            // Return last N lines from transcript
            val allLines = transcript.split("\n")
            return allLines.takeLast(lines.coerceAtLeast(1)).joinToString("\n")
        }

        override fun getSessionCwd(index: Int): String? {
            val sessionList = _sessions.value
            if (index !in sessionList.indices) return null
            return sessionList[index].cwd
        }

        override fun homePath(): String = shellProvider.defaultWorkingDirectory()

        override fun prefixPath(): String {
            // prefix = rootDir/usr, home = rootDir/home
            // rootDir = context.filesDir.absolutePath
            val home = shellProvider.defaultWorkingDirectory()
            // home is <rootDir>/home, prefix is <rootDir>/usr
            val rootDir = java.io.File(home).parent ?: home
            return "$rootDir/usr"
        }

        override fun readFile(path: String, maxBytes: Int): String? {
            return try {
                val file = java.io.File(path)
                if (!file.exists() || !file.isFile || !file.canRead()) return null
                if (file.length() > maxBytes) return null
                file.readText()
            } catch (e: Exception) {
                Log.e(TAG, "MCP readFile error: $path", e)
                null
            }
        }

        override fun writeFile(path: String, content: String): Boolean {
            return try {
                val file = java.io.File(path)
                file.parentFile?.mkdirs()
                file.writeText(content)
                true
            } catch (e: Exception) {
                Log.e(TAG, "MCP writeFile error: $path", e)
                false
            }
        }

        override fun listDirectory(path: String): List<FileEntry> {
            return try {
                val dir = java.io.File(path)
                if (!dir.exists() || !dir.isDirectory) return emptyList()
                dir.listFiles()?.map { file ->
                    FileEntry(
                        name = file.name,
                        isDirectory = file.isDirectory,
                        size = if (file.isFile) file.length() else 0L,
                    )
                }?.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name })
                    ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "MCP listDirectory error: $path", e)
                emptyList()
            }
        }
    }

    companion object {
        private const val TAG = "NovaTerm"
        private const val SAVE_INTERVAL_MS = 30_000L // 30s periodic session save

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

        /** Map of preset names to commands that should be executed in a new session. */
        private val PRESET_COMMANDS = mapOf(
            "claude" to "claude",
            "gemini" to "gemini",
            "aider" to "aider",
            "opencode" to "opencode",
        )
    }
}
