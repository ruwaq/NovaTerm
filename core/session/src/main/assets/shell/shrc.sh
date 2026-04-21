# NovaTerm shell init (sourced via ENV variable)
# NOTE: Only use shell builtins here — external commands
# may not work before LD_PRELOAD/nvterm-exec is active.
[ -f "$HOME/.profile" ] && . "$HOME/.profile"
[ -f "$HOME/.bashrc" ] && . "$HOME/.bashrc"
# Display MOTD using read loop (no cat — W^X safe)
if [ -f "$HOME/.motd" ]; then
  while IFS= read -r line; do echo "$line"; done < "$HOME/.motd"
fi

# Mark initial prompt (OSC 133)
printf '\033]133;A\007'
