#!/usr/bin/env bash
# Install the packaged add-on and restart Freeplane, hands-free.
#
#   tools/install-and-restart.sh [package.addon.mm]
#
# Restarts only when the package is newer than what's installed, so it is safe to
# call from a watcher or a build hook. A running instance is stopped and relaunched
# with the map it had open — no dialogues, no confirmations.
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROFILE="${XDG_CONFIG_HOME:-$HOME/.config}/freeplane/1.12.x"
FREEPLANE_HOME="${FREEPLANE_HOME:-$HOME/bin/freeplane-1.12.11}"
JAVA_23="${FREEPLANE_JAVA_HOME:-$HOME/.sdkman/candidates/java/23.0.2-oracle}"

PACKAGE="${1:-$(ls -t "$REPO_ROOT"/build/addon/LLM-AddOn-*.addon.mm 2>/dev/null | head -1)}"
[ -n "$PACKAGE" ] && [ -f "$PACKAGE" ] || { echo "no package found" >&2; exit 1; }

VERSION="$(basename "$PACKAGE" | sed -E 's/.*(v[0-9.]+)\.addon\.mm/\1/')"
INSTALLED="$(grep -o 'version="v[^"]*"' "$PROFILE/addons/promptLlmAddOn.script.xml" 2>/dev/null | head -1 | sed -E 's/version="([^"]+)"/\1/')"

if [ "$VERSION" = "$INSTALLED" ]; then
  echo "already at $VERSION — nothing to do"
  exit 0
fi

PID="$(ps -eo pid,cmd | grep '[f]reeplanelauncher.jar' | awk '{print $1}' | head -1)"
MAP="$(ps -eo pid,cmd | grep '[f]reeplanelauncher.jar' | grep -oE '/[^ ]+\.mm' | head -1)"

python3 "$REPO_ROOT/tools/install_freeplane_addon.py" "$PACKAGE" --base "$PROFILE" >/dev/null
echo "installed $VERSION (was ${INSTALLED:-none})"

if [ -n "$PID" ]; then
  kill "$PID" 2>/dev/null || true
  for _ in $(seq 1 20); do kill -0 "$PID" 2>/dev/null || break; sleep 1; done
fi

FREEPLANE_JAVA_HOME="$JAVA_23" nohup "$FREEPLANE_HOME/freeplane.sh" ${MAP:+"$MAP"} >/tmp/freeplane-auto.log 2>&1 &
echo "restarted with ${MAP:-the last map}"
