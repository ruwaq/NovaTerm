# NovaTerm Process Survival Guide
**Date:** March 2026 | **Primary target:** Xiaomi 15T Pro (HyperOS 2/3, Dimensity 9400)

---

## The Problem: 5 Independent Kill Systems

Android has multiple independent systems that kill background processes. A terminal emulator must defend against ALL of them simultaneously.

### 1. lmkd (Low Memory Killer Daemon)

- Userspace daemon that replaces kernel LMK (removed in kernel 4.12)
- Uses PSI (Pressure Stall Information) to detect memory pressure
- Kills processes by oom_adj score (higher = killed first)

**Key thresholds (configurable via `ro.lmk.*`):**
- Low pressure: oom_adj >= 1001 (disabled by default)
- Medium pressure: oom_adj >= 800
- Critical pressure: oom_adj >= 0 (even foreground services!)

**OOM Adj scores (from ProcessList.java):**

| Score | Description | Risk |
|---|---|---|
| -1000 | Native processes | Safe |
| -800 | Persistent system processes | Safe |
| 0 | Foreground app | Very safe |
| 200 | **Foreground service (visible notification)** | **NovaTerm lives here** |
| 500 | Active service | Moderate risk |
| 700 | Previous app | High risk |
| 900-999 | Cached apps | First to die |

### 2. Phantom Process Killer (Android 12+)

The **most dangerous** for terminal emulators.

- Limit: **32 child processes** across ALL apps (not per-app)
- Counts any process forked by an app (bash, ssh, python, etc.)
- Kills processes whose parent app has highest oom_adj first
- Also kills processes using excessive CPU (checked every 5 minutes)

**Disabling (requires user action):**
- Android 14+: `Settings > Developer Options > Disable child process restrictions`
- Android 12-13 (ADB): `adb shell settings put global settings_enable_monitor_phantom_procs false`
- Must also disable config sync: `adb shell device_config set_sync_disabled_for_tests persistent`

### 3. App Standby Buckets (Android 9+)

5 tiers based on usage patterns. With foreground service + battery exemption, impact is minimal.

### 4. Doze Mode

Activates after ~1 hour of screen off + no movement. Cuts network, ignores wakelocks, defers alarms. Relevant for SSH connections.

### 5. OEM Killers (The Real Enemy)

Stock Android is relatively benign. OEM customizations are brutal.

---

## Xiaomi / HyperOS Specific

### Three Proprietary Kill Layers

**PowerKeeper (`com.miui.powerkeeper`)**
- Monitors CPU/RAM/network usage per app
- Has internal whitelist (WhatsApp, WeChat, etc. are immune)
- More aggressive during "sleep hours" (detected automatically)
- Can be disabled via ADB: `adb shell pm disable-user com.miui.powerkeeper`
- WARNING: disabling may cause issues on some devices

**PeriodicCleaner (`com.android.server.am.PeriodicCleanerService`)**
- Runs inside system_server (not a separate APK)
- **Ignores** user settings like "locked" and "battery optimization"
- Diagnostic: `adb shell cmd periodic dump`
- Disable (temporary): `adb shell cmd periodic enable false`
- Disable (permanent, requires root): `setprop persist.sys.periodic.enable false`

**SecurityCenter (`com.miui.securitycenter`)**
- Controls Autostart permission
- Manages "protected apps" list

### Detecting Xiaomi/HyperOS Programmatically

```kotlin
// Detect HyperOS specifically
fun isHyperOS(): Boolean {
    return !getSystemProperty("ro.mi.os.version.name").isNullOrBlank()
}

// Detect MIUI (present in both MIUI and HyperOS)
fun isMiui(): Boolean {
    return !getSystemProperty("ro.miui.ui.version.name").isNullOrBlank()
}

// Detect Xiaomi device
fun isXiaomiDevice(): Boolean {
    return Build.BRAND.lowercase() in setOf("xiaomi", "redmi", "poco")
}

private fun getSystemProperty(key: String): String? {
    return try {
        Runtime.getRuntime().exec("getprop $key")
            .inputStream.bufferedReader().readLine()
    } catch (e: IOException) { null }
}
```

### Detecting Autostart Permission

```kotlin
// Op code 10021 = MIUI autostart permission
fun isAutostartEnabled(context: Context): Boolean? {
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val method = appOps.javaClass.getMethod(
            "checkOpNoThrow",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java
        )
        val result = method.invoke(appOps, 10021, Process.myUid(), context.packageName) as Int
        result == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) { null }
}
```

Library: [XomaDev/MIUI-Autostart](https://github.com/XomaDev/MIUI-Autostart)

### Xiaomi-Specific Intents

```kotlin
// Open autostart settings
fun openAutostart(context: Context) {
    context.startActivity(Intent().apply {
        component = ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
    })
}

// Open battery saver settings
fun openBatterySaver(context: Context) {
    context.startActivity(Intent().apply {
        component = ComponentName(
            "com.miui.powerkeeper",
            "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
        )
        putExtra("package_name", context.packageName)
        putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager))
    })
}
```

### User Guide: Whitelist NovaTerm on HyperOS

1. **Battery Saver** → No restrictions
   `Settings > Apps > Manage apps > NovaTerm > Battery saver > No restrictions`

2. **Autostart** → ON
   `Settings > Apps > Manage apps > NovaTerm > App permissions > Background autostart > ON`

3. **Lock in Recents** → Swipe app card DOWN in recent apps (lock icon appears)

4. **Background Data** → ON
   `Settings > Apps > Manage apps > NovaTerm > Data usage > Background data > ON`
   `+ Unrestricted data > ON`

5. **Sleep Mode** → OFF (optional)
   `Settings > Battery > Settings (gear) > Scenarios > Sleep Mode > OFF`

6. **Battery Optimization** (stock Android)
   `Settings > Privacy protection > Special permissions > Battery optimization > All apps > NovaTerm > Don't optimize`

---

## Other OEM Notes

### Samsung (One UI)
- Sleeping apps (3 days unused) / Deep sleeping apps (16 days)
- Since One UI 6.0: foreground services should work as AOSP intended
- Install "Good Guardians" app > Memory Guardian > "Keep more apps in background"

### OnePlus (OxygenOS)
- Deep Optimization kills aggressively (enabled by default since OnePlus 6+)
- Sleep Standby Optimization disconnects network during detected sleep hours
- Settings can RESET themselves randomly
- "No known solution on dev end" - dontkillmyapp.com

### Huawei (EMUI)
- **PowerGenie**: System task killer, NO user settings to configure
- Only fix: uninstall via ADB: `adb shell pm uninstall -k --user 0 com.huawei.powergenie`
- HwPFWService kills wakelocks >60min unless tag is whitelisted
- Whitelisted tag: `"LocationManagerService"` (use for wakelock naming)

---

## Defense Strategy (Layered)

### Layer 1: Maximize Service Survival

```kotlin
// TerminalService.kt changes needed:

// 1. Change to START_STICKY (currently START_NOT_STICKY - BUG)
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    return START_STICKY  // System will restart service if killed
}

// 2. Auto-activate wake lock when sessions exist
fun createSession() {
    // ... create session ...
    if (sessions.size == 1) {
        acquireWakeLock()  // First session -> auto wake lock
    }
}

// 3. Add WifiLock (critical for SSH)
private var wifiLock: WifiManager.WifiLock? = null

fun acquireWifiLock() {
    val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "novaterm:wifi")
    wifiLock?.acquire()
}

// 4. Wake lock without fixed timeout (or 4h with auto-renewal)
fun acquireWakeLock() {
    val pm = getSystemService(POWER_SERVICE) as PowerManager
    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "novaterm:terminal")
    wakeLock?.acquire(4 * 60 * 60 * 1000L)  // 4 hours, renew when needed
}

// 5. Request battery optimization exemption
fun requestBatteryExemption(context: Context) {
    val pm = context.getSystemService(POWER_SERVICE) as PowerManager
    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
        context.startActivity(Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        ))
    }
}
```

### Layer 2: OEM-Specific User Guidance

- Detect `Build.MANUFACTURER` on first launch
- Show OEM-specific setup guide
- Link to dontkillmyapp.com/{manufacturer} for detailed instructions
- Re-check permissions periodically (OEM updates can reset them)

### Layer 3: Session Recovery

**Fundamental truth:** You CANNOT reconnect to a dead PTY. The file descriptor dies with the process. Terminal state in memory is lost.

**What you CAN do:**
1. Serialize the visual buffer periodically (~every 10 seconds)
2. On restart, create new shell in same CWD + replay visual content
3. Show banner: `[Session restored - previous process was killed by system]`

```kotlin
data class SerializedSession(
    val id: String,
    val name: String,
    val cwd: String,
    val rows: Int,
    val columns: Int,
    val cursorRow: Int,
    val cursorCol: Int,
    val screenContent: ByteArray,    // ANSI-encoded screen
    val scrollbackContent: ByteArray, // ANSI-encoded scrollback (compressed)
    val timestamp: Long,
    val shellPath: String,
    val env: Map<String, String>,
)
```

**Size estimate:** ~50KB visible screen, ~2-5MB with scrollback. Compressed: ~200-500KB.

### Layer 4: Process Protection via tmux

Auto-start tmux inside each NovaTerm session:
```bash
tmux new-session -A -s novaterm-$SESSION_ID
```

Benefits:
- If UI dies but service lives, tmux keeps running
- If user reopens app, auto-attach to tmux session
- tmux-resurrect can save layout/content periodically

Limitation: If the service also dies, tmux dies too.

### Layer 5: SSH Resilience

- **Mosh**: UDP-based, survives network changes, IP changes, device sleep
- **Eternal Terminal**: Similar to mosh, full scroll support
- **autossh**: Auto-reconnect wrapper for SSH
- Detect when user runs `ssh` and suggest mosh

### Layer 6: Automatic Restart

```kotlin
// WorkManager watchdog (every 15 min)
val healthCheck = PeriodicWorkRequestBuilder<ServiceHealthCheckWorker>(
    15, TimeUnit.MINUTES
).build()
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "service-health", ExistingPeriodicWorkPolicy.KEEP, healthCheck
)

// Boot receivers
// AndroidManifest.xml:
// <receiver> BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, MY_PACKAGE_REPLACED

// onTaskRemoved (user swipes away)
override fun onTaskRemoved(rootIntent: Intent?) {
    saveAllSessionStates()
    super.onTaskRemoved(rootIntent)
}
```

---

## Foreground Service Configuration

### Type: `specialUse`

This is the correct type for terminal emulators. It has NO timeout (unlike dataSync's 6-hour limit).

```xml
<service
    android:name=".service.TerminalService"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Terminal emulator that maintains interactive shell sessions (bash/sh),
        executes long-running user-initiated commands (compilation, package management, SSH),
        and must keep child processes alive while the app is in background.
        No other foreground service type covers this use case." />
</service>
```

### Play Store Requirements for specialUse:
1. Detailed `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` description
2. Declaration in Play Console (Policy > App content)
3. Manual review by Google
4. May request demo video

### Notification Best Practices

```kotlin
NotificationCompat.Builder(this, CHANNEL_SERVICE)
    .setContentTitle("NovaTerm - ${sessions.size} sessions")
    .setContentText("Tap to open terminal")
    .setSmallIcon(R.drawable.ic_terminal)
    .setOngoing(true)
    .setPriority(NotificationCompat.PRIORITY_LOW)  // Minimum LOW, not MIN
    .addAction(R.drawable.ic_add, "New", newSessionPendingIntent)
    .addAction(R.drawable.ic_lock, wakeLockLabel, wakeLockPendingIntent)
    .addAction(R.drawable.ic_close, "Exit", exitPendingIntent)
    .build()
```

### Wake Lock and Google Play

Google Play penalizes apps with excessive wake locks (March 2026 enforcement):
- Threshold: >5% of sessions with >2 hours wake lock (screen off) in 28 days
- Use timeout (4 hours) with manual renewal
- Give user explicit control (toggle in notification)

---

## What Does NOT Work

| Approach | Why it fails |
|---|---|
| `android:persistent` | Only works for system apps |
| Silent audio trick | Android 17 blocks it (background audio hardening) |
| Native daemon | Phantom process killer kills it (Android 12+) |
| CRIU checkpoint/restore | Requires root, ARM64 PAC bug |
| Hidden API reflection | Breaks between versions |
| Dual process keep-alive | Marginal benefit, high complexity |

---

## Future: Phase 2 Architecture

When NovaTerm moves to Rust core (Phase 2), the architecture enables better survival:

```
Kotlin/Compose UI <--> Unix Domain Socket <--> Rust Terminal Server
                                                    |
                                                    +-> PTY 1 (bash)
                                                    +-> PTY 2 (ssh)
                                                    +-> PTY 3 (python)
```

If the UI process dies, the Rust terminal server can continue holding PTYs.
This is essentially tmux built into NovaTerm.

**Limitation:** Phantom process killer can still kill the Rust server process.

---

## Sources

- [Android lmkd documentation](https://source.android.com/docs/core/perf/lmkd)
- [Phantom Process Killer analysis](https://github.com/agnostic-apollo/Android-Docs/blob/master/en/docs/apps/processes/phantom-cached-and-empty-processes.md)
- [Android Foreground Service Types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Don't Kill My App - Xiaomi](https://dontkillmyapp.com/xiaomi)
- [Don't Kill My App - Samsung](https://dontkillmyapp.com/samsung)
- [11 Layers to Survive OEM Killing](https://dev.to/stoyan_minchev/what-android-oems-do-to-background-apps-and-the-11-layers-i-built-to-survive-it-28bb)
- [Termux Issue #2366 - Phantom Process Killing](https://github.com/termux/termux-app/issues/2366)
- [Termux Issue #3709 - Session Persistence](https://github.com/termux/termux-app/issues/3709)
- [XomaDev/MIUI-Autostart](https://github.com/XomaDev/MIUI-Autostart)
- [Android Wake Lock Best Practices](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock/best-practices)
- [Battery Technical Quality Enforcement (March 2026)](https://android-developers.googleblog.com/2026/03/battery-technical-quality-enforcement.html)
