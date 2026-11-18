#!/usr/bin/env bash
# Check a judgement transport end to end, so a replacement model can be tried
# without launching Freeplane.
#
#   tools/judgement-check.sh                 # Jev
#   tools/judgement-check.sh decision        # the decision endpoint
#   DECISION_MODEL=some/model tools/judgement-check.sh decision
#   JEV_MODEL=jev-latest JEV_API_URL=... tools/judgement-check.sh jev
#
# Keys are read from the environment only: JEV_API_KEY / TYPESAFE_API_KEY and
# OPENROUTER_API_KEY.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${REPO_ROOT}/build/libs/llm.jar"
GROOVY="${GROOVY_BIN:-$HOME/.sdkman/candidates/groovy/current/bin/groovy}"
JAVA_17="${JAVA_17_HOME:-$HOME/.sdkman/candidates/java/17.0.13-tem}"
TRANSPORT="${1:-jev}"

[ -f "$JAR" ] || { echo "missing $JAR (run ./gradlew jar)" >&2; exit 1; }

FP_CORE="$(find "${FREEPLANE_HOME:-$HOME/bin/freeplane-1.12.11}" -name 'freeplaneviewer.jar' | head -1)"

JAVA_HOME="$JAVA_17" "$GROOVY" -cp "$JAR:$FP_CORE" -e '
def J = Class.forName("com.barrymac.freeplane.addons.llm.judgement.Judgement")
def Keys = Class.forName("com.barrymac.freeplane.addons.llm.Keys")
def T = Class.forName("com.barrymac.freeplane.addons.llm.judgement.Judgement\$Transport")
String requested = args[0].toUpperCase()
def transport = T."${requested}"
String key = transport == T.JEV ? Keys.jev() : Keys.openRouter(null)
println "transport : ${transport}"
println "endpoint  : ${J.endpointFor(transport, System.getenv())}"
println "model     : ${J.modelFor(transport, System.getenv())}"
println "key       : ${key ? "present (" + key.length() + " chars)" : "MISSING"}"
if (!key) { System.exit(3) }
def state = [c0: [claim: "The notice was filed on 30 July."], c1: [claim: "No filing was made before 28 August."]]
def questions = [
  conflict: [type: "choice",
             instructions: "Do these two claims contradict each other?",
             criteria: [contradicts: "Both cannot be true",
                        not_contradiction: "Both can be true",
                        cannot_tell: "Not enough information"]],
  solid: [type: "noul", instructions: "Are both claims stated with a specific date?"]
]
try {
  J.ask(transport, state, questions, key, null).each { println "answer    : ${it}" }
} catch (Throwable t) {
  def cause = t; while (cause.cause != null) cause = cause.cause
  println "FAILED    : ${cause.class.simpleName}: ${cause.message}"
  System.exit(1)
}
' "$TRANSPORT"