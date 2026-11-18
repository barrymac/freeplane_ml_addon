# Demo: lenses, enmeshment, disagreements

Everything below runs against a real neurOS scope. Four shortcuts, five minutes.

## 1. Launch it so Jev is reachable

The judgement actions read `JEV_API_KEY` from the environment Freeplane starts in.
Starting from a desktop menu will not have it.

```bash
cd ~/src/ai/freeplane_ml_addon
tools/enmesh-demo.sh            # prepares /tmp/enmesh-demo and launches Freeplane
```

One command prepares the folder, writes the map and starts Freeplane with a Java
version it accepts (24+ is rejected upstream, which is why the wrapper pins 23).
Use `--no-launch` to prepare without opening a window.

The `SCOPE_KNOWLEDGE` symlink beside the map is what tells the add-on which scope
is in play — no configuration needed.

## 2. Enmesh a node that has no links at all

Select **Adjudicate contradictions** → `Ctrl+Alt+E`.

A dialog lists the lenses (dependency, blocking, structure, tracking, reference,
code, governance, contradiction). Tick **dependency** and **reference**, press OK.
Jev reads both entries and answers one question per candidate: *which
relationship, if any, holds?*

What you should see: a few connectors appear **dashed and grey**, labelled
`proposed: depends on` or `proposed: references`, running to new nodes under
**Related knowledge**. The node gains the tag `enmesh proposals`.

What it means: those are candidates, not facts. Delete any you disagree with, or
keep them and rewrite the card's links by hand.

If nothing appears, the dialog says so — and that is a real answer: the text does
not support a relationship to anything in this scope.

## 3. Draw what is actually declared

Select **Bounded relationship expansion** → `Ctrl+Alt+R`.

This draws only what the card declares, filtered by your lens. Expect three
connectors ("depends on" once, "blocks" twice) and one link reported as outside
the scope — that one points at the diagram.

Then select **Graph traversal over 9P** (no relationships at all) and press
`Ctrl+Alt+R`. The dialog should say it hit an **enmeshment gap**, and the node is
tagged `mesh gap: no links`.

## 4. Look for disagreements

Select **Bounded relationship expansion** → `Ctrl+Alt+C`.

These cards were written to agree, so the honest result is *reconciles* or
*unresolved*, with the reason and confidence in the summary. A `contradicts` here
would mean the model is over-reading.

For a known-positive, use the shipped probe pair in `demo/probe/` — two cards that
genuinely disagree (filed 30 July vs nothing before 28 August). Ingest that folder
into the scope, run the same shortcut on the first card, and expect a dashed
crimson connector labelled `contradicts`. Measured: **0.96 confidence**. If it
comes back reconciled, the judgement plane is wrong, not the fixture.

## 5. Check a preview before publishing

Select any card → `Ctrl+Alt+P` (preview) → `Ctrl+Alt+Shift+C` (check).

Jev looks at the cached preview and answers five narrow questions — UUID,
hostname, person, private path, secret. The highest probability decides, and the
node is tagged `publish check: safe|review|not safe`.

## The same things without Freeplane

```bash
tools/mesh-gaps.sh   /mnt/neuros-9p-mss1/freeplane            # islands and dangling links
tools/mesh-plan.sh   <card.md> /mnt/neuros-9p-mss1/freeplane  # what a draw would produce
tools/jev-check.sh                                            # is Jev reachable, what does it answer
tools/jev-vision-check.sh shot.png                            # the publish check on one image
```

## Resetting

`Ctrl+Z` removes the last drawing action. To start over, delete the
**Related knowledge** branch — nothing in the knowledge source was modified by any
of these actions.