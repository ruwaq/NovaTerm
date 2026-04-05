# NovaTerm - Play Store Listing

## App Name
NovaTerm - AI Terminal

## Short Description (80 chars max)
Rust-powered Android terminal with AI tools, GPU rendering, and split panes

## Full Description

NovaTerm is a next-generation terminal emulator for Android, built from the ground up for modern phones. It combines a proven terminal core with a Rust backend, Jetpack Compose UI, and first-class AI tool support.

WHAT IS NOVATERM?

A full Linux terminal on your Android phone. Run bash, install packages, SSH into servers, write code, and use AI coding assistants -- all from your pocket. NovaTerm is designed for developers, sysadmins, and power users who need a real terminal on mobile.

KEY FEATURES

- 7 color schemes with hot-reload (Gruvbox Dark, Catppuccin Mocha, Nord, Dracula, Solarized, Monokai, Gruvbox Light)
- Built-in split panes (up to 4 panes, no tmux required)
- Picture-in-Picture floating terminal
- Configurable extra keys bar with symbol popups
- Scrollback search and configurable buffer (up to 50K lines)
- Voice input via Android speech recognition
- Session persistence and restore on boot
- Clickable URLs and smart entity detection
- Dynamic app shortcuts for active sessions
- 7 shell functions built-in (extract, weather, cheat, and more)
- OEM battery optimization guides (Xiaomi, Samsung, Huawei, and others)

AI INTEGRATION

NovaTerm is the first Android terminal designed for AI coding tools:

- One-tap installer for Claude Code, Gemini CLI, Aider, and OpenCode
- Dedicated AI extra keys (Ctrl+O transcript, ! bash prefix, Ctrl+B scroll)
- Encrypted API key storage (AES-256-GCM via Android Keystore)
- Session presets: long-press the app icon to launch directly into your favorite AI tool
- MCP server with 10 tools for AI agent orchestration
- Synchronized output mode for flicker-free AI streaming
- Environment auto-configured for AI tools (TERM_PROGRAM, COLORTERM=truecolor)

TECHNICAL HIGHLIGHTS

- Rust core: 4 crates with 177+ tests (VT parser, PTY, JNI bridge, renderer)
- 270+ Kotlin tests across 18 test files
- Alacritty's VT parser (alacritty_terminal 0.25.1) via JNI
- Safe Rust PTY implementation (replaces legacy C code)
- Kitty Keyboard Protocol support
- OSC 133 semantic zones for structured command output
- SQLite session storage with content-addressable deduplication
- XDG Base Directory compliant

HOW IS IT DIFFERENT FROM TERMUX?

NovaTerm builds on Termux's excellent package ecosystem (same packages, same repos) while adding:

- Modern Material 3 UI with Jetpack Compose
- Built-in split panes (no tmux needed for basic splits)
- AI tool integration out of the box
- Rust-powered VT parsing for better performance
- Encrypted credential storage
- Picture-in-Picture mode
- Voice input
- Session presets and app shortcuts

PRIVACY FIRST

- No telemetry, no analytics, no ads, no tracking
- No account required
- API keys encrypted on-device, never transmitted
- MCP server binds to localhost only
- Open source (Apache 2.0)
- Full privacy policy: https://github.com/nvterm/NovaTerm/blob/main/docs/privacy-policy.md

EARLY ACCESS

NovaTerm is in alpha. The core terminal works well, but some features are still being refined. We welcome feedback and contributions.

Source code: https://github.com/nvterm/NovaTerm

SYSTEM REQUIREMENTS

- Android 11 (API 30) or later
- arm64 (aarch64) processor
- ~120 MB storage (including bootstrap packages)

## Category
Developer Tools

## Content Rating
Everyone

## Tags
terminal, emulator, android, rust, ai, coding, developer, ssh, linux

## Feature Graphic Text
"The AI-native terminal for Android"

## Screenshots (descriptions for asset creation)
1. Terminal with Gruvbox Dark theme showing a coding session
2. Color scheme picker during onboarding (7 themes)
3. Split panes with two terminal sessions side by side
4. AI tool installer screen (Claude Code, Gemini CLI, Aider, OpenCode)
5. Extra keys bar with symbol popups
6. Picture-in-Picture floating terminal over another app
7. Settings screen showing configuration options
8. Voice input active in terminal
