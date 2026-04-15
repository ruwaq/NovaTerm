# NovaTerm bash configuration
# Sourced for interactive non-login shells.

# Source profile if not already loaded
[ -z "$HISTFILE" ] && . "$HOME/.profile"

# ── Shell options (bash only) ────────────────────────────
if [ -n "$BASH_VERSION" ]; then
  shopt -s checkwinsize   # Update LINES/COLUMNS after each command
  shopt -s histappend     # Append to history, don't overwrite
  shopt -s cmdhist        # Save multi-line commands as one entry
  shopt -s globstar 2>/dev/null  # ** recursive glob (bash 4+)
  shopt -s nocaseglob     # Case-insensitive globbing
fi

# ── Colors ──────────────────────────────────────────────
alias ls='ls --color=auto'
alias grep='grep --color=auto'
alias diff='diff --color=auto'

# ── Navigation ──────────────────────────────────────────
alias ll='ls -lah'
alias la='ls -A'
alias ..='cd ..'
alias ...='cd ../..'
alias p='cd ~/projects'
alias s='cd ~/storage/shared'

# ── Safety ──────────────────────────────────────────────
alias rm='rm -i'
alias mv='mv -i'
alias cp='cp -i'

# ── Shortcuts ───────────────────────────────────────────
alias h='history'
alias c='clear'
alias q='exit'

# ── Network ─────────────────────────────────────────────
alias ports='netstat -tlnp 2>/dev/null || ss -tlnp'
alias myip='curl -s ifconfig.me'
alias timer='echo "Timer started. Stop with Ctrl+D." && date && time cat && date'

# ── Functions ───────────────────────────────────────────
mkcd() { mkdir -p "$1" && cd "$1"; }

# Universal archive extraction
extract() {
  if [ -f "$1" ]; then
    case "$1" in
      *.tar.bz2) tar xjf "$1" ;;
      *.tar.gz)  tar xzf "$1" ;;
      *.tar.xz)  tar xJf "$1" ;;
      *.tar.zst) tar --zstd -xf "$1" ;;
      *.bz2)     bunzip2 "$1" ;;
      *.gz)      gunzip "$1" ;;
      *.tar)     tar xf "$1" ;;
      *.tbz2)    tar xjf "$1" ;;
      *.tgz)     tar xzf "$1" ;;
      *.zip)     unzip "$1" ;;
      *.7z)      7z x "$1" ;;
      *.xz)      unxz "$1" ;;
      *.zst)     unzstd "$1" ;;
      *)         printf 'Cannot extract: %s\n' "$1" >&2; return 1 ;;
    esac
  else
    printf 'File not found: %s\n' "$1" >&2; return 1
  fi
}

# One-liner weather
weather() { curl -s "wttr.in/${1:-}?format=3"; }

# Cheatsheets from cheat.sh
cheat() { curl -s "cheat.sh/$1"; }

# ── Shell integration (OSC 133 semantic markers) ─────────
# Enables command tracking, completion notifications, and AI block parsing.
_novaterm_prompt() {
  local exit=$?
  # Mark end of previous command output + exit code
  printf '\033]133;D;%d\007' "$exit"
  # Mark prompt start
  printf '\033]133;A\007'
  # Original prompt logic: orange > on success, red > on error
  if [ $exit -eq 0 ]; then
    PS1='\033[38;5;208m>\033[0m '
  else
    PS1='\033[38;5;167m>\033[0m '
  fi
}

# Wire up prompt and crash-safe history
PROMPT_COMMAND="_novaterm_prompt;history -a"

# PS0 triggers on command execution (bash 4.4+) — acts as preexec.
# Marks the start of command output (OSC 133;C).
if [ -n "$BASH_VERSION" ]; then
  if [ "${BASH_VERSINFO[0]}" -ge 5 ] || { [ "${BASH_VERSINFO[0]}" -eq 4 ] && [ "${BASH_VERSINFO[1]}" -ge 4 ]; }; then
    PS0='\033]133;C\007'
  fi
fi

# ── Command not found ────────────────────────────────────
command_not_found_handler() {
  local pkg
  printf '\e[38;5;167m%s\e[0m: command not found\n' "$1" >&2
  pkg=$(apt-cache search --names-only "^$1$" 2>/dev/null | head -1 | cut -d' ' -f1)
  if [ -n "$pkg" ]; then
    printf '  \e[38;5;208m→\e[0m Install with: \e[1mapt install %s\e[0m\n' "$pkg" >&2
  fi
  return 127
}
