#!/data/data/com.nvterm/files/usr/bin/sh
# NovaTerm post-bootstrap setup — installs Claude Code globally.
# Runs automatically on first launch after bootstrap extraction.
# Idempotent: safe to run multiple times.

set -e

PREFIX="/data/data/com.nvterm/files/usr"
NPM="$PREFIX/bin/npm"
CLAUDE="$PREFIX/bin/claude"
MARKER="$PREFIX/var/lib/novaterm/.claude-installed"

# Skip if already installed
if [ -f "$MARKER" ]; then
    exit 0
fi

# Skip if npm not available
if [ ! -x "$NPM" ]; then
    echo "npm not found, skipping Claude Code install"
    exit 0
fi

echo "Installing Claude Code..."
NODE_OPTIONS="--max-old-space-size=4096" "$NPM" install -g @anthropic-ai/claude-code 2>&1 || {
    echo "Claude Code install failed (will retry on next launch)"
    exit 0
}

# Mark as installed
mkdir -p "$(dirname "$MARKER")"
touch "$MARKER"
echo "Claude Code installed successfully"
