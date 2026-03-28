# NovaTerm Technology Stack Research
**Date:** March 2026 | **Author:** NovaTerm Research

---

## Current Dependencies (in project) vs Recommended

| Dependency | Current | Recommended | Notes |
|---|---|---|---|
| AGP | 8.7.3 | **9.1.0** | ALERT: removes NDK/JNI in library modules |
| Kotlin | 2.1.0 | **2.3.20** | K2 compiler standard, inline class changes |
| Compose BOM | 2024.12.01 | **2026.03.00** | 15+ months of improvements |
| Material3 | 1.3.1 | **1.4.x** (via BOM) | M3 Expressive in alpha |
| core-ktx | 1.15.0 | **1.18.0** | Minor |
| Lifecycle | 2.8.7 | **2.11.1** | Significant |
| Activity Compose | 1.9.3 | **1.12.3** | Significant |
| Navigation | 2.8.5 | **2.9.7** (Nav2) or **Nav3 1.0.0-alpha10** | Nav3 is future |
| WindowManager | 1.3.0 | **1.5.0** | 5 breakpoints now |
| Compile SDK | 36 | **36** | Correct |
| Target SDK | 35 | **36** | Required Play Store Aug 2026 |
| NDK | 29.0.14206865 | **29.0.14206865** | Correct |
| Gradle | ? | **9.4.1** | Required by AGP 9 |
| JVM Target | 17 | **21** | Recommended with AGP 9 |

### CRITICAL: AGP 9 and JNI in Library Modules

AGP 9 removes NDK/JNI support in library modules. `core/terminal-emulator` has JNI (PTY native code). Options:
1. Stay on AGP 8.x for now (will lose support eventually)
2. Move JNI code to the app module (breaks modular architecture)
3. Investigate community workarounds for AGP 9

**Recommendation:** Stay on AGP 8.7.3 for Phase 1, plan migration for Phase 2 when we rewrite the terminal core in Rust anyway.

### Navigation 3 vs Navigation Compose (Nav2)

Navigation 3 is stable since November 2025 and is the recommended future:
- Back stack controlled by you (SnapshotStateList)
- Type-safe (no string routes)
- Scene API for adaptive layouts (critical for Phase 4 desktop mode)
- Compose-first from the ground up

**Recommendation:** Keep Nav2 for Phase 1, migrate to Nav3 when implementing Phase 4 adaptive layouts.

---

## Phase 2: Vulkan GPU Renderer + Rust Core

### Recommended Stack

```
Kotlin/Compose (UI layer)
  |
  AndroidExternalSurface (Compose Foundation)
  |    - Renders on separate layer via system compositor
  |    - Bypasses GPU composition = better battery
  |
  JNI (passes ANativeWindow to Rust)
  |
  cargo-ndk 4.1.2 (build toolchain)
  |
  UniFFI 0.31.0 (API bindings Kotlin<->Rust)
  + JNI direct (render loop hot path, avoid UniFFI overhead)
  |
  wgpu 29.0.0 (GPU abstraction over Vulkan)
  |    - Android Vulkan backend is first-class
  |    - GLES 3.0 fallback available
  |    - Same code can target browser (WebGPU) and desktop
  |
  Glyph Atlas Renderer
  |    - fontdue crate for glyph rasterization (fast, pure Rust)
  |    - Grayscale atlas for text + BGRA atlas for emoji
  |    - Instanced rendering with 2 draw calls (background + foreground)
  |    - Damage tracking via rangeset crate (only redraw changed cells)
  |
  alacritty_terminal 0.25.1 (VT parser + terminal state)
  OR libghostty-vt (when Android support lands)
```

### Key Crates

| Crate | Version | Purpose |
|---|---|---|
| `wgpu` | 29.0 | GPU rendering abstraction |
| `alacritty_terminal` | 0.25 | Terminal emulation |
| `cargo-ndk` | 4.1.2 | Android build toolchain |
| `uniffi` | 0.31 | Kotlin binding generation |
| `jni` | 0.21 | Direct JNI for hot paths |
| `fontdue` | latest | Glyph rasterization |
| `raw-window-handle` | 0.6 | Window abstraction |
| `rangeset` | latest | Damage tracking |

### Why wgpu over raw Vulkan (ash)

- Abstracts Vulkan/GLES/WebGPU - single codebase for all backends
- Proven in production by Rio terminal
- Less boilerplate than ash
- Future-proof: same code can target browser via WebGPU
- GLES 3.0 fallback for edge cases

### Why NOT ash (raw Vulkan)

- Much more code to write and maintain
- Only Vulkan (no fallback)
- Only needed if we need extreme optimization beyond wgpu's capabilities
- Ghostty uses platform-native APIs (Metal/GL), not raw Vulkan

### Rendering Architecture (inspired by Ghostty + Alacritty + Windows Terminal)

**Threading model:**
1. **Main Thread** - Compose UI + input handling
2. **I/O Thread** - PTY communication + VT parsing
3. **Render Thread** - GPU rendering (independent frame rate)
4. **PTY Read Thread** - Dedicated blocking read (like Ghostty)

**Glyph Atlas approach:**
- Pre-rasterize glyphs to texture atlas on CPU (fontdue)
- Two atlas types: grayscale (text) and BGRA (emoji/color fonts)
- SharedGridSet pattern to share atlas between multiple terminal tabs
- Deferred font loading (only load metadata until glyph needed)
- Sprite font for box drawing characters (programmatic, not from font files)

**Frame rendering (2 draw calls, Alacritty pattern):**
1. Draw call 1: Background colors (instanced quads)
2. Draw call 2: Foreground glyphs (instanced textured quads from atlas)
Result: ~500fps possible on modern hardware

**Damage tracking:**
- Track changed cells per frame using rangeset
- Only upload changed instance data to GPU
- Minimize GPU work for typical terminal usage (most cells don't change)

### Font Rendering Pipeline

```
Font files (TTF/OTF)
  |
  HarfBuzz (text shaping - ligatures, complex scripts)
  |    - Compile via NDK, or use Android's built-in
  |
  FreeType (glyph rasterization to bitmap)
  |    - Available via NDK
  |
  Glyph Atlas (texture on GPU)
  |    - Pack bitmaps into atlas texture
  |    - LFU eviction when atlas is full (wezterm lfucache pattern)
  |
  wgpu instanced rendering
```

### Dimensity 9400 GPU Notes

- GPU: Immortalis-G925, 24 clusters, Vulkan 1.3
- 134fps in GFX Aztec 1440P Vulkan benchmark
- 30% advantage over competition at 40% less power

**Known gotchas:**
- Aggressive thermal throttling under sustained load (not a problem for terminal - low GPU load)
- Discard with stencil testing bugs (avoid this combination)
- Don't invert viewport depth (minDepth/maxDepth)
- Set all 4 components (xyzw) to NaN, not just some
- Empty render passes with only CLEAR can cause black block artifacts

### libghostty-vt (Future Option)

Ghostty's extracted VT library:
- Zero dependencies (not even libc)
- SIMD-optimized parsing
- Supports Kitty Graphics Protocol, tmux Control Mode
- Currently has issues with Android's bionic libc (DT_NEEDED, dlopen fails)
- When Android support lands, could replace both Termux's Java VT parser and alacritty_terminal

### Terminal Protocols to Support

| Protocol | Priority | Why |
|---|---|---|
| Kitty Graphics Protocol | High | De facto standard for inline images, animations |
| Kitty Keyboard Protocol | High | Precise key reporting |
| OSC 8 (hyperlinks) | High | Clickable links in terminal |
| OSC 52 (clipboard) | High | Critical for tmux/SSH clipboard |
| OSC 133 (shell integration) | High | Semantic zones (prompt/command/output) |
| Mode 2027 (grapheme clusters) | High | Correct Unicode rendering |
| Synchronized output (Mode 2026) | Medium | Prevent tearing |
| OSC 66 (complex scripts) | Medium | Differentiator - only Kitty/Foot support it |
| Sixel | Low | Legacy, Kitty Graphics is superior |
| iTerm2 inline images | Low | Fallback for tools that only support this |

---

## Phase 3: AI On-Device + MCP

### Recommended Stack

```
Layer 1: Inference Engine
  Primary: llama.cpp (b8554, March 2026) via kotlinllamacpp
    - Mature, stable, CPU-based
    - 10-20 tok/s with Q4 models on flagships
    - Lazy loading avoids loading entire model in RAM
    - i-quants for extreme compression

  Secondary: LiteRT-LM v0.9.0-alpha03
    - When NPU support matures for Dimensity 9400
    - Kotlin-native API with coroutines
    - sendMessageAsync() returns Flow<Message> for streaming

Layer 2: Model
  Default: Gemma-3n-E4B (Q4)
    - 8B params, runs like 4B, ~3GB RAM
    - Multimodal (text + image + audio)
    - Leaves ~8-9GB free for OS + app

  Lightweight: FunctionGemma-270M
    - For fast function calling

  Alternative: Qwen3-4B (Q4_K_M GGUF)
    - Excellent at code + multilingual
    - ~2.5GB RAM

Layer 3: Protocol
  MCP Kotlin SDK (official, modelcontextprotocol/kotlin-sdk)
    - Supports JVM, Native, JS, Wasm
    - Transports: stdio, Streamable HTTP, SSE, WebSocket

  Android MCP SDK (dev.jasonpearson:mcp-android-sdk:1.0.0)
    - Auto-init via AndroidX Startup
    - WebSocket (port 8080) + HTTP/SSE (port 8081)

  Embedded MCP server in NovaTerm
    - Expose terminal tools/resources to AI

Layer 4: UX
  Streaming via Kotlin Flow
  Terminal context as MCP resource
```

### Dimensity 9400 NPU Notes

- NPU 890: ~50 TOPS, supports speculative decoding
- NeuroPilot delegate for LiteRT-LM
- **Known bug**: Gemma-3n-E2B shows ~1 tok/s on GPU vs ~6.5 tok/s on CPU
- GPU/NPU optimization for MediaTek is less mature than Qualcomm
- **Recommendation**: Start with llama.cpp on CPU, migrate to LiteRT-LM NPU when NeuroPilot matures

### Comparison: LiteRT-LM vs llama.cpp vs ExecuTorch

| Aspect | LiteRT-LM | llama.cpp | ExecuTorch |
|---|---|---|---|
| Maturity | Alpha (v0.9.0-a03) | Very mature (b8554) | GA (v1.1.0) |
| Kotlin API | Native, excellent | Via kotlinllamacpp | React Native only |
| NPU support | Via NeuroPilot | No NPU | Qualcomm/MediaTek |
| Model format | Custom | GGUF (standard) | .pte (AOT compiled) |
| Streaming | Flow<Message> | Callbacks | Callbacks |
| DX for Kotlin | Best | Good | Poor |

---

## Phase 4: Desktop Mode + Plugins

### Desktop Mode (Android 16 QPR3, GA March 2026)

- Freeform windows on external monitor via USB-C
- Samsung DeX replaced by native desktop mode in One UI 8
- One implementation covers both Pixel and Samsung

**Key APIs:**
- `DisplayManager.DisplayListener`: onDisplayAdded/Changed/Removed
- `getDisplays(DISPLAY_CATEGORY_PRESENTATION)` for external screens
- WindowManager 1.5: 5 breakpoints (Compact/Medium/Expanded/Large/Extra-large)
- Navigation 3 Scene API for multi-pane adaptive layouts
- Compose M3 Adaptive 1.2+: `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)`

### Plugin System

```
Layer 1 (MVP): Lua scripting
  LuaJ v3.0.2 (pure Java, Android compatible)
  For: themes, keybindings, output filters, simple hooks
  ~10x slower than desktop JVM, OK for scripting
  Fork: gudzpoz/luajava supports Lua 5.1-5.5

Layer 2: WASM plugins
  Chicory 1.7.5 (pure Java, zero JNI, Android tested)
  chicory-compiler-android v0.0.1 (WASM->Dalvik, experimental)
  Limitation: 200MB+ heap for compilation, 8MB stack thread
  WASM proposals: SIMD, Tail Calls, Exception Handling, Threads

  Architecture reference: Zellij (v0.44)
  - wasmi interpreter + Protocol Buffers for messages
  - Strict isolation: plugins can't touch filesystem/network directly
  - Explicit permissions: RunCommands, WebAccess, ReadCliPipes
  - WASI mounts per plugin: /host, /data, /cache, /tmp
```

### NOT Recommended

| Technology | Why not |
|---|---|
| GraalVM/Truffle | Memory footprint too large for 12GB mobile |
| Extism Java SDK (classic) | Requires native library |
| Wasmtime on Android | Poorly tested, requires NDK |
| WASI Component Model | Still evolving (1.0 expected late 2026) |
| Termux model (separate APKs) | Fragmented, poor UX, deprecated sharedUserId |

---

## Differentiators

NovaTerm would be the FIRST to achieve:
1. **GPU rendering on Android** - No Android terminal has this
2. **OSC 66 complex scripts** - Only Kitty and Foot support it (new March 2026)
3. **Real accessibility** via AccessibilityNodeInfo + OSC 133 semantic zones
4. **On-device AI with MCP** - No mobile terminal has this
5. **WASM plugins** - First mobile terminal with plugin system

---

## Sources

### Android Dev Stack
- AGP 9.1.0 Release Notes - developer.android.com
- Kotlin 2.3.20 Release - blog.jetbrains.com
- Compose BOM - developer.android.com
- Navigation 3 Stable - android-developers.googleblog.com
- WindowManager 1.5 - android-developers.googleblog.com

### GPU Rendering
- wgpu GitHub - github.com/gfx-rs/wgpu
- wgpu-in-app (Android integration) - github.com/jinleili/wgpu-in-app
- Ghostty GitHub - github.com/ghostty-org/ghostty
- Alacritty GitHub - github.com/alacritty/alacritty
- Windows Terminal Atlas Engine - deepwiki.com/microsoft/terminal
- Warp: Adventures in Text Rendering - warp.dev/blog
- Slug Algorithm public domain - hackaday.com (March 2026)

### AI On-Device
- LiteRT-LM GitHub - github.com/google-ai-edge/LiteRT-LM
- llama.cpp - github.com/ggml-org/llama.cpp
- Gemma 3n - ai.google.dev/gemma/docs/gemma-3n
- MCP Kotlin SDK - github.com/modelcontextprotocol/kotlin-sdk
- Android MCP SDK - kaeawc.github.io/android-mcp-sdk

### Desktop + Plugins
- Android 16 Desktop Mode - android-developers.googleblog.com
- Chicory WASM - github.com/dylibso/chicory
- Zellij Plugin System - deepwiki.com/zellij-org/zellij
- Extism Chicory SDK - github.com/extism/chicory-sdk
