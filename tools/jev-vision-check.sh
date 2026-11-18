#!/usr/bin/env bash
# Ask Jev about an image, e.g. whether a screenshot carries private identifiers.
#
#   tools/jev-vision-check.sh <image> [model]
#
# Reads JEV_API_KEY (or TYPESAFE_API_KEY) from the environment.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${REPO_ROOT}/build/libs/llm.jar"
GROOVY="${GROOVY_BIN:-$HOME/.sdkman/candidates/groovy/current/bin/groovy}"
JAVA_17="${JAVA_17_HOME:-$HOME/.sdkman/candidates/java/17.0.13-tem}"
IMAGE="${1:?usage: jev-vision-check.sh <image> [model]}"
MODEL="${2:-}"

[ -f "$JAR" ] || { echo "missing $JAR (run ./gradlew jar)" >&2; exit 1; }
[ -f "$IMAGE" ] || { echo "missing image: $IMAGE" >&2; exit 1; }
FP_CORE="$(find "${FREEPLANE_HOME:-$HOME/bin/freeplane-1.12.11}" -name 'freeplaneviewer.jar' | head -1)"

JAVA_HOME="$JAVA_17" "$GROOVY" -cp "$JAR:$FP_CORE" -e '
def J = Class.forName("com.barrymac.freeplane.addons.llm.judgement.Judgement")
def Keys = Class.forName("com.barrymac.freeplane.addons.llm.Keys")
File image = new File(args[0])
String model = args.length > 1 && args[1] ? args[1] : null
def state = [shot: [caption: "A screenshot from a mind-map tool", image: "image"]]
def questions = [
  private_details: [type: "noul",
                    instructions: "Does `shot.image` show any UUID, hostname, username, email address, or private filesystem path?"],
  verdict: [type: "choice",
            instructions: "Is `shot.image` safe to publish as-is?",
            criteria: [safe: "Nothing private is visible",
                       needs_cropping: "Something private is visible but croppable",
                       not_safe: "Private material is clearly visible"]]
]
println "model : ${model ?: J.MODEL}"
println "image : ${image.name} (${image.length()} bytes)"
try {
  def answers = J.ask(state, questions, Keys.jev(), J.imageAsset(image), model)
  answers.each { println "answer: ${it}" }
} catch (Throwable t) {
  def cause = t; while (cause.cause != null) cause = cause.cause
  println "FAILED: ${cause.class.simpleName}: ${cause.message}"
  System.exit(1)
}
' "$IMAGE" "$MODEL"
