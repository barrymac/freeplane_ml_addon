# The ms-s1 lab (for agents without the mount)

This document is the map for anyone joining mesh-sight without the freeplane
software project's mount. It says what the lab is for, where the two scopes live,
and what state they are in right now.

## Why a lab

The ms-s1 lab protects production. Every risky change — extraction-gate flips,
parser runs, backfill drains, read/write separation — is proven here first, and
only promoted once it holds. The Freeplane plugin's mesh work happens here, not
against prod.

## The two scopes

| scope | mount (local FUSE) | mount (9p read plane) | contents |
|---|---|---|---|
| **freeplane software** (`sys:software-v1`) | `/mnt/neuros/software/…` | `/mnt/neuros-9p-mss1/freeplane` | work cards, diagrams, class entries for the add-on |
| **hohfeld legal** (`sys:hohfeldian-v1`) | `/mnt/neuros/hohfeldian/legalease-legal` | `/mnt/neuros-9p-mss1/legalease-legal` | filings, statute-texts, evidence, judges, matters |

Both are discovered **by map location**, never by configuration: a map inside a
neurOS surface (`README.cartridge` + `by-query/`), or a workspace with a
`*_KNOWLEDGE` symlink to one, is neurOS-enabled. No scope picker, no host names
in code.

## What state it is in (2026-11-19)

- **Marker→edge wiring was the big finding**: the wiring code was complete but
  `neuros.extraction.gate.enabled` defaulted to FALSE, silently. Fixed on the DR
  clean room; activation on prod is the chair's trigger, with a backfill drain.
- **A drain looks like an empty mesh and is not one.** During the drain,
  `by-dep`/`by-rdep` listings return EAGAIN ("transient not-ready"). That is a
  THIRD state — draining, try later — not "no edges" and not "edges present".
  The plugin shows `scope degraded / transient not-ready` rather than blank.
- **The read side already works for API-minted edges.** `by-tag/judge` returns 9
  judges and `by-dep` walks `PRESIDES_OVER`/`REFERENCES` — see the render below.

## How to look at it yourself

```bash
# the confirmed judges
ls /mnt/neuros/hohfeldian/legalease-legal/by-tag/judge

# one judge's outbound edges
U=$(grep '^uuid:' /mnt/neuros/hohfeldian/legalease-legal/ingest-status/<judge-file> | awk '{print $2}')
ls /mnt/neuros/hohfeldian/legalease-legal/by-dep/$U/

# the Freeplane render of the judge mesh (committed)
tools/render-judges.py /mnt/neuros/hohfeldian/legalease-legal /tmp/judge-render.mm
```

The gap instruments work on any scope, including this one:

```bash
tools/mesh-gaps.sh /mnt/neuros-9p-mss1/freeplane
tools/mesh-plan.sh <entry.card.md> /mnt/neuros-9p-mss1/freeplane
```

## The doctrine that applies here

- **Reads are cheap, writes are gated.** Every mutation goes through one
  transport (a watched workspace) with a read-back receipt; nothing is written
  directly to a mount that does not permit it.
- **No data is not no problem.** An absent series, an empty result, and a drain
  are different states and must never be rendered as one.
- **Domain-agnostic throughout.** The cartridge supplies the vocabulary; the
  plugin never hardcodes an ontology's types, surfaces or markers.

Pointer to the wider initiative: `docs/MESH-SIGHT.md` in this repository.