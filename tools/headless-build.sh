#!/usr/bin/env bash
# Complete, headless build → package → (optional) install+restart cycle.
#
#   tools/headless-build.sh              # build + package only
#   tools/headless-build.sh --install    # also install and restart Freeplane
#
# Always refreshes build/addon/lib (so the packaged jar is never stale), and
# uses the repo-root package as the template (prepareAddonSource's Sync deletes
# build/addon's own copy).
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_17="${JAVA_17_HOME:-$HOME/.sdkman/candidates/java/17.0.13-tem}"

# 1. Compile and refresh the add-on's lib directory.
JAVA_HOME="$JAVA_17" "$REPO_ROOT/gradlew" jar prepareAddonSource --console=plain

# 2. Package from sources, using the repo-root template (always present).
TEMPLATE="$REPO_ROOT/LLM-AddOn-v0.9.40.addon.mm"
[ -f "$TEMPLATE" ] || TEMPLATE="$(ls -t "$REPO_ROOT"/build/addon/LLM-AddOn-*.addon.mm | head -1)"
python3 "$REPO_ROOT/tools/package-addon.py" "$TEMPLATE" \
  "$REPO_ROOT/build/addon/LLM-AddOn-v0.9.40.addon.mm"

# 3. Keep a root copy for the next iteration's template and for release uploads.
cp "$REPO_ROOT/build/addon/LLM-AddOn-v0.9.40.addon.mm" "$REPO_ROOT/"

if [ "${1:-}" = "--install" ]; then
  "$REPO_ROOT/tools/install-and-restart.sh" "$REPO_ROOT/build/addon/LLM-AddOn-v0.9.40.addon.mm"
else
  echo "headless build complete (use --install to install and restart)"
fi
