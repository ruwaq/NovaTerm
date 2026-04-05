# Changelog

All notable changes to NovaTerm are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- GitHub organization `nvterm` with professional CI/CD
- Dependabot auto-merge for minor/patch updates
- Release workflow (automated APK on tag push)
- AI contribution policy

### Changed
- Migrated all references from `PrometeoDEV` to `nvterm` org
- Complete `com.termux` → `com.novaterm` Java package migration
- Renamed `LegacyTermuxEngine` → `LegacyEngine`, `TermuxSessionManager` → `SessionManager`
- Native `libnovaterm_pty.so` replaces `libtermux.so`

### Fixed
- 18 bugs fixed in deep audit (memory leaks, race conditions, ANR)
- dpkg wrapper for recursive dirs and large packages
- Compilation errors (float-to-int cast, missing coroutine imports)
- Sticky scroll (preserve position while output is flowing)

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

[Unreleased]: https://github.com/nvterm/NovaTerm/compare/v0.2.0-alpha...HEAD
[0.2.0-alpha]: https://github.com/nvterm/NovaTerm/compare/v0.1.0-alpha...v0.2.0-alpha
[0.1.0-alpha]: https://github.com/nvterm/NovaTerm/releases/tag/v0.1.0-alpha
