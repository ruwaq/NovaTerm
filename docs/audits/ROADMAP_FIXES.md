# NovaTerm Fix Roadmap — 301 Audit Issues

**Date:** 2026-05-06
**Goal:** Fix all 301 issues in logical sprints, minimizing merge conflicts and maximizing stability.

---

## Sprint 1: Stop the Bleeding (CRITICAL — 31 issues)
*Objective: Eliminate crashes, data loss, and security vulnerabilities before any new feature work.*

### 1.1 Rust Core — Crash Fixes
| Issue | File | Description | Effort |
|-------|------|-------------|--------|
| C1 | `session_map.rs` | `destroy()` panics on concurrent lock | Small |
| C2 | `novaterm-pty/lib.rs` | `close_fds_except` uses `malloc` post-fork | Medium |
| C6 | `renderer_jni.rs` | ANativeWindow UAF | Small |
| C7 | `atlas_texture.rs` | Wrong row stride on Mali/PowerVR | Small |
| C8 | `context.rs` | GPU init blocks UI thread → ANR | Medium |

### 1.2 Kotlin Core — Crash & Leak Fixes
| Issue | File | Description | Effort |
|-------|------|-------------|--------|
| C9 | `GpuTerminalScreen.kt` | MutableState written from Choreographer | Small |
| C10 | `GpuTerminalScreen.kt` | IME proxy steals focus | Small |
| C11 | `GpuRenderer.kt` | TOCTOU race renderFrame/destroy | Small |
| C25 | `TerminalService.kt` | `removeSession` CAS destroys wrong session | Medium |
| C26 | `TerminalService.kt` | `onSessionFinished` leaks engines | Small |
| C27 | `RustEngine.kt` | TOCTOU native handle race | Small |
| C28 | `TerminalView.java` | Hard-coded background ignores palette | Small |
| C29 | `TerminalView.java` | Cursor blinker Handler leak | Small |
| C30 | `TerminalView.java` | `mEmulator` not volatile | Small |

### 1.3 Security Fixes
| Issue | File | Description | Effort |
|-------|------|-------------|--------|
| C12 | `AndroidManifest.xml` | TerminalService exported with `signature` | Small |
| C13 | `AndroidManifest.xml` | DocumentsProvider `grantUriPermissions` | Small |
| C14 | `novaterm_pty.c` | Native PTY unsanitized input | Medium |
| C15 | `TerminalService.kt` | Boot scripts without verification | Medium |
| C16 | `TerminalEmulator.java` | OSC 52 clipboard exfiltration | Small |
| C17 | `EnvironmentBuilder.kt` | LD_PRELOAD without checksum | Medium |

### 1.4 Bootstrap Fixes
| Issue | File | Description | Effort |
|-------|------|-------------|--------|
| C3 | `BootstrapInstaller.kt` | `deleteRecursively()` follows symlinks | Small |
| C4 | `BootstrapInstaller.kt` | `isBootstrapped` false → prefix wipe | Medium |
| C5 | `BootstrapInstaller.kt` | Concurrent bootstrap without mutex | Medium |
| C18 | `BootstrapInstaller.kt` | dpkg wrapper suppresses all errors | Medium |
| C19 | `BootstrapInstaller.kt` | Large package stub .deb | Medium |
| C20 | `app/build.gradle.kts` | Duplicate bootstrap ZIP (+31MB) | Small |
| C21 | `keystore.properties` | Hardcoded signing passwords | Small |
| C23 | `DataStoreConfigStore.kt` | Color scheme serialization mismatch | Small |
| C24 | Multiple | Hardcoded `com.nvterm` paths | Medium |

---

## Sprint 2: HIGH Priority (83 issues)
*Objective: Fix race conditions, resource leaks, and correctness errors.*

### 2.1 Rust Core — Correctness
- H1: `dup()` O_NONBLOCK poisons writer
- H2: `dup()` without CLOEXEC
- H3: `destroy()` double-kill PID reuse
- H4: `stop()` no SIGHUP → orphans
- H5: Writer thread never unparked
- H6: Scrollback broken (`total_lines()`)
- H44: `libc_atoi` overflow
- H45: `close_fds_except` range too small
- H46: Reader thread exits on EINTR
- H47: Unbounded EventCollector buffer
- H48: Unbounded ByteBuffer
- H49: `nativeGetGrid` returns null
- H50: `nativeWrite` silently drops data

### 2.2 GPU Renderer — Correctness
- H51: Bind group recreated every frame
- H52: Missing buffer size validation
- H53: Dummy AllocId panic on eviction
- H54: `destroy()` drops resources without idle

### 2.3 Kotlin Session Engine
- H62: `removeSession` catch omits engine cleanup
- H63: `destroyAll` clears without destroying
- H64: `attachEngine` overwrites silently
- H65: Dead `SessionManager`
- H66: `splitPane` index race
- H67: `LegacyEngine` unsynchronized access

### 2.4 GPU Terminal Screen + IME
- H24-H35: All IME, focus, and rendering race issues

### 2.5 Security & APT
- H36-H43: dpkg, bashrc, checksum, disk space, GPG

### 2.6 Build System
- H15-H18: NDK, CMake, prebuilt binaries, ProGuard

### 2.7 Settings
- H19-H20: DataStore dead code, StateFlow race
- H61: Service cold-start defaults

---

## Sprint 3: MEDIUM Priority (100 issues)
*Objective: Improve UX, add validation, fix tests, reduce tech debt.*

- UI: Missing Compose UI tests, `TerminalEmulator` test harness, accessibility tests
- Performance: Config-change scroll loss, GPU frame callback unconditional, unbounded buffers
- Validation: `setComposingText` missing, `keyEventToBytes` gaps, `applyTerminalPalette` static mutation
- Dead code: Remove `DataStoreConfigStore`, old AI classes, unused modules
- APT: Cache location, timeout, config versioning
- Build: F-Droid metadata, dead ProGuard rules, AGP deprecation

---

## Sprint 4: LOW Priority (64 issues)
*Objective: Polish, hygiene, documentation.*

- Deprecated API replacements (`getExternalStorageDirectory`)
- Dead imports, dead code cleanup
- Test flakiness, brittle assertions
- Comments, naming, i18n consistency

---

## Execution Rules

1. **One sprint at a time.** No new features until CRITICAL sprint is done.
2. **Build after every fix group.** Run `./gradlew :app:assembleDebug` after each module batch.
3. **Test on emulator after security/bootstrap fixes.** Run `scripts/test-dual-prefix.sh`.
4. **Never force-push.** Git safety rule from memory.
5. **Commit with descriptive messages.** Include issue number (C1, H23, etc.).
