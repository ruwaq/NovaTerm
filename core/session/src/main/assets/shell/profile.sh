# NovaTerm shell profile (POSIX compatible)
# Sourced on login shells. Keep this lightweight.

# ── Environment ────────────────────────────────────────
export EDITOR=vi
export PAGER=less
export LESS='-R -i -M'
export COLORTERM=truecolor
export TERM_PROGRAM=novaterm
export TERM_PROGRAM_VERSION="{{VERSION}}"

# ── /tmp ────────────────────────────────────────────────
# NovaTerm creates /tmp symlink from Java (has app permissions).
# Ensure TMPDIR always points to a writable directory.
export TMPDIR="$PREFIX/tmp"
mkdir -p "$TMPDIR" 2>/dev/null

# ── History (crash-safe: saved on every command) ────────
export HISTSIZE=10000
export HISTFILESIZE=10000
export HISTCONTROL=erasedups:ignoredups:ignorespace
export HISTFILE="$HOME/.local/state/bash_history"

# ── Prompt (initial fallback) ───────────────────────────
# Interactive prompt is configured in .bashrc with OSC 133 markers.
# This fallback covers non-bash POSIX shells.
export PS1='\033[38;5;208m>\033[0m '
shopt -s histappend 2>/dev/null

# AI tools: install from Settings → AI Tools, or run:
#   apt install nodejs && npm i -g @anthropic-ai/claude-code

# Mark command start after profile loads (OSC 133)
printf '\033]133;B\007'
