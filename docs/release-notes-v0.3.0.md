# NovaTerm v0.3.0-alpha

**Release Date:** April 5, 2026

Third alpha release of NovaTerm. This release adds AI tool integration, built-in split panes, Picture-in-Picture mode, voice input, and significant stability improvements with 18 bug fixes.

---

## Highlights

- **AI Tool Integration** -- One-tap installer for Claude Code, Gemini CLI, Aider, and OpenCode with encrypted API key storage and dedicated extra keys
- **Built-in Split Panes** -- Terminal multiplexer with binary tree layout, up to 4 panes per session (14 tests)
- **Picture-in-Picture** -- Floating terminal window with UI adaptation
- **Voice Input** -- Mic button in extra keys bar using Android SpeechRecognizer
- **MCP Orchestration** -- 4 new tools (10 total) with rate limiting and bearer token auth
- **18 Bug Fixes** -- Deep audit covering memory leaks, race conditions, and ANR issues

---

## What's New

### AI Integration
- API key management for Anthropic, Google, OpenAI, OpenRouter (masked display, auto-export to env)
- AI tool installer: one-tap install for Claude Code, Gemini CLI, Aider, OpenCode
- Session presets with app shortcuts (long-press icon to launch directly into AI tools)
- AI Coding extra keys style (Ctrl+O transcript, ! bash prefix, Ctrl+B scroll)
- Synchronized output (DEC private mode 2026) for flicker-free AI streaming
- AI environment auto-config: CLAUDE_CODE_SCROLL_SPEED, AIDER_DARK_MODE
- EncryptedSharedPreferences for API key storage (AES-256-GCM via Android Keystore)

### Terminal Features
- Built-in terminal multiplexer: split panes with binary tree layout, max 4 panes (14 tests)
- Picture-in-Picture floating terminal with UI adaptation (hide chrome in PiP)
- Voice input via Android SpeechRecognizer (mic button in ExtraKeysBar)
- Scrollback buffer configurable in Settings (1K, 5K, 10K, 25K, 50K lines)
- Dynamic session shortcuts (long-press app icon shows active sessions)
- Notification "Float" action button for PiP from notification
- Command-not-found bash hook (suggests `pkg install <package>`)
- DECSET 45 reverse wrap-around implementation
- Sixel image parser in Rust core (sixel-image + sixel-tokenizer crates, 10 tests)

### Shell Enhancements
- New functions: `extract()`, `weather()`, `cheat()`, `command_not_found_handler()`
- New aliases: `ports`, `myip`, `timer`
- Environment: TERM_PROGRAM=novaterm, COLORTERM=truecolor, TERM_PROGRAM_VERSION

### MCP Server
- 4 new tools: create_session, get_session_output, wait_for_output, get_terminal_info (10 total)
- Rate limiting: 60 requests/min sliding window (6 tests)
- Bearer token authentication: UUID per session, app-private token file

### Accessibility
- TalkBack basics: announceForAccessibility (throttled), content descriptions

### Infrastructure
- GitHub organization `nvterm` with 6 repos and professional CI/CD
- Dependabot with auto-merge for minor/patch updates
- Release workflow (automated APK on tag push)
- Security audit workflow (weekly cargo-audit)
- AI contribution policy (AI_POLICY.md)
- CHANGELOG.md, .editorconfig, CODEOWNERS, crash report template

## Changes

- OSC 52 clipboard buffer expanded from 8KB to 64KB
- Migrated GitHub org from PrometeoDEV to nvterm
- Complete com.termux to com.novaterm Java package migration
- Renamed LegacyTermuxEngine to LegacyEngine, TermuxSessionManager to SessionManager
- Native libnovaterm_pty.so replaces libtermux.so
- Notification: replaced Wake Lock toggle with Float action
- Target expanded: all Android 11+ phones (not just flagships)
- Squash-only merges, auto-delete branches on merge

## Bug Fixes

- 18 bugs fixed in deep audit (memory leaks, race conditions, ANR)
- Hardcoded Termux paths in Rust tests and renderer
- dpkg wrapper for recursive dirs and large packages
- Cursor race condition guard documented
- Compilation errors (float-to-int cast, missing coroutine imports)
- Voice input race condition (getOrNull prevents IndexOutOfBoundsException)
- Thread-unsafe paneManagers map replaced with ConcurrentHashMap
- Session preset delay increased to 1000ms with isRunning check
- Handler callback leak in onDestroy (removeCallbacks instead of removeAll)
- Missing resize listener cleanup in removeSession
- PiP guard when no active sessions
- Lint error: StringFormatMatches %d to %1$d
- Force unwrap (!!) eliminated in McpServer, ExtraKeysBar

## Security

- RUN_COMMAND intent permission verification (checkCallingOrSelfPermission)
- Deep link nvterm://run disabled (prevents command injection from websites)
- Sensitive data redacted from logs (Log.d + char count only)
- AutoApprovalManager denies DANGEROUS tools from localhost

---

## Installation

Download the APK from the [Releases page](https://github.com/nvterm/NovaTerm/releases/tag/v0.3.0-alpha).

**Requirements:**
- Android 11 (API 30) or later
- arm64 (aarch64) processor
- ~120 MB storage

## Known Limitations

- GPU renderer (wgpu/Vulkan) is code-complete but needs device testing -- currently uses Canvas rendering
- On-device LLM (command prediction) is planned for a future release
- Alpha software: expect rough edges and please report issues

## Full Changelog

[v0.2.0-alpha...v0.3.0-alpha](https://github.com/nvterm/NovaTerm/compare/v0.2.0-alpha...v0.3.0-alpha)
