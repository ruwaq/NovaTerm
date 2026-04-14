# NovaTerm Code Audit Results
**Date:** March 2026 | **Verdict: SALVAGEABLE — No rewrite needed, incremental fixes**

---

## Critical Bugs (Fix IMMEDIATELY)

| # | Location | Bug | Impact |
|---|---|---|---|
| 1 | `TerminalService.kt:28` | `mutableStateListOf` (Compose) in a Service | Unpredictable recomposition behavior |
| 2 | `TerminalService.kt:33` | `onSessionFinished()` is empty | Dead sessions become zombies |
| 3 | `TerminalService.kt:55` | `START_NOT_STICKY` | Service dies without restart, user loses work |
| 4 | `TerminalService.kt:113` | Wake lock 10min timeout | Silently expires, UI still shows "active" |
| 5 | `Theme.kt:53` | Dynamic Colors override Gruvbox by default | Gruvbox theme never applies |
| 6 | `BootReceiver.kt:11` | Starts empty service on boot | Zombie service, possible crash on Android 12+ |
| 7 | `MainActivity.kt:53` | `unbindService` without try-catch | Can crash with IllegalArgumentException |
| 8 | `termux.c:165` | JNI ReleaseStringUTFChars uses wrong ref | Memory corruption (UB) |
| 9 | `TermuxSessionManager.kt` | `lateinit var client` without guard | UninitializedPropertyAccessException crash |
| 10 | `TermuxSessionManager.kt` | Race conditions in create/destroy | Concurrent modification crashes |

## Architectural Issues

| # | Issue | Fix |
|---|---|---|
| 1 | `TerminalService` duplicates session creation logic from `TermuxSessionManager` | Use single path via SessionManager |
| 2 | `NotificationProvider` contract returns `Any`, impl doesn't implement interface | Redesign contract or move out of core:common |
| 3 | `DataStoreConfigStore.update()` has race condition | Let DataStore collect drive state, don't set manually |
| 4 | No `TerminalBackend` abstraction | Add interface for Java->Rust swap |
| 5 | ViewModel in `app/` instead of `feature:terminal` | Move to feature module |
| 6 | No `core:service` module | Extract service logic from app/ |
| 7 | No `core:bootstrap` module | Create for Phase 1 |
| 8 | 6 of 9 modules don't use convention plugins | Apply consistently |
| 9 | Dependencies hardcoded outside version catalog | Move all to libs.versions.toml |
| 10 | No Gradle Wrapper | Generate immediately |

## Feature Module Issues

| Module | Key Issue |
|---|---|
| `feature:terminal` | `NovaTermViewClient` is a total stub — CTRL/ALT/log don't work |
| `feature:terminal` | fontSize hardcoded to 14, ignores preferences |
| `feature:terminal` | No `DisposableEffect` for cleanup (import is dead) |
| `feature:settings` | Local `fontSize` state doesn't sync with external changes |
| `feature:settings` | Uses SharedPreferences instead of DataStore |
| `feature:oem-compat` | Instructions are for MIUI, not HyperOS 2 |
| All screens | No `@Preview` composables, no strings.xml |

## Build System Issues

| Issue | Priority |
|---|---|
| No Gradle Wrapper (`gradlew`) | CRITICAL — nothing builds without it |
| No `ndkVersion` in terminal-emulator | HIGH — inconsistent NDK |
| No `consumerProguardFiles` in terminal-emulator | HIGH — R8 may strip JNI |
| CI missing NDK setup | HIGH — build job will fail |
| `core/common` has hardcoded Kotlin version | MEDIUM |
| ndkBuild (Android.mk) should migrate to CMake | MEDIUM (pre-AGP 9) |

## Ideal Architecture (Incremental Migration)

### New modules needed for Phase 1:
- `core:service` — Extract TerminalService + survival logic from app/
- `core:bootstrap` — Filesystem setup + package installation
- `feature:shortcuts` — Tiles, shortcuts, deep links, share

### Key new interface: `TerminalBackend`
```kotlin
interface TerminalBackend {
    fun createSession(config: SessionConfig): Int
    fun destroySession(sessionId: Int)
    fun write(sessionId: Int, data: ByteArray)
    fun resize(sessionId: Int, rows: Int, cols: Int)
    val sessionEvents: Flow<SessionEvent>
}
```
This enables Phase 2 Java->Rust swap without touching UI.

### Migration plan (incremental, each step compiles):
1. Add `TerminalBackend` + `TerminalRenderer` to core:common
2. Create `TermuxBackend` wrapper in core:session
3. Create `core:service`, extract from app/
4. Create `core:bootstrap`
5. Create `app/di/` with manual DI
6. Move ViewModel to feature:terminal
7. Apply convention plugins to all modules
8. Move hardcoded deps to version catalog
9. Generate Gradle Wrapper + fix CI

### Module dependency graph (Phase 1 target):
```
app → feature:terminal → core:session → core:terminal-emulator
    → feature:settings → core:config
    → feature:oem-compat
    → core:service → core:session + core:notification
    → core:bootstrap
    → core:common (everyone depends on this)
```

Rule: core/ never depends on feature/. feature/ never depends on feature/. Only app/ sees everything.
