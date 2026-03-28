# NovaTerm

Next-generation Android terminal emulator. Modern UI built with Kotlin + Jetpack Compose on top of Termux's battle-tested terminal emulation engine.

## Features

- **7 color schemes** with live preview and hot-reload (Gruvbox, Catppuccin, Solarized, Monokai, Nord, Dracula)
- **Swipe between sessions** with HorizontalPager
- **Smart notifications** when commands finish in background
- **Clickable URLs** with confirmation dialog
- **Session persistence** — survives app kill, restores on next launch
- **SQLite block store** with content-addressable dedup
- **OSC 133 semantic zones** for structured command history
- **History search** with fuzzy matching
- **Professional shell** out-of-the-box (.profile, .bashrc, .inputrc)
- **DocumentsProvider** — browse terminal files from any Android file manager
- **Extra keys** optimized for developers (pipe, dash, Ctrl, Alt, arrows)
- **Shift+Enter** for multiline input (Claude Code, Gemini CLI)
- **XDG compliant** directory structure
- **AI-first** — TERM_PROGRAM=novaterm, truecolor, large scrollback
- **OEM battery optimization** guides for Xiaomi, Samsung, Huawei, etc.

## Architecture

```
app/                    Kotlin + Compose — UI, Service, ViewModel
core/
  bootstrap/            Bootstrap installer (JNI + extraction)
  common/               Contracts, models, utilities
  config/               DataStore preferences
  session/              Session management + SQLite persistence
  terminal-emulator/    VT parser, grid, PTY JNI (from Termux, Apache 2.0)
  terminal-view/        Canvas renderer, gestures (from Termux, Apache 2.0)
feature/
  terminal/             Terminal UI, extra keys, URL detection, color palettes
  settings/             Preferences, color scheme picker, onboarding
  oem-compat/           OEM detection + battery optimization
```

## Build

Requires Android SDK with NDK 29. Target SDK 35, compile SDK 36.

```bash
./gradlew assembleDebug
./gradlew test
```

## Target

- **minSdk 30** (Android 11)
- **arm64-v8a only** — optimized for modern flagships
- **Vulkan 1.1+** required (Phase 2 GPU rendering)

## Roadmap

- **Phase 1**: Functional terminal with bootstrap + Termux packages *(current)*
- **Phase 2**: Vulkan GPU renderer + Rust core (alacritty/vte)
- **Phase 3**: AI integration (LiteRT-LM + NPU + MCP servers)
- **Phase 4**: Desktop mode (Android 16 APIs) + plugin system

## License

Apache License 2.0. See [LICENSE](LICENSE).

Terminal emulation core from [Termux](https://github.com/termux/termux-app) (Apache 2.0).
