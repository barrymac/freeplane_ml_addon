#!/usr/bin/env bash
# Check Jev end to end, without launching Freeplane.
#
#   tools/jev-check.sh
#
# Reads JEV_API_KEY (or TYPESAFE_API_KEY) from the environment, asks two real
# questions about a contradictory pair, and prints the typed answers.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${REPO_ROOT}/build/libs/llm.jar"
GROOVY="${GROOVY_BIN:-$HOME/.sdkman/candidates/groovy/current/bin/groovy}"
JAVA_17="${JAVA_17_HOME:-$HOME/.sdkman/candidates/java/17.0.13-tem}"

[ -f "$JAR" ] || { echo "missing $JAR (run ./gradlew jar)" >&2; exit 1; }
FP_CORE="$(find "${FREEPLANE_HOME:-$HOME/bin/freeplane-1.12.11}" -name 'freeplaneviewer.jar' | head -1)"

JAVA_HOME="$JAVA_17" "$GROOVY" -cp "$JAR:$FP_CORE" -e '
def J = Class.forName("com.barrymac.freeplane.addons.llm.judgement.Judgement")
def Keys = Class.forName("com.barrymac.freeplane.addons.llm.Keys")
String key = Keys.jev()
println "endpoint : ${J.ENDPOINT}"
println "model    : ${J.MODEL}"
println "key      : ${key ? "present (" + key.length() + " chars)" : "MISSING"}".toString()
if (!key) { System.exit(3) }
def state = [c0: [claim: "The notice was filed on 30 July."], c1: [claim: "No filing was made before 28 August."]]
def questions = [
  conflict: [type: "choice",
             instructions: "Do these two claims contradict each other?",
             criteria: [contradicts: "Both cannot be true",
                        not_contradiction: "Both can be true",
                        cannot_tell: "Not enough information"]],
  conditional: [type: "noul", instructions: "Can both hold if a date or party differs?"],
  specificity: [type: "score", instructions: "How specific are these claims?",
                criteria: ["Vague", "Some detail", "Specific dates and parties"]]
]
try {
  J.ask(state, questions, key).each { println "answer   : ${it}" }
} catch (Throwable t) {
  def cause = t; while (cause.cause != null) cause = cause.cause
  println "FAILED   : ${cause.class.simpleName}: ${cause.message}"
  System.exit(1)
}
'
