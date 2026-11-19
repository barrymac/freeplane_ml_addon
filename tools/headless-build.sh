#!/usr/bin/env bash
# Build the add-on without ever opening Freeplane's packaging dialog.
#
#   tools/headless-build.sh
#
# Compiles the jar with gradle (no GUI), then assembles the .addon.mm from
# sources with the pure-Python packager. Use this for headless iteration instead
# of ./gradlew packageAddon, which always opens the "Create release package" dialog.
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_17="${JAVA_17_HOME:-$HOME/.sdkman/candidates/java/17.0.13-tem}"

JAVA_HOME="$JAVA_17" "$REPO_ROOT/gradlew" jar --console=plain
python3 "$REPO_ROOT/tools/package-addon.py"
echo "headless build complete"
