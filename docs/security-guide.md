# NovaTerm Security Guide
**Date:** March 2026 | **Author:** NovaTerm Research

---

## Escape Sequence Attacks

### Critical: OSC 52 Clipboard Hijacking
Current code allows unrestricted clipboard writes. A background process can inject malicious text.
**Fix:** Gate clipboard writes to when app has focus (like Microsoft Terminal PR #19357).

### Critical: Bracketed Paste Bypass
Malicious clipboard content containing `ESC[201~` can escape bracketed paste mode.
**Fix:** Strip `ESC[201~` from pasted content before sending to PTY.

### Medium: DECRQSS Echoback
Verify responses don't include control chars or newlines. Consider disabling by default.

### Medium: Title Injection
Rate-limit title changes to max 5/second to prevent DoS.

### Safe: Title Reporting (OSC 20/21)
Already disabled in Termux code — keep it disabled.

### Safe: C1 Controls in UTF-8
Bytes 0x80-0x9F are ignored in UTF-8 mode (correct behavior).

## App Security

### SSH Key Storage
- Use Android Keystore with BiometricPrompt for hardware-backed keys
- `setUserAuthenticationRequired(true)` + `setInvalidatedByBiometricEnrollment(true)`
- Use `CryptoObject` in BiometricPrompt (not just success callback — vulnerable to hooking)
- Support both Keystore keys and traditional ~/.ssh/ file keys

### Data Protection
- Change `android:allowBackup="false"` (currently true — leaks SSH keys via adb backup)
- Use `EncryptedSharedPreferences` for sensitive config
- Offer `FLAG_SECURE` option (prevents screenshots)
- Encrypt serialized scrollback if persisted to disk

### Privacy
- **Zero telemetry. Period.** (Lesson from Warp controversy)
- No analytics, no crash reporting by default
- If ever needed: opt-in only with clear dialog

## Network Security

- SSH keys: Ed25519 default, RSA-4096 fallback
- Known hosts: strict checking by default
- Bootstrap downloads: certificate pinning via `network_security_config.xml`
- GPG verification of bootstrap packages
- HTTPS only, TLS 1.3 minimum

## Android-Specific

- Don't request `MANAGE_EXTERNAL_STORAGE` unless absolutely necessary
- BootReceiver: verify `exported` setting works with BOOT_COMPLETED on Android 12+
- ContentProvider (if implemented): validate document IDs against path traversal
- Intent security: explicit intents, validate all extras

## Plugin Security (Phase 4)

- WASM sandbox with capability-based permissions
- Explicit permission prompt at plugin install time
- Resource limits: 64MB memory, CPU timeout per plugin
- Plugin signing with author keys
- Isolated memory between plugins
