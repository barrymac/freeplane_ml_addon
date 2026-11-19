#!/usr/bin/env bash
# Write a demo map whose nodes are linked to the entries in a scope.
#
#   tools/demo-map.sh <scope-directory> [output.mm]
#
# Place the output inside a directory that holds a *_KNOWLEDGE symlink to the
# scope, so the add-on discovers the scope from the map's location.
set -euo pipefail
SCOPE="${1:?usage: demo-map.sh <scope-directory> [output.mm]}"
OUT="${2:-$(pwd)/EnmeshDemo.mm}"

python3 - "$SCOPE" "$OUT" <<'PY'
import os, sys
scope, out = sys.argv[1], sys.argv[2]
wanted = [
    ('Bounded relationship expansion', 'bounded-relationship-expansion'),
    ('Automatic visible hit connectors', 'automatic-visible-hit-connectors'),
    ('Lazy door navigation', 'lazy-door-navigation'),
    ('Bounded mesh density inspector', 'bounded-mesh-density-inspector'),
    ('Publish screenshot tour', 'publish-neuros-screenshot-tour'),
    ('Adjudicate contradictions  <-- enmesh candidate', 'adjudicate-contradictions'),
    ('Neutral provider configuration  <-- enmesh candidate', 'neutral-provider-configuration'),
    ('Graph traversal over 9P  <-- enmesh candidate', 'graph-traversal'),
]
listing = os.listdir(scope)
nodes, index = [], 0
for label, key in wanted:
    match = next((f for f in listing if key in f), None)
    if match is None:
        continue
    index += 1
    nodes.append(f'    <node TEXT="{label}" POSITION="right" ID="ID_{index:03d}" '
                 f'LINK="{os.path.join(scope, match)}"/>')
if not nodes:
    index = 0
    for f in sorted(listing):
        if f.endswith('.card.md'):
            index += 1
            nodes.append(f'    <node TEXT="{f.replace(".card.md", "")}" POSITION="right" '
                         f'ID="ID_{index:03d}" LINK="{os.path.join(scope, f)}"/>')

xml = ('<map version="freeplane 1.12.1">\n'
       '  <node TEXT="Enmesh demo — pick a node, press Ctrl+Alt+E" FOLDED="false" ID="ID_ROOT">\n'
       + '\n'.join(nodes) + '\n  </node>\n</map>\n')
open(out, 'w').write(xml)
print(f"{out}: {len(nodes)} linked entries")
PY
