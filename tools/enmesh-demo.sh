#!/usr/bin/env bash
# One command for the enmeshment demo: prepare the folder, write the map, launch
# Freeplane with a Java it accepts.
#
#   tools/enmesh-demo.sh [scope-directory] [output-directory]
#   tools/enmesh-demo.sh --no-launch          # prepare everything, don't open a window
#
# Freeplane rejects Java 24+, so this pins FREEPLANE_JAVA_HOME unless you set it.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LAUNCH=1
if [ "${1:-}" = "--no-launch" ]; then LAUNCH=0; shift; fi
SCOPE="${1:-/mnt/neuros-9p-mss1/freeplane}"
OUT_DIR="${2:-/tmp/enmesh-demo}"
FREEPLANE_HOME="${FREEPLANE_HOME:-$HOME/bin/freeplane-1.12.11}"
JAVA_23="${FREEPLANE_JAVA_HOME:-$HOME/.sdkman/candidates/java/23.0.2-oracle}"

[ -d "$SCOPE" ] || { echo "scope not found: $SCOPE" >&2; exit 1; }
[ -x "$JAVA_23/bin/java" ] || { echo "Java 23 not found at $JAVA_23" >&2; exit 1; }

mkdir -p "$OUT_DIR"
ln -sfn "$SCOPE" "$OUT_DIR/SCOPE_KNOWLEDGE"
"$REPO_ROOT/tools/demo-map.sh" "$SCOPE" "$OUT_DIR/EnmeshDemo.mm"

if [ -z "${JEV_API_KEY:-}${TYPESAFE_API_KEY:-}" ]; then
  echo "warning: neither JEV_API_KEY nor TYPESAFE_API_KEY is set —" >&2
  echo "         enmeshment and disagreements will refuse to run." >&2
fi

if [ "$LAUNCH" -eq 0 ]; then
  echo "prepared: $OUT_DIR/EnmeshDemo.mm"
  exit 0
fi

echo "launching Freeplane (Java 23) with $OUT_DIR/EnmeshDemo.mm"
FREEPLANE_JAVA_HOME="$JAVA_23" nohup "$FREEPLANE_HOME/freeplane.sh" \
  "$OUT_DIR/EnmeshDemo.mm" >/tmp/enmesh-demo/freeplane.log 2>&1 &
echo "log: /tmp/enmesh-demo/freeplane.log"
