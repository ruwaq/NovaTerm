# Terminal Emulator Architecture Reference
**Date:** March 2026 | **Author:** NovaTerm Research

Analysis of how the best terminal emulators are built, with lessons for NovaTerm.

---

## Ghostty (Zig, 49K stars)

**Creator:** Mitchell Hashimoto (HashiCorp founder)
**License:** MIT | **Status:** Very active, non-profit under Hack Club

### Architecture
- Core library (libghostty) + native GUI per platform (>90% shared code)
- C-ABI boundary between core and GUI
- macOS: Swift/AppKit, Linux: GTK4 via Zig

### Threading
- Main Thread: UI
- I/O Thread: PTY + VT parsing
- Render Thread: GPU
- PTY Read Thread: dedicated blocking read
- Communication via SPSC lock-free queues (64 messages)

### Rendering
- Metal (macOS), OpenGL/Vulkan (Linux)
- Grid-based glyph atlas (grayscale + BGRA for emoji)
- SharedGridSet: share atlas between multiple surfaces
- Target 120fps, timer 8ms, frame diffing separate from GPU execution
- 2ms key-to-screen latency

### Font System
- HarfBuzz (Linux) / CoreText (macOS) for shaping
- FreeType (Linux) / CGBitmapContext (macOS) for rasterization
- Deferred font loading (metadata only until glyph needed)
- Sprite font for box drawing (programmatic, not from font files)
- Nerd Fonts constraint system via codegen

### libghostty-vt
- Extracted VT library, zero dependencies
- SIMD-optimized parsing
- Supports Kitty Graphics, tmux Control Mode
- Plans: libghostty-font, libghostty-renderer
- Android: not yet (bionic libc issues with DT_NEEDED, dlopen)

### Lessons for NovaTerm
- Core-as-library + native-GUI is the winning pattern
- Dedicated threading per function is critical
- SharedGridSet saves memory with multiple tabs
- Deferred font loading is important on mobile (memory constraints)

---

## Alacritty (Rust, 63K stars)

**Maintainers:** Christian Duerr, Kirill Chibisov
**License:** Apache 2.0 | **Status:** Active (v0.17-rc1 March 2026)

### What Makes It Fast
- Only **2 draw calls** per frame (instanced rendering)
- ~500 FPS possible with full screen of text
- ~30MB RAM (most memory-efficient)
- Damage tracking: only redraw changed cells
- Zero-copy I/O: PTY data flows directly to parser
- Table-driven VT parser (procedural macro generated)
- String pooling for cell attributes

### Architecture
- Deliberately minimal: no tabs, no splits, no multiplexing
- `alacritty_terminal` crate usable as library
- Dual renderer: GLES2 (legacy) + GLSL3 (modern)
- crossfont crate for cross-platform rasterization

### Lessons for NovaTerm
- 2 draw calls pattern is the gold standard
- Damage tracking is essential for battery life on mobile
- Zero-copy PTY->parser pipeline reduces latency
- Minimalism works for a dedicated audience

---

## WezTerm (Rust, 25K stars)

**Creator:** Wez Furlong (solo, side project)
**License:** MIT | **Status:** Last stable release Feb 2024 (!)

### Architecture
- Multiplexer integrated: `Mux` singleton, thread-safe via `Arc<Mux>`
- Domain trait: LocalPane, ClientPane, RemoteSshDomain
- PtyReader thread per pane

### Rendering Pipeline
1. PTY -> `Terminal::advance_bytes()` -> Parser -> State
2. `MuxNotification::Alert` -> `schedule_next_frame()` -> paint
3. GlyphCache with LFU eviction
4. Dirty region tracking with rangeset crate
5. Layer composition: glyphs, images, selection, cursor, overlay

### Lua Configuration
- mlua runtime, ~50 exported functions
- Event system: `wezterm.on(event, callback)`
- Hot reload via file watcher
- Bidirectional type bridging

### Lessons for NovaTerm
- LFU eviction for glyph cache is critical on Android (limited RAM)
- Dirty region tracking with rangeset is efficient
- Domain trait pattern enables future multiplexing/SSH
- WARNING: solo maintainer risk - WezTerm shows what happens

---

## Kitty (Python/C, 32K stars)

**Creator:** Kovid Goyal (also created Calibre)
**License:** GPL-3.0 | **Status:** Very active (v0.46.2)

### Kitty Graphics Protocol (de facto standard)
- Transmission: Direct (base64), File, Temp file, Shared memory, Chunked
- Formats: RGB, RGBA, PNG with zlib compression
- Unicode placeholders: images that move with text
- Animations: frame transmission + control
- Streaming for large files (0.32+)
- Adopted by: WezTerm, Ghostty, Konsole, foot

### Kittens (Extension System)
- Python scripts with access to kitty internals
- Overlay window over current terminal
- Access to Remote Control API

### Lessons for NovaTerm
- Kitty Graphics Protocol should be primary image protocol
- Unicode placeholders for image-text integration
- Keyboard protocol for precise input handling

---

## Zellij (Rust, 30K stars)

**Creator:** Aram Drevekenin
**License:** MIT | **Status:** Active (v0.44)

### WASM Plugin System
- Runtime: wasmi v0.51 (interpreter, not JIT - secure, sandboxed)
- Communication: Protocol Buffers (prost)
- Strict isolation: no direct filesystem/network access
- Explicit permissions: RunCommands, WebAccess, ReadCliPipes
- WASI mounts per plugin: /host, /data, /cache, /tmp
- Workers: `*_worker` functions spawn WASM threads

### Layout System
- KDL (KDL Document Language) for declarative layouts
- Tiled + floating + stacked panes

### Lessons for NovaTerm
- WASM + protobuf + permissions is the gold standard for plugins
- Strict isolation is non-negotiable for security
- KDL is good for declarative layouts

---

## Rio (Rust, 6.5K stars)

**Creator:** Raphael Amorim
**License:** MIT | **Status:** Active

### WGPU Approach
- Renderer "Sugarloaf": DX11/12, Metal, Vulkan, GLES3, WebGPU
- Redux state machine: only redraw changed lines
- Tokio runtime for async I/O
- Works in browser via WASM + WebGPU

### Lessons for NovaTerm
- WGPU is proven for terminal rendering
- Same code can target multiple backends
- "Minimal redraw" via state machine is battery-efficient

---

## Windows Terminal (C++, ~90K stars)

**Team:** ~6-8 Microsoft engineers

### Atlas Rendering Engine
- Built by Leonard Hecker
- DirectWrite + Direct3D 11
- Dynamic texture atlas caching glyphs
- Renders as textured quads - extremely fast
- Open source (MIT)

### Lessons for NovaTerm
- A small dedicated team (6-8) can build world-class terminal
- Atlas renderer approach is correct
- Being the default OS terminal is the ultimate distribution advantage

---

## Warp (Rust, closed source, $73M VC)

**Creator:** Zach Lloyd (ex-Google)
**Team:** ~50+ employees

### Innovation: Command Blocks
- Each command is an interactive block (input + output grouped)
- AI can analyze context per block
- Copy/share individual command outputs

### Business Validation
- $1M ARR every 10 days (2025)
- 500K+ active users
- Proves AI-in-terminal is a real market

### What NOT to copy
- Closed source
- Required login (now optional)
- Telemetry controversy
- VC pressure to monetize

---

## Comparison Table

| Terminal | Stars | Lang | Renderer | Multiplexer | AI | Images | Plugins |
|---|---|---|---|---|---|---|---|
| Alacritty | 63K | Rust | OpenGL | No | No | No | No |
| Ghostty | 49K | Zig | Metal/Vk/DX | No | No | Kitty | No |
| Kitty | 32K | Py/C | OpenGL | Sessions | No | Kitty (creator) | Kittens (Py) |
| Zellij | 30K | Rust | N/A (mux) | Core feature | No | N/A | WASM |
| Warp | 26K | Rust | Metal/Vk | Yes | Yes | Blocks | No |
| WezTerm | 25K | Rust | GL/M/Vk | Yes | No | Kitty+iTerm2 | Lua |
| Wave | 19K | Go | Electron | Yes | Yes (BYOK) | Preview | No |
| Rio | 6.5K | Rust | WGPU | Tabs | No | WIP | Planned |

---

## Protocol Support Matrix

| Protocol | Ghostty | Kitty | WezTerm | Alacritty | Foot |
|---|---|---|---|---|---|
| Kitty Graphics | Yes | Yes (creator) | Yes | No | Yes |
| Kitty Keyboard | Yes | Yes (creator) | Yes | No | Yes |
| OSC 8 (hyperlinks) | Yes | Yes | Yes | Yes | Yes |
| OSC 52 (clipboard) | Yes | Yes | Yes | Yes | Yes |
| OSC 133 (shell integration) | Yes | Yes | Yes | No | Yes |
| Mode 2027 (grapheme clusters) | Yes | Yes | Yes | No | Yes |
| Sixel | No | No | Yes | No | Yes |
| OSC 66 (complex scripts) | PR open | Yes | No | No | Yes |

---

## Sources

- [Ghostty GitHub](https://github.com/ghostty-org/ghostty)
- [Ghostty DeepWiki](https://deepwiki.com/ghostty-org/ghostty)
- [Alacritty DeepWiki](https://deepwiki.com/alacritty/alacritty)
- [WezTerm DeepWiki](https://deepwiki.com/wezterm/wezterm)
- [Kitty Graphics Protocol](https://sw.kovidgoyal.net/kitty/graphics-protocol/)
- [Zellij Plugin System](https://deepwiki.com/zellij-org/zellij)
- [Rio Terminal](https://rioterm.com/)
- [Windows Terminal Atlas Engine](https://deepwiki.com/microsoft/terminal)
- [Warp 2.0 Announcement](https://www.warp.dev/blog/reimagining-coding-agentic-development-environment)
- [State of Terminal Emulators 2025](https://www.jeffquast.com/post/state_of_terminal_emulation_2025/)
