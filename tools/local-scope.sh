#!/usr/bin/env bash
# Build a local knowledge scope for fast iteration when the network scope is slow.
#
#   tools/local-scope.sh [output-directory]
#
# The fixture carries the discovery markers a real surface has (README.cartridge,
# by-query/), the project's cards, its diagrams, and the class entries named in
# their evidence lines — so the whole relationship pipeline runs from local disk
# in milliseconds. Semantic search honestly returns nothing here: there is no
# substrate behind it.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${1:-/tmp/enmesh-local}"
SCOPE_NAME="${2:-LOCAL_KNOWLEDGE}"

mkdir -p "$OUT/scope/by-query" "$OUT/scope/by-tag/class" "$OUT/scope/diagrams"

# Discovery markers: the cartridge text is copied, not faked, so the contract the
# add-on reads is the real one.
if [ -f /mnt/neuros-9p-mss1/freeplane/README.cartridge ]; then
  cp /mnt/neuros-9p-mss1/freeplane/README.cartridge "$OUT/scope/"
else
  printf '# local fixture scope\n# stands in for a knowledge surface while the real one is busy\n' \
    > "$OUT/scope/README.cartridge"
fi

# Cards: the project's own, straight from the repository.
find "$REPO_ROOT/demo/probe" -name '*.card.md' -exec cp {} "$OUT/scope/" \; 2>/dev/null || true

# Classes: every entry the cards name in their evidence lines, so the
# named-in-text path has something real to resolve against.
for name in ConfigManager ApiCallerFactory ApiConfig ModelCompatibility Judgement \
            EnmeshProposal ContradictionPlanner PrivacyCheck Keys NeurosEnmeshAction \
            NeurosMeshAction MeshDrawing MeshVocabulary TextReferences; do
  found=$(find "$REPO_ROOT/src" -name "${name}.groovy" | head -1)
  [ -n "$found" ] && cp "$found" "$OUT/scope/by-tag/class/"
done

# Diagrams: the relationship wave and the probe pair's own diagram, if present.
find "$REPO_ROOT/docs/diagrams" -name '*.puml' -exec cp {} "$OUT/scope/diagrams/" \; 2>/dev/null || true

# The map links its nodes to files in the scope.
ln -sfn "$OUT/scope" "$OUT/${SCOPE_NAME}"
"$REPO_ROOT/tools/demo-map.sh" "$OUT/scope" "$OUT/EnmeshDemo.mm"

echo "local scope : $OUT/scope"
echo "classes     : $(ls "$OUT/scope/by-tag/class" | wc -l), cards: $(ls "$OUT/scope" | grep -c 'card.md')"
echo "map         : $OUT/EnmeshDemo.mm"
echo "open with   : $HOME/bin/freeplane-1.12.11/freeplane.sh $OUT/EnmeshDemo.mm"