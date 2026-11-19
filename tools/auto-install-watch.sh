#!/usr/bin/env bash
# Watch the build directory and install+restart Freeplane whenever a new package
# appears. For hands-free iteration while you are away from the keyboard.
#
#   tools/auto-install-watch.sh            # runs until interrupted
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WATCH_DIR="$REPO_ROOT/build/addon"

command -v inotifywait >/dev/null || { echo "needs inotify-tools (inotifywait)" >&2; exit 1; }

echo "watching $WATCH_DIR for new add-on packages…"
while inotifywait -q -e close_write --include '.*\.addon\.mm$' "$WATCH_DIR"; do
  "$REPO_ROOT/tools/install-and-restart.sh" || echo "install failed; continuing to watch"
done
