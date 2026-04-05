# NovaTerm

[![CI](https://github.com/nvterm/NovaTerm/actions/workflows/ci.yml/badge.svg)](https://github.com/nvterm/NovaTerm/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-11%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-purple.svg)](https://kotlinlang.org)
[![Rust](https://img.shields.io/badge/Rust-1.80%2B-orange.svg)](https://www.rust-lang.org)
[![GitHub release](https://img.shields.io/github/v/release/nvterm/NovaTerm?include_prereleases)](https://github.com/nvterm/NovaTerm/releases)

Next-generation Android terminal emulator. Rust-powered, GPU-accelerated, AI-native. Runs on any Android 11+ phone.

Modern UI (Kotlin + Jetpack Compose) with a Rust core (wgpu + alacritty_terminal) and on-device AI. Built for developers who live in the terminal — from budget phones to flagships.

## Highlights

- **GPU-accelerated** — Vulkan compute shaders via wgpu (Adreno, Mali, PowerVR, Xclipse)
- **Rust core** — VT parser (alacritty_terminal), PTY, renderer — 177 tests
- **AI-first** — API key management, one-tap AI tool installer, session presets
- **On-device AI** — Gemma 4 E2B for command suggestions, no cloud needed
- **AI CLI ready** — Optimized for Claude Code, Gemini CLI, Aider, Codex
- **PiP floating window** — Multitask with a floating terminal overlay
- **7 color schemes** — Gruvbox, Catppuccin, Solarized, Monokai, Nord, Dracula
- **Session persistence** — Survives app kill, boot restore, crash-safe history
- **MCP server** — 6 tools for AI agents to control the terminal
- **Touch-first** — 48dp targets, swipe tabs, extra keys with popups

## Features

| Category | Features |
|----------|----------|
| **Terminal** | Truecolor, 10K scrollback, bracketed paste, Unicode/emoji, OSC 52 clipboard (64KB), synchronized output (DEC 2026), DECSET 45 |
| **AI** | Shift+Enter (CSI 13;2u), OSC 133 semantic prompts, OSC 9 notifications, TERM_PROGRAM detection, API key management, AI tool installer, session presets, AI Coding extra keys |
| **Sessions** | Swipe tabs, rename, close confirmation, SQLite block store, CAS dedup, scrollback configurable 1K-50K, session presets |
| **Shell** | Professional .profile/.bashrc/.inputrc, XDG dirs, storage symlinks, command-not-found handler |
| **UX** | History search, clickable URLs/IPs/paths, smart notifications, OEM battery guides, PiP floating window, voice input, dynamic shortcuts |
| **Security** | MCP approval manager, blocked commands, localhost-only, no telemetry |

## Architecture

```
app/                    Kotlin + Compose — UI, Service, ViewModel
core/
  bootstrap/            First-launch package extraction (bash, apt, coreutils)
  common/               Shared contracts and models
  session/              Session lifecycle, Rust JNI bridge, SQLite persistence
  terminal-emulator/    VT parser + PTY JNI (from Termux, Apache 2.0)
  terminal-view/        Canvas renderer + gestures (from Termux, Apache 2.0)
  mcp/                  AI agent protocol server (6 tools)
  llm/                  On-device LLM (Gemma 4 E2B, GGUF)
feature/
  terminal/             Terminal UI, extra keys, entity detection, color palettes
  settings/             Preferences, color scheme picker, model download
  oem-compat/           OEM detection + battery optimization guides
rust-core/              Cargo workspace — 4 crates, 177 tests
  novaterm-vt/          VT parser (alacritty_terminal 0.25.1)
  novaterm-bridge/      JNI bridge (23 exports)
  novaterm-pty/         Safe Rust PTY (replaces C)
  novaterm-renderer/    GPU renderer (wgpu 29, Vulkan compute shaders)
```

## Build

```bash
# Debug APK
./gradlew assembleDebug

# Tests (Kotlin — 270+ tests)
./gradlew test

# Rust tests (177 tests)
cd rust-core && cargo test --workspace

# Rust with Vulkan GPU
./build-android.sh --release --vulkan
```

### Requirements

- Android SDK (compileSdk 36, minSdk 30)
- JDK 17
- Rust 1.80+ (for rust-core)
- NDK 27+ (for cross-compilation)

### Building on Termux

NovaTerm can be built directly on Android:

```bash
# Rust compiles natively (no cargo-ndk needed)
cd rust-core && cargo build -p novaterm-bridge --features gpu --release

# Full APK (requires aapt2 ARM64 package)
pkg install aapt2
./gradlew assembleDebug
```

## Roadmap

- [x] **Phase 1** — Functional terminal with bootstrap + Termux packages
- [x] **Phase 2a** — Rust core (4 crates, 177 tests, JNI bridge)
- [x] **Phase 2b** — Vulkan GPU renderer (wgpu compute shaders)
- [x] **Phase 3** — AI integration (MCP server, Gemma 4, predictions)
- [ ] **Phase 4** — Desktop mode (Android 16+) + plugin system

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup and guidelines.

## Security

See [SECURITY.md](SECURITY.md) for our security policy and vulnerability reporting.

## License

Apache License 2.0. See [LICENSE](LICENSE).

Terminal emulation core from [Termux](https://github.com/termux/termux-app) (Apache 2.0).
