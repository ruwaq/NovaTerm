# Contributing to NovaTerm

Thank you for your interest in contributing to NovaTerm! This guide will help you get started.

## Getting Started

1. **Fork** the repository
2. **Clone** your fork: `git clone https://github.com/YOUR_USERNAME/NovaTerm.git`
3. **Create a branch**: `git checkout -b feat/your-feature`
4. **Make your changes** and commit
5. **Push** and open a Pull Request

## Development Setup

### Requirements

- Android Studio Ladybug+ or IntelliJ IDEA
- JDK 17
- Android SDK (compileSdk 36, minSdk 30)
- Rust 1.80+ (for rust-core)

### Building

```bash
# Debug APK (requires Android SDK)
./gradlew assembleDebug

# Rust core only
cd rust-core && cargo test --workspace

# Rust with Vulkan GPU renderer
cd rust-core && cargo build -p novaterm-bridge --features gpu --release
```

### On Termux (Android device)

NovaTerm can be built directly on Android via Termux:

```bash
# Rust tests
cd rust-core && cargo test --workspace

# Full APK (requires aapt2 ARM64)
./gradlew assembleDebug
```

## Code Style

- **Kotlin**: Official style, strict mode, no `any` types
- **Java**: Existing Termux code follows its own conventions — don't reformat
- **Rust**: `cargo fmt` + `cargo clippy`
- **Commits**: Semantic (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`)
- **Comments**: English
- **No unnecessary dependencies** — justify any new dependency in your PR

## Architecture

```
app/            Kotlin + Compose — UI layer
core/           Business logic, session management, AI
  bootstrap/    First-launch package extraction
  common/       Shared contracts and models
  session/      Session lifecycle, Rust JNI bridge
  terminal-*    Termux VT parser + Canvas renderer (Phase 1)
  mcp/          AI agent protocol server
  llm/          On-device LLM (Gemma 4)
feature/        Feature modules (terminal UI, settings, OEM compat)
rust-core/      Cargo workspace (VT parser, PTY, GPU renderer, JNI bridge)
```

## What to Work On

- Issues labeled `good first issue` are great starting points
- Check the [Roadmap](CLAUDE.md) for planned features
- Bug reports with reproduction steps are always welcome

## Pull Request Guidelines

- Keep PRs focused — one feature or fix per PR
- Include tests for new functionality
- Update documentation if behavior changes
- Squash commits before merging
- Reference related issues (`Fixes #123`)

## Testing

```bash
# Kotlin tests (270+ tests)
./gradlew test

# Rust tests (160 tests)
cd rust-core && cargo test --workspace
```

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
