# Changelog

All notable changes to NovaTerm are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3.0-alpha] - 2026-04-05

### Added
- GitHub organization `nvterm` with 6 repos and professional CI/CD
- Picture-in-Picture floating terminal with UI adaptation (hide chrome in PiP)
- Voice input via Android SpeechRecognizer (mic button in ExtraKeysBar)
- Scrollback buffer configurable in Settings (1K, 5K, 10K, 25K, 50K lines)
- Dynamic session shortcuts (long-press app icon shows active sessions)
- Notification "Float" action button for PiP from notification
- Command-not-found bash hook (suggests `pkg install <package>`)
- API key management for Anthropic, Google, OpenAI, OpenRouter (masked, auto-export)
- AI tool installer (one-tap install for Claude Code, Gemini CLI, Aider, OpenCode)
- Session presets with app shortcuts (long-press → Claude Code / Gemini CLI)
- AI Coding extra keys style (Ctrl+O transcript, ! bash prefix, Ctrl+B scroll)
- Synchronized output (DEC private mode 2026) for flicker-free AI streaming
- DECSET 45 reverse wrap-around implementation
- Shell functions: extract(), weather(), cheat(), command_not_found_handler()
- Shell aliases: ports, myip, timer
- Environment: TERM_PROGRAM=novaterm, COLORTERM=truecolor, TERM_PROGRAM_VERSION
- AI env auto-config: CLAUDE_CODE_SCROLL_SPEED, AIDER_DARK_MODE
- Sixel image parser in Rust core (sixel-image + sixel-tokenizer crates, 10 tests)
- Built-in terminal multiplexer (split panes, binary tree layout, max 4 panes, 14 tests)
- MCP agent orchestration: 4 new tools (create_session, get_session_output, wait_for_output, get_terminal_info) — total 10 tools
- MCP rate limiting (60 req/min sliding window, 6 tests)
- MCP bearer token authentication (UUID per session, app-private token file)
- TalkBack accessibility basics (announceForAccessibility throttled, content descriptions)
- EncryptedSharedPreferences for API key storage (AES-256-GCM via Android Keystore)
- Dependabot with auto-merge for minor/patch updates
- Release workflow (automated APK on tag push)
- Security audit workflow (weekly cargo-audit)
- AI contribution policy (AI_POLICY.md)
- CHANGELOG.md (Keep a Changelog format)
- .editorconfig, CODEOWNERS, crash report template

### Changed
- OSC 52 clipboard buffer expanded from 8KB to 64KB
- Migrated GitHub org from PrometeoDEV to nvterm
- Complete com.termux → com.novaterm Java package migration
- Renamed `LegacyTermuxEngine` → `LegacyEngine`, `TermuxSessionManager` → `SessionManager`
- Native `libnovaterm_pty.so` replaces `libtermux.so`
- Notification: replaced Wake Lock toggle with Float action
- Squash-only merges, auto-delete branches on merge
- Target: all Android 11+ phones (not just flagships)

### Fixed
- 18 bugs in deep audit (memory leaks, race conditions, ANR)
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
- Lint error: StringFormatMatches %d → %1$d
- Force unwrap (!!) eliminated in McpServer, ExtraKeysBar

### Security
- RUN_COMMAND intent permission verification (checkCallingOrSelfPermission)
- Deep link nvterm://run disabled (prevents command injection from websites)
- Sensitive data redacted from logs (Log.d + char count only)
- AutoApprovalManager denies DANGEROUS tools from localhost

## [0.2.0-alpha] - 2026-04-04

### Added
- Rust core: 4 crates, 177+ tests (novaterm-vt, novaterm-bridge, novaterm-pty, novaterm-renderer)
- GPU rendering: wgpu 29.0 + Vulkan compute shaders (code complete)
- On-device LLM module (Gemma 4 E2B) + N-gram command prediction
- MCP server (6 tools) wired to TerminalService
- AI-powered entity detection (URL, IPv4/IPv6, localhost:port, file paths)
- Scrollback search + configurable extra keys + font family selection
- Kitty Keyboard Protocol + Kitty Graphics Protocol
- SQLite BlockStore + CAS dedup + OSC 133 semantic zones
- Session persistence + boot restore + periodic save (30s)
- Comprehensive terminal test suite (130 tests)
- Self-update script for building from within NovaTerm

### Changed
- Strangler Fig: dual-run validation (Legacy Java ↔ Rust engine)
- Settings toggle for experimental Rust backend

### Fixed
- 17 critical/high/medium fixes across 11 files (comprehensive audit)
- 5 ANR/memory/race/cursor/poll bugs
- ByteQueue.notify() → notifyAll() (thread starvation)
- MainThreadHandler memory leak (static + WeakReference)
- I/O threads without daemon flag

### Security
- ApprovalManager + SecurityPolicy for MCP
- Blocked dangerous commands and paths
- Localhost-only MCP binding

## [0.1.0-alpha] - 2026-03-15

### Added
- Initial release: functional Android terminal emulator
- Kotlin + Jetpack Compose UI with Material 3
- 7 color schemes (Gruvbox Dark default) with hot-reload
- Onboarding with color scheme picker and typing animation
- Swipe tabs, session rename, close confirmation
- Extra keys bar with symbol popups
- Bootstrap system (CI + extraction + W^X bypass)
- DocumentsProvider for file access
- Clickable URLs + history search
- Professional shell config (.profile, .bashrc, .inputrc)
- XDG Base Directory structure
- Smart notifications + status line (CWD + git branch)
- OEM battery optimization guides (Xiaomi, Samsung, Huawei)
- About screen

[Unreleased]: https://github.com/nvterm/NovaTerm/compare/v0.3.0-alpha...HEAD
[0.3.0-alpha]: https://github.com/nvterm/NovaTerm/compare/v0.2.0-alpha...v0.3.0-alpha
[0.2.0-alpha]: https://github.com/nvterm/NovaTerm/compare/v0.1.0-alpha...v0.2.0-alpha
[0.1.0-alpha]: https://github.com/nvterm/NovaTerm/releases/tag/v0.1.0-alpha
