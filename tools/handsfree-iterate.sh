#!/usr/bin/env bash
# Hands-free iteration: install new packages automatically and confirm Freeplane's
# "Create release package" dialog while you are away from the keyboard.
#
#   tools/handsfree-iterate.sh       # runs until interrupted
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# 1. Watch for new add-on packages and install+restart when one lands.
"$REPO_ROOT/tools/auto-install-watch.sh" &
WATCH_PID=$!

# 2. Confirm the packaging dialog whenever it appears. Freeplane's default answer
# is "Yes", so Return completes it. Only needed while you are away.
confirm_loop() {
  while true; do
    if command -v wmctrl >/dev/null && command -v xdotool >/dev/null; then
      WID="$(wmctrl -l | grep -i 'Create release package' | awk '{print $1}' | head -1)"
      if [ -n "$WID" ]; then
        xdotool windowactivate --sync "$WID" 2>/dev/null || true
        sleep 0.3
        xdotool key --clearmodifiers Return 2>/dev/null || true
        echo "$(date +%H:%M:%S) confirmed the release-package dialog"
      fi
    fi
    sleep 3
  done
}
confirm_loop &
CONFIRM_PID=$!

trap 'kill $WATCH_PID $CONFIRM_PID 2>/dev/null || true' EXIT
echo "hands-free iteration running (package watcher + dialog confirmer)"
wait
