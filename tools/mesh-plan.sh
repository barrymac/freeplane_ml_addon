#!/usr/bin/env bash
# Dry run of the relationship drawing, without Freeplane:
#
#   tools/mesh-plan.sh <entry.card.md|diagram.puml> <scope-directory>
#
# Shows exactly which relationships resolve, which do not, and what each one
# will read as on a connector. Use it before drawing into a map.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${REPO_ROOT}/build/libs/llm.jar"
GROOVY="${GROOVY_BIN:-$HOME/.sdkman/candidates/groovy/current/bin/groovy}"
JAVA_HOME_17="${JAVA_17_HOME:-$HOME/.sdkman/candidates/java/17.0.13-tem}"

[ -f "$JAR" ] || { echo "missing $JAR (run ./gradlew packageAddon)" >&2; exit 1; }

JAVA_HOME="$JAVA_HOME_17" "$GROOVY" -cp "$JAR" "${REPO_ROOT}/tools/mesh-plan.groovy" "$@"