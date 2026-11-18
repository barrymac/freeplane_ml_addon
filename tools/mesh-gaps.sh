#!/usr/bin/env bash
# Report enmeshment and substrate gaps in a knowledge scope.
#
#   tools/mesh-gaps.sh <scope-directory>
#
# "no links"      = the entry declares no relationships (enmeshment gap)
# "unresolved"    = it declares relationships, none of which resolve here (substrate gap)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${REPO_ROOT}/build/libs/llm.jar"
GROOVY="${GROOVY_BIN:-$HOME/.sdkman/candidates/groovy/current/bin/groovy}"
JAVA_17="${JAVA_17_HOME:-$HOME/.sdkman/candidates/java/17.0.13-tem}"
SCOPE="${1:?usage: mesh-gaps.sh <scope-directory>}"

[ -f "$JAR" ] || { echo "missing $JAR (run ./gradlew jar)" >&2; exit 1; }
FP_CORE="$(find "${FREEPLANE_HOME:-$HOME/bin/freeplane-1.12.11}" -name 'freeplaneviewer.jar' | head -1)"

JAVA_HOME="$JAVA_17" "$GROOVY" -cp "$JAR:$FP_CORE" -e '
def idxC = Class.forName("com.barrymac.freeplane.addons.llm.neuros.MeshIndex")
def gapsC = Class.forName("com.barrymac.freeplane.addons.llm.neuros.MeshGaps")
def index = idxC.getMethod("load", File, int).invoke(null, new File(args[0]), 400)
def report = gapsC.getMethod("report", idxC).invoke(null, index)
println "entries: ${index.entries.size()}"
println ""
println "ENMESHMENT GAPS (declares no relationships): ${report.noLinks.size()}"
report.noLinks.each { println "  - ${it}" }
println ""
println "PARTIAL LINKS (some targets dangle): ${report.partial.size()}"
report.partial.each { println "  - ${it}" }
println ""
println "SUBSTRATE GAPS (declares links, none resolve): ${report.unresolved.size()}"
report.unresolved.each { println "  - ${it}" }
' "$SCOPE"
