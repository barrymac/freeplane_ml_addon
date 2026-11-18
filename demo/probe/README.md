# Easter egg: a pair that disagrees on purpose

Two probe cards in this folder make claims that cannot both be true:

- one says the notice was filed on **30 July 2026**
- the other says **nothing** was filed before **28 August 2026**

They reference each other, so a neurOS scope wires them together, and
**Find neurOS Disagreements** (`Ctrl+Alt+C`) has something real to find.

## Try it

1. Put this folder where a neurOS scope can ingest it.
2. Link both cards from a map, or let the scope list them.
3. Select one card and press `Ctrl+Alt+C`.

Expected: a **dashed crimson** connector labelled `contradicts`, with a
confidence in the summary. If it comes back *reconciles* or *unresolved* instead,
the judgement is being cautious — run it again, or raise the pair's specificity.

## Why it ships

Every other card in this project agrees with its neighbours, so the *positive*
case of contradiction detection had nothing to run against. A test fixture you
can see on a map is more convincing than a unit test you cannot.

## The part that is not a joke

If a judgement model says these two agree, treat it as a defect in the judgement
plane, not as an opinion. The two statements have no reading in which both hold:
different dates, same filing, no condition in either text that separates them.

If you "fix" one of these cards to make them agree, the fixture stops testing
anything. Leave them wrong on purpose.
