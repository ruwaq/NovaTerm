package com.novaterm.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.novaterm.app.NovaTermApp
import com.novaterm.app.R
import com.novaterm.app.ui.MainActivity
import android.content.ComponentName
import android.content.pm.PackageManager
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
import com.novaterm.core.session.persistence.db.BlockStore
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
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

    // ── Core delegates ────────────────────────────────────

    private lateinit var shellProvider: AndroidShellProvider
    private lateinit var sessionStore: SessionStore
    lateinit var blockStore: BlockStore
        private set

    // ── Session state (observable) ─────────────────────────

    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    val sessionCount: Int get() = _sessions.value.size

    /**
     * Called by the UI to register a screen update callback.
     * The callback MUST call TerminalView.onScreenUpdated().
     * Thread-safe: always dispatched to main thread.
     */
    var onScreenUpdated: (() -> Unit)? = null

    // ── MCP server ──────────────────────────────────────────

    private var mcpServer: McpServer? = null

    // ── Preferences bridge ──────────────────────────────────

    @Volatile var bellEnabled: Boolean = true
    @Volatile var useRustBackend: Boolean = false
    @Volatile var mcpEnabled: Boolean = false
    @Volatile var mcpPort: Int = McpServerConfig.DEFAULT_PORT

    // ── Rust engine instances (Phase 2) ────────────────────

    private val rustEngines = java.util.concurrent.ConcurrentHashMap<String, TerminalEngine>()
    private var rustEngineFactory: RustEngine.Factory? = null

    /** Get the Rust engine for a session handle, if one exists. */
    fun getRustEngine(sessionHandle: String): TerminalEngine? = rustEngines[sessionHandle]

    // ── Lifecycle flag ──────────────────────────────────────

    @Volatile private var isServiceDestroyed = false

    // ── Locks ──────────────────────────────────────────────

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    val isWakeLockHeld: Boolean get() = wakeLock?.isHeld == true

    // ── Clipboard ──────────────────────────────────────────

    private val clipboardManager: ClipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // ── Session client callbacks ───────────────────────────

    private val sessionClient = object : TerminalSessionClient {

        override fun onTextChanged(changedSession: TerminalSession) {
            // Phase 2 validation: log Rust engine state on every screen update
            if (useRustBackend) {
                validateRustEngine(changedSession)
            }

            val callback = onScreenUpdated ?: return
            if (Looper.myLooper() == Looper.getMainLooper()) {
                callback()
            } else {
                mainHandler.post(callback)
            }
        }

        override fun onTitleChanged(changedSession: TerminalSession) {
            _sessions.update { it.toList() }
            onTextChanged(changedSession)
        }

        override fun onSessionFinished(finishedSession: TerminalSession) {
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
                val text = clip.getItemAt(0).coerceToText(this@TerminalService)
                session?.write(text.toString())
            }
        }

        override fun onBell(session: TerminalSession) {
            if (!bellEnabled) return

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
                sendBellNotification(session)
            }
        }

        override fun onColorsChanged(session: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}

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

        startForeground(NOTIFICATION_ID, buildNotification())

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

        if (!mcpEnabled) return

        try {
            val config = McpServerConfig(
                enabled = true,
                port = mcpPort,
            )
            val bridge = ServiceMcpBridge()
            val server = McpServer(config, bridge)
            server.start()
            mcpServer = server
            Log.i(TAG, "MCP server started on port $mcpPort")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MCP server", e)
        }
    }

    private val saveRunnable = object : Runnable {
        override fun run() {
            if (isServiceDestroyed) return
            if (_sessions.value.isNotEmpty()) {
                saveSessionMetadata()
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
                _sessions.value = emptyList()
                snapshot.forEach { session ->
                    rustEngines.remove(session.mHandle)?.destroy()
                    session.setRawByteInterceptor(null)
                    session.setResizeListener(null)
                    session.finishIfRunning()
                }
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
        onScreenUpdated = null
        mcpServer?.stop()
        mcpServer = null
        stopPeriodicSave()
        saveSessionMetadata()
        if (::blockStore.isInitialized) blockStore.close()
        val snapshot = _sessions.value.toList()
        snapshot.forEach { session ->
            rustEngines.remove(session.mHandle)?.destroy()
            session.setRawByteInterceptor(null)
            session.finishIfRunning()
        }
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
        return createSessionInternal(shellProvider.defaultWorkingDirectory())
    }

    private fun createSessionInternal(cwd: String): TerminalSession? {
        return try {
            val shellCmd = shellProvider.shellCommand()
            val shell = shellCmd[0] // executable (linker64 or shell)
            val env = shellProvider.buildEnvironment()

            Log.i(TAG, "Creating session: cmd=${shellCmd.toList()} cwd=$cwd")

            val session = TerminalSession(
                shell,
                cwd,
                shellCmd,
                env,
                TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
                sessionClient,
            )

            Log.i(TAG, "Session created: pid=${session.pid} running=${session.isRunning}")

            // Attach Rust VT engine if enabled (Phase 2 dual-run mode)
            if (useRustBackend) {
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
        _sessions.update { list ->
            if (index !in list.indices) return@update list
            val session = list[index]
            // Cleanup Rust engine
            rustEngines.remove(session.mHandle)?.destroy()
            session.setRawByteInterceptor(null)
            session.finishIfRunning()
            list.filterIndexed { i, _ -> i != index }
        }
        updateNotification()
        updateBootReceiverState()
        if (_sessions.value.isEmpty()) stopSelf()
    }

    fun toggleWakeLock() {
        if (wakeLock?.isHeld == true) {
            releaseLocks()
        } else {
            acquireLocks()
        }
        updateNotification()
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

    // ── Phase 2 validation ────────────────────────────────────

    private var validationCount = 0L

    /**
     * Compare Java emulator cursor with Rust engine cursor.
     * Logs mismatches to help validate Rust parser correctness.
     * Only runs when useRustBackend is enabled.
     */
    private fun validateRustEngine(session: TerminalSession) {
        val engine = rustEngines[session.mHandle] ?: return
        val emulator = session.emulator ?: return

        // Throttle: only validate every 100th update to avoid log spam
        if (++validationCount % 100 != 0L) return

        val javaCursorRow = emulator.cursorRow
        val javaCursorCol = emulator.cursorCol
        val rustCursor = engine.getCursor()

        if (javaCursorRow != rustCursor.row || javaCursorCol != rustCursor.column) {
            Log.w(TAG, "Rust/Java cursor mismatch: " +
                "Java=($javaCursorRow,$javaCursorCol) " +
                "Rust=(${rustCursor.row},${rustCursor.column}) " +
                "[update #$validationCount]")
        } else if (validationCount % 1000 == 0L) {
            Log.d(TAG, "Rust/Java cursor match OK at ($javaCursorRow,$javaCursorCol) " +
                "[update #$validationCount]")
        }
    }

    // ── Session persistence ──────────────────────────────────

    @Synchronized
    private fun saveSessionMetadata() {
        val metadata = _sessions.value.mapIndexed { index, session ->
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

    // ── Lock management ────────────────────────────────────

    private fun acquireLocks() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "novaterm:service-wakelock",
            )
        }
        wakeLock?.acquire(WAKELOCK_TIMEOUT_MS)

        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "novaterm:service-wifilock",
            )
        }
        wifiLock?.acquire()
    }

    private fun releaseLocks() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        if (wifiLock?.isHeld == true) wifiLock?.release()
        wifiLock = null
    }

    // ── Notifications ──────────────────────────────────────

    private fun sendBellNotification(session: TerminalSession) {
        val title = session.title?.takeIf { it.isNotBlank() } ?: "Terminal"
        val contentIntent = PendingIntent.getActivity(
            this, NOTIFICATION_BELL,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, NovaTermApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(getString(R.string.notification_bell_activity))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_BELL, notification)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val newSessionIntent = PendingIntent.getService(
            this, REQ_NEW_SESSION,
            Intent(this, TerminalService::class.java).setAction(ACTION_NEW_SESSION),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val wakeLockIntent = PendingIntent.getService(
            this, REQ_TOGGLE_WAKELOCK,
            Intent(this, TerminalService::class.java).setAction(ACTION_TOGGLE_WAKELOCK),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val exitIntent = PendingIntent.getService(
            this, REQ_EXIT,
            Intent(this, TerminalService::class.java).setAction(ACTION_EXIT),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val count = _sessions.value.size
        val lockLabel = getString(if (isWakeLockHeld) R.string.action_wake_lock_on else R.string.action_wake_lock_off)

        return NotificationCompat.Builder(this, NovaTermApp.CHANNEL_SERVICE)
            .setContentTitle(getString(R.string.notification_title_running))
            .setContentText(getString(R.string.notification_sessions, count))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(if (isWakeLockHeld) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_input_add, getString(R.string.action_new_session), newSessionIntent)
            .addAction(android.R.drawable.ic_lock_lock, lockLabel, wakeLockIntent)
            .addAction(android.R.drawable.ic_delete, getString(R.string.action_exit), exitIntent)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

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
        private const val NOTIFICATION_ID = 1337
        private const val REQ_EXIT = 1
        private const val REQ_NEW_SESSION = 2
        private const val REQ_TOGGLE_WAKELOCK = 3
        private const val NOTIFICATION_BELL = 1338
        private const val SAVE_INTERVAL_MS = 30_000L // 30s periodic session save
        private const val WAKELOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L // 4h safety net

        const val ACTION_EXIT = "com.nvterm.action.EXIT"
        const val ACTION_NEW_SESSION = "com.nvterm.action.NEW_SESSION"
        const val ACTION_TOGGLE_WAKELOCK = "com.nvterm.action.TOGGLE_WAKELOCK"
    }
}
