# How the Freeplane plugin led to the mesh-sight insight

**And what it now does about it: visualization as the diagnostic, and tagging as the repair.**

---

## Part 1 — The causal chain

### 1. We set out to draw edges, and found there was nothing to draw

The plugin's first mesh feature was modest: read the typed links a card declares
(`[[depends-on:…]]`, `[[blocks:…]]`, `[[references:…]]`) and render them as
labelled Freeplane connectors. That worked on the cards we wrote ourselves. The
moment it ran against the real substrate, three things became visible that no
listing had ever made obvious:

- **24% of declared edges dangled.** Forty-two declared relationships, thirty-two
  resolvable, ten pointing at identities no mounted file carried.
- **The vocabulary had collapsed to three words.** `depends-on=16`, `blocks=14`,
  `documents=2`. The cartridge declares a rich edge vocabulary; the corpus used
  almost none of it.
- **The topology was hub-and-spoke, not mesh.** Density 0.076, and one card
  carrying degree 18 — nearly half the graph passing through a single node.

None of this is visible in a document listing. It became visible because we tried
to *render* the mesh rather than *count* it.

### 2. Empty expansions turned out to be the teacher

The most instructive failure mode was not an error. It was a node that looked
interesting and expanded to nothing. Every such expansion had two possible
causes, and telling them apart was itself the diagnosis:

- **Enmeshment gap** — the entry declares no relationships, even though the work
  it describes is full of them. The material is richer than its links.
- **Substrate gap** — the entry declares links, but they resolve nowhere: the
  identities or surfaces the mount should expose are absent.

The plugin now tags the node with which gap it hit. A dead end becomes a work
item. That is the origin of "the empty surface is invisible until something tries
to read it": you can scroll past six thousand untagged documents and feel
nothing; an empty judges node in a map you're navigating offends.

### 3. Asking a model to invent the mesh failed honestly

We then tried to have a judgement model (Jev, TypeSafe System One) propose
missing relationships. It ran end-to-end — thirteen layers, per-layer timings —
and came back with `none` at 0.36–0.88 confidence across eight candidates and
three different prompt phrasings. Not a failure of the model: a correct reading
of material that genuinely does not cross-reference.

The run taught us the architecture the insight rests on:

- **Retrieval is code's job.** The model must never choose what to compare.
  Semantic search, graph neighbours, lexical overlap and named-artefact
  resolution produce the candidate set; the model only adjudicates.
- **Judgement is a last-mile operation.** Stage one asks *whether* an edge is
  warranted (a probability, so code can rank); stage two asks *which* directional
  relationship holds, only on the shortlist.
- **Low confidence is a harness defect, not task difficulty.** Our stage-two
  naming scored 0.33 because the candidate text was truncated at 600 characters —
  the acceptance criteria that state direction were being cut off. The fix was
  state, not thresholds.

This is the NSJP composition the insight names: symbolic retrieval constrains,
judgement adjudicates, code and a person dispose. A model must never be the thing
that decides what exists.

### 4. The consumer organ was the missing piece

Every component already existed separately — the diagram doctrine, the
enmeshment skills, the graph-context machinery, the substrate surfaces, the
identity-claim machinery. What was missing was the thing that reads the mesh
visually and makes a human want to feed it. The plugin is the first organ that
*demands* metabolism: it renders the absence, measures it, and refuses to be
satisfied by a confident answer that has no referent.

The synthesis falls out directly:

> The substrate has a complete skeleton (ontology, edges, identity claims,
> serving surfaces) and a complete bloodstream (6,232 transduced documents + 23
> semantic-rich diagrams) — but no metabolism. Nothing converts documents into
> entities, diagrams into edges, or queries into feedback. The plugin is the
> first organ that demands metabolism, and mesh-sight is the team that builds it.

---

## Part 2 — What the plugin does about it

The plugin has two roles from here, and they are deliberately asymmetric:
**visualization is free; writing is gated.**

### Role A — Visualization as the diagnostic instrument

| surface | what it shows | what it measures |
|---|---|---|
| **Draw relationships** (`Ctrl+Alt+R`) | declared edges as labelled, family-coloured connectors | dangling rate, vocabulary usage, hub concentration |
| **Browse** (`Ctrl+Alt+I`) | the cartridge's own surfaces, types, work views | which declared surfaces have zero instances |
| **Gap diagnosis** | a node tagged `mesh gap: no links` / `mesh gap: unresolved` | enmeshment vs substrate absence, per entry |
| **Enmeshment mission** (`Ctrl+Alt+E`) | ranked candidates, worth probabilities, proposed edges dashed grey | whether the text supports any relationship at all |
| **Disagreements** (`Ctrl+Alt+C`) | dashed crimson contradictions vs solid reconciliations | where the corpus argues with itself |

Every one of these is a measurement the mesh makes about itself. `by-tag/judge`
being empty is a fact; an empty judges node in a navigable map is an instruction.

### Role B — Mesh densification through confidence-gated tagging

The same mission that proposes edges can *write* them — but writing has stricter
rules than drawing, because a wrong edge is a wrong fact, not a wrong picture.
Three channels, ordered by safety:

1. **Node tags (local).** Gap diagnoses and provisional proposals live as tags on
   the map. Zero risk to the source; the map is the working surface.
2. **Marker lines into a watched workspace (the real densification).** Accepted
   proposals become ordinary card text — `[[references:…]]`, `[[depends-on:…]]`,
   `[[tracks:…]]` and their backlinks — written into a local watched workspace.
   They ingest through the normal path and become graph content the next run
   reads. This is the loop that closes the 24%-dangling rate and the collapsed
   vocabulary: the references get written, the mesh gets denser, the next
   measurement is better.
3. **Direct mount writes (only where permitted).** Where a mount accepts writes,
   the same markers can land in place. Where it does not — a read-only 9P mount
   is a hard fact, not a prompt — the mission degrades to channel 2 and says
   which channel it used. Never silently skipped.

**The gate is the same everywhere:** an evidence-bearing repair needs a *named
counterpart* and *confidence ≥ 0.75* and a *stated direction*. Anything below
stays a dashed-grey `proposed:` connector; `cannot_tell` stays a gap tag. The
mission must never manufacture the graph it was built to measure.

And the human loop is the point: confirm / merge / reject candidate entities in
the map itself. **The mind map is the entity-resolution UI.** Confirmed entities
become tagged files; tagged files ingest; the mesh grows; the next visualization
is richer. Visualization creates the demand, tagging satisfies it, and the round
trip is the product.

---

## Part 3 — Domain-agnostic by construction

Nothing in the pipeline knows a domain:

- **The cartridge drives everything.** Curated surfaces, primitive types, state
  machines, edge vocabularies and MIME routing are read from the mounted
  contract. Hohfeld declares no curated surfaces, so the plugin says so instead
  of silently shortening the catalogue; it declares `matter`/`filing`/`contract`
  state machines, so those appear as work views. Same code, different ontology,
  different catalogue — no per-domain branches.
- **Family vocabulary, not legal vocabulary.** Dependency, blocking, structure,
  tracking, reference, code, governance, contradiction. `contradicts` reads
  crimson and dashed in any domain; `supersedes`/`overrules`/`distinguishes` are
  resolution kinds the legal cartridge already owns, consumed the same way.
- **Retrieval is substrate-shaped, not content-shaped.** Semantic search, graph
  neighbours, lexical overlap, named-artefact extraction — all of them work on
  whatever the mount exposes, whether that is five software cards or 6,232
  filings.
- **Gaps are measured, not assumed.** The gap report (`tools/mesh-gaps.sh`) runs
  against any scope and returns islands, partial links and fully unresolved
  entries, deduplicated — the same instrument for a software board and a legal
  corpus.

The acceptance test is deliberately human-scale and domain-free:

> `by-tag/<type>` returns a real entity with real edges, and the mind map renders
> it as a connected node rather than an empty gap.

Judge or class, filing or card — the shape of the win is identical.

---

## The one-paragraph version for the room

We built a plugin to draw relationships in Freeplane, and it immediately showed
us a substrate with a full skeleton and no metabolism: declared surfaces empty,
declared edges dangling, vocabulary collapsed, graph hub-and-spoke. Empty
expansions became diagnoses rather than dead ends; a judgement model asked to
invent the missing mesh correctly refused, which taught us that retrieval belongs
to code, judgement to the model, and disposal to a person. The plugin is now the
consumer organ: it visualizes the mesh to make absence offensive, and it writes
confidence-gated marker lines into a watched workspace to densify what it
measures — node tags first, watched-workspace cards second, mount writes only
where permitted. Everything is cartridge-driven and domain-agnostic. The mind map
is not a viewer; it is the entity-resolution UI, and the round trip — see the
gap, name the gap, write the gap — is how the mesh finally gets woven.
