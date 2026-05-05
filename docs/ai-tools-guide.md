# Installing AI Tools in NovaTerm

NovaTerm is a minimal, professional terminal. We do **not** pre-install any AI tools. Instead, you get a full Linux environment (bash, apt, node, python) and install exactly what you need — just like on macOS or Linux.

---

## Quick Start

```bash
# Update package index
apt update

# Install essential tools
apt install nodejs python git curl wget openssh

# Verify
node -v   # v25.8.2
python3 --version  # 3.13.13
```

---

## Claude Code

**Problem:** Claude Code's native binary requires `glibc`. Android uses `bionic`.

**Solution:** Run Claude Code inside a `proot-distro` Debian container.

```bash
# 1. Install proot-distro
apt install proot-distro

# 2. Install Debian
proot-distro install debian

# 3. Enter Debian (now you have glibc)
proot-distro login debian

# 4. Inside Debian: install Node.js and Claude Code
apt update && apt install -y nodejs npm
npm install -g @anthropic-ai/claude-code

# 5. Run Claude Code
claude
```

**Tip:** Create an alias in `~/.bashrc`:
```bash
alias claude='proot-distro login debian -- claude'
```

---

## Gemini CLI

Same approach as Claude Code (requires glibc):

```bash
proot-distro login debian
npm install -g @google/gemini-cli
gemini
```

---

## Ollama

**Option A: Native Termux package (recommended)**

```bash
apt install ollama
ollama serve &
ollama run llama3
```

**Option B: Via proot-distro (official binary)**

```bash
proot-distro login debian
curl -fsSL https://ollama.com/install.sh | sh
ollama serve &
ollama run llama3
```

---

## Aider

Aider is pure Python — works natively without proot:

```bash
pip install aider-chat
aider
```

---

## Continue.dev / OpenCode

Continue.dev has a Node.js CLI:

```bash
npm install -g @continuedev/cli
continue
```

---

## Tips

- **proot-distro** gives you a full Debian/Ubuntu environment with glibc. Anything that runs on Linux server runs here.
- **Performance:** Native Termux packages (Option A) are faster than proot. Use native when available.
- **Storage:** proot-distro Debian needs ~500MB. Make sure you have free space.
- **Backups:** Your home directory inside proot is at `/data/data/com.nvterm/files/usr/var/lib/proot-distro/installed-rootfs/debian/home/`.

---

## Why No Pre-installed AI?

- **Freedom:** Install only what you use. No bloat.
- **Security:** No API keys stored in the app. No AI services running in background.
- **Updates:** You control versions. Update via `npm`, `pip`, or `apt`.
- **Size:** APK stays lean (~31MB instead of 100MB+).
