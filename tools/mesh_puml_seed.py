#!/usr/bin/env python3
"""mesh-sight PUML seed parser (mechanical, no model).

Parses the semantic-rich PlantUML diagrams of the legalease-legal watched
workspace and mints typed entity files (one .md per entity) that ingest
through the hohfeldian cartridge (sys:hohfeldian-v1):

  * component/participant/actor/robust node labels -> entity candidates,
    typed ONLY with declared subtypes the cartridge can route end-to-end
    (compound file extension + mimePipeline with a satisfiable
    requiredMarker);
  * CNJ case numbers (8004600-02 style, short or full form) -> matter;
  * arrows (-->, ->>, -[#style]->) -> edge candidates wired with DECLARED
    markers (default [[references:]]);
  * PUML comment markers in diagram headers seed canonical party names
    (held-by/against targets);
  * labels/priorities/statuses become mechanical context in the body.

Laws: only declared subtypes and declared markers are emitted; every
marker target is the exact basename of another minted file, so
link-health has zero ghosts by construction; confidence stays mechanical
(mention frequency in the diagrams, recorded in the body); nodes the
cartridge cannot type are reported as vocabulary gaps, never force-typed.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path

SUBTYPE_EXT = {
    "matter": "matter.md",
    "judge": "judge.md",
    "party": "party.md",
    "witness": "witness.md",
    "judgment": "judgment.md",
    "opinion": "opinion.md",
    "exhibit": "exhibit.md",
    "contract": "contract.md",
    "jural_position": "claim.md",
}
ACTIVATION = {
    "matter": "matter",
    "judge": "judge",
    "party": "party",
    "witness": "witness",
    "judgment": "judgment",
    "opinion": "opinion",
    "exhibit": "exhibit",
    "contract": "contract",
    "jural_position": "claim",
}
EDGE_MARKER = {
    ("judge", "matter"): "presides-over",
    ("opinion", "judgment"): "accompanies",
    ("party", "contract"): "party-to",
}
DEFAULT_MARKER = "references"

JUDGE_KW = re.compile(
    r"\b(?i:judge|juíza|juiza|juiz|desembargadora|desembargador)\b"
    r"(?:\s+plantonista\b\s*:?)?\s*"
)
NAME = re.compile(
    r"([A-ZÀ-Ý][a-zà-ÿ'’\-]+(?:(?:[ \t]+(?:da|de|do|dos|das|e)[ \t]+|[ \t]+)"
    r"[A-ZÀ-Ý][a-zà-ÿ'’\-]+){0,6})"
)
PARTY_KEYWORDS = re.compile(
    r"\b(plaintiff|defendant|flagranteado|réu|reu|requerente|requerido|"
    r"promovente|executado|executada|exequente|vítima|vitima)\b",
    re.IGNORECASE,
)
WITNESS_KW = re.compile(r"\b(?i:witness|testemunha)\b")
JUDGMENT_KW = re.compile(r"\b(?i:sentenca|sentença|judgment|verdict|acórdão|acordao)\b")
OPINION_KW = re.compile(r"\b(?i:parecer|opinion)\b")
CONTRACT_KW = re.compile(r"\b(?i:contract|contrato|distrato|convenção|convencao)\b")
EXHIBIT_KW = re.compile(r"\b(?i:exhibit|exibição|exibicao|anexo)\b")
FILING_KW = re.compile(
    r"\b(?i:petição|peticao|petition|agravo|queixa|embargos|recurso|apelação|"
    r"apelacao|habeas|liminar|despacho|decisão|decisao|intimação|intimacao|"
    r"mandado|certidão|certidao|ata notarial|brief|manifestação|manifestacao|"
    r"contrarrazões|oitiva|audiência|audiencia|hearing)\b"
)
NORM_KW = re.compile(
    r"\b(?i:art\.?\s*\d|artigo|\bcpc\b|\bcpp\b|\bcp\b|\blmp\b|lei\b|"
    r"resolução|resolucao|statute|precedent)\b"
)
LAWYER_KW = re.compile(
    r"\b(?i:lawyer|advogado|advogada|counsel|attorney|defensor|defensora|"
    r"procurador|procuradora|solicitor)\b"
)

CNJ_FULL = re.compile(r"\b(\d{7}-\d{2})\.(\d{4}\.\d\.\d{2}\.\d{4})\b")
CNJ_SHORT = re.compile(r"\b(\d{7}-\d{2})\b")

NODE_DECL = re.compile(
    r"^\s*(?i:participant|actor|entity|boundary|control|database|collections|"
    r"queue|component|card|rectangle|folder|frame|node|cloud|storage|object|"
    r"usecase|robust|concise)\b\s+(.*)$"
)
AS_ALIAS = re.compile(r"^(.*?)\s+as\s+([A-Za-z0-9_]+)\s*(.*)$", re.IGNORECASE)
QUOTED = re.compile(r'^"(.*)"\s*(.*)$', re.DOTALL)
STEREO = re.compile(r"<<([^>]*)>>")
ARROW = re.compile(
    r"^\s*([A-Za-z0-9_]+)\s+"
    r"([\-\.]+(?:\[[^\]]*\])?(?i:left|right|up|down)?[\-\.]*>?>?\*?o?0?)\s+"
    r"([A-Za-z0-9_]+)\s*(?::\s*(.*))?$"
)
COMMENT_MARKER = re.compile(r"^\s*'\s*(\[\[([a-z-]+):([^\]]+)\]\])")
ANY_MARKER = re.compile(r"\[\[([a-z-]+):([^\]]+)\]\]")
CREOLE = re.compile(r"<[^>]+>|\*\*|//|__|''")
MAX_TEXTS = 8
MAX_TEXT_CHARS = 800
MAX_ARROWS = 16


def clean_label(label: str) -> str:
    label = label.replace("\\n", "\n")
    label = CREOLE.sub("", label)
    lines = [ln.strip(" -") for ln in label.split("\n")]
    return "\n".join(ln for ln in lines if ln).strip()


def sanitize(text: str) -> str:
    return ANY_MARKER.sub(lambda m: f"[[ {m.group(1)}:{m.group(2)}]]", text)


def decode(path: Path) -> str:
    raw = path.read_bytes()
    for enc in ("utf-8", "latin-1"):
        try:
            return raw.decode(enc)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="replace")


@dataclass
class Node:
    alias: str
    label: str
    stereotype: str
    diagram: str


@dataclass
class Entity:
    key: str
    subtype: str
    mentions: Counter = field(default_factory=Counter)
    texts: list = field(default_factory=list)
    arrows: list = field(default_factory=list)
    full_ids: set = field(default_factory=set)
    markers: set = field(default_factory=set)
    diagrams: set = field(default_factory=set)


def slug(key: str, subtype: str) -> str:
    import unicodedata

    stripped = unicodedata.normalize("NFD", key)
    stripped = "".join(ch for ch in stripped if not unicodedata.combining(ch))
    safe = re.sub(r"[^a-z0-9._' -]+", "-", stripped.lower()).strip(" -")
    safe = re.sub(r"[ ']+", "-", safe)
    return f"{safe}.{SUBTYPE_EXT[subtype]}"


class Census:
    def __init__(self) -> None:
        self.diagrams: dict[str, dict[str, Node]] = {}
        self.raw_arrows: list = []
        self.entities: dict[str, Entity] = {}
        self.node_entity: dict[tuple[str, str], str] = {}
        self.canonical_parties: dict[str, set] = {}
        self.gaps: Counter = Counter()
        self.gap_examples: defaultdict = defaultdict(list)
        self.stats: Counter = Counter()
        self.edges: list = []

    def gap(self, kind: str, example: str) -> None:
        self.gaps[kind] += 1
        if len(self.gap_examples[kind]) < 10:
            self.gap_examples[kind].append(example[:70])

    def entity(self, key: str, subtype: str) -> Entity:
        existing = self.entities.get(key)
        if existing:
            if existing.subtype != subtype:
                self.stats["type_conflicts"] += 1
            return existing
        e = Entity(key=key, subtype=subtype)
        self.entities[key] = e
        return e

    def note(self, e: Entity, node: Node) -> None:
        e.mentions[node.diagram] += 1
        e.diagrams.add(node.diagram)
        t = node.label.strip()
        if t and t not in e.texts and len(e.texts) < MAX_TEXTS:
            e.texts.append(t[:MAX_TEXT_CHARS])
        fm = CNJ_FULL.search(node.label)
        if fm:
            e.full_ids.add(fm.group(0))


def parse_file(path: Path, c: Census) -> None:
    text = decode(path)
    diag = path.name
    nodes: dict[str, Node] = {}
    for raw in text.splitlines():
        cm = COMMENT_MARKER.match(raw)
        if cm:
            token, target = cm.group(2), cm.group(3).strip()
            if token in ("held-by", "against"):
                if re.search(r"(?i)your name|tbd|placeholder|template", target):
                    continue
                toks = {t for t in re.findall(r"[a-zà-ÿ]{3,}", target.lower())}
                c.canonical_parties.setdefault(target, toks)
            continue
        nm = NODE_DECL.match(raw)
        if nm:
            node = build_node(nm.group(1), diag)
            if node:
                nodes[node.alias] = node
                c.stats["nodes"] += 1
            continue
        am = ARROW.match(raw)
        if am:
            style = am.group(2)
            if "hidden" in style:
                continue
            c.raw_arrows.append((diag, am.group(1), am.group(3), (am.group(4) or "").strip()))
            c.stats["arrows"] += 1
    c.diagrams[diag] = nodes


def build_node(rest: str, diag: str) -> Node | None:
    alias, label, stereo = "", "", ""
    am = AS_ALIAS.match(rest)
    if am:
        label, alias, tail = am.group(1), am.group(2), am.group(3)
    else:
        qm = QUOTED.match(rest)
        if qm:
            label, tail = qm.group(1), qm.group(2)
            am2 = re.search(r"\bas\s+([A-Za-z0-9_]+)", tail)
            if am2:
                alias = am2.group(1)
        else:
            parts = rest.split()
            alias = parts[0] if parts else ""
            label = " ".join(parts[1:])
    if am or True:
        sm = STEREO.search(rest)
        if sm:
            stereo = sm.group(1)
    label = STEREO.sub("", label)
    label = clean_label(label).strip('"').strip()
    if not label:
        return None
    if not alias:
        alias = re.sub(r"\W+", "_", label[:24]) or "node"
    return Node(alias, label, stereo, diag)


JUNK_KEY = re.compile(r"^[\d/\.\-–—: ,·]+$")


def plausible_key(key: str, subtype: str) -> bool:
    if not key or len(key) < 4:
        return False
    if key.startswith(("[", '"', "(", "·", "<")):
        return False
    if JUNK_KEY.match(key):
        return False
    if sum(ch.isalpha() for ch in key) < 4:
        return False
    if subtype in ("judge", "party", "witness"):
        return len(key.split()) >= 2
    return True


def classify_node(node: Node, c: Census) -> str | None:
    label = node.label
    if CNJ_FULL.search(label) or CNJ_SHORT.search(label):
        return classify_matter(node, c)
    title = label.split("\n")[0].strip()
    zone = "\n".join(label.split("\n")[:3])
    party = match_party(label, c)
    if party:
        pe = c.entity(party, "party")
        c.note(pe, node)
        c.canonical_parties.setdefault(
            party, set(re.findall(r"[a-zà-ÿ]{3,}", party.lower())))
        return party
    jm = JUDGE_KW.search(zone)
    if jm:
        nm = NAME.search(zone, jm.end())
        key = nm.group(1).strip() if nm else ""
        if plausible_key(key, "judge"):
            c.note(c.entity(key, "judge"), node)
            return key
        c.gap("judge (name not extractable)", title)
        return None
    if LAWYER_KW.search(zone):
        c.gap("lawyer (ghost-promoted, no file route)", title)
        return None
    if WITNESS_KW.search(title):
        nm = NAME.search(" ".join(
            ln for ln in label.split("\n")[:2] if ln[:1].isupper()))
        key = nm.group(1).strip() if nm else title
        if plausible_key(key, "witness"):
            c.note(c.entity(key, "witness"), node)
            return key
        c.gap("witness (name not extractable)", title)
        return None
    if CONTRACT_KW.search(title):
        key = first_name_line(label)
        if plausible_key(key, "contract"):
            c.note(c.entity(key, "contract"), node)
            return key
    if JUDGMENT_KW.search(title):
        key = first_name_line(label)
        if plausible_key(key, "judgment"):
            c.note(c.entity(key, "judgment"), node)
            return key
    if OPINION_KW.search(title):
        key = first_name_line(label)
        if plausible_key(key, "opinion"):
            c.note(c.entity(key, "opinion"), node)
            return key
    if EXHIBIT_KW.search(title):
        key = first_name_line(label)
        if plausible_key(key, "exhibit"):
            c.note(c.entity(key, "exhibit"), node)
            return key
    if FILING_KW.search(label):
        c.gap("filing (no marker-wired pipeline)", title)
        return None
    if NORM_KW.search(label):
        c.gap("norm/statute (ghost-promoted, no file route)", title)
        return None
    c.gap("untyped node (no declared subtype fits)", title)
    return None


def first_name_line(label: str) -> str:
    for ln in label.split("\n"):
        if ln.strip():
            return ln.strip()[:80]
    return label[:80]


def classify_matter(node: Node, c: Census) -> str:
    label = node.label
    fm = CNJ_FULL.search(label)
    sm = CNJ_SHORT.search(label)
    if fm:
        key = fm.group(1)
    elif sm:
        key = sm.group(1)
    else:
        return ""
    e = c.entity(key, "matter")
    c.note(e, node)
    pkey = match_party(label, c) or match_party_fulltext(label, c)
    if pkey:
        pe = c.entity(pkey, "party")
        c.note(pe, node)
        c.canonical_parties.setdefault(
            pkey, set(re.findall(r"[a-zà-ÿ]{3,}", pkey.lower())))
    jm = JUDGE_KW.search(label)
    if jm:
        nm = NAME.search(label, jm.end())
        jkey = nm.group(1).strip() if nm else ""
        if plausible_key(jkey, "judge"):
            je = c.entity(jkey, "judge")
            je.markers.add(f"presides-over:{slug(key, 'matter')}")
            c.note(je, node)
    return key


def match_party_fulltext(label: str, c: Census) -> str | None:
    toks = set(re.findall(r"[a-zà-ÿ]{3,}", label.lower()))
    for full, ftoks in c.canonical_parties.items():
        if len(toks & ftoks) >= 2:
            return full
    return None


def normalize_key(key: str) -> str:
    import unicodedata

    stripped = unicodedata.normalize("NFD", key)
    stripped = "".join(ch for ch in stripped if not unicodedata.combining(ch))
    return re.sub(r"\s+", " ", stripped.strip().lower())


def merge_accent_duplicates(c: Census) -> None:
    groups: dict[str, list[str]] = {}
    for key in list(c.entities):
        if c.entities[key].subtype in ("matter",):
            continue
        groups.setdefault(normalize_key(key), []).append(key)
    for _, members in groups.items():
        if len(members) < 2:
            continue
        members.sort(key=lambda k: (-sum(c.entities[k].mentions.values()), k))
        keep = members[0]
        keeper = c.entities[keep]
        for other in members[1:]:
            loser = c.entities.pop(other)
            keeper.mentions.update(loser.mentions)
            keeper.diagrams.update(loser.diagrams)
            keeper.full_ids.update(loser.full_ids)
            keeper.markers.update(loser.markers)
            for t in loser.texts:
                if t not in keeper.texts and len(keeper.texts) < MAX_TEXTS:
                    keeper.texts.append(t)
            for a in loser.arrows:
                if len(keeper.arrows) < MAX_ARROWS:
                    keeper.arrows.append(a)
            c.stats["accent_merges"] += 1
        for dk, key in list(c.node_entity.items()):
            if key in members:
                c.node_entity[dk] = keep


def match_party(label: str, c: Census) -> str | None:
    lines = [ln.strip() for ln in label.split("\n") if ln.strip()]
    head_lines: list = []
    for ln in lines[:3]:
        if re.match(r"^[A-ZÀ-Ý(\"]", ln) and not PARTY_KEYWORDS.search(ln):
            head_lines.append(ln)
        else:
            break
    joined = " ".join(head_lines)
    if PARTY_KEYWORDS.search(label):
        nm = NAME.search(joined)
        if nm and plausible_key(nm.group(1).strip(), "party"):
            return nm.group(1).strip()
    toks: set = set()
    for ln in label.split("\n")[:2]:
        toks.update(t for t in re.findall(r"[a-zà-ÿ]{3,}", ln.lower()))
    for full, ftoks in c.canonical_parties.items():
        shared = toks & ftoks
        if (len(toks) == 1 and shared) or len(shared) >= 2:
            return full
    return None


def wire_arrows(c: Census) -> None:
    for diag, src, dst, lbl in c.raw_arrows:
        nodes = c.diagrams.get(diag, {})
        skey = c.node_entity.get((diag, src))
        dkey = c.node_entity.get((diag, dst))
        if skey and dkey:
            if skey == dkey:
                c.stats["self_arrows_skipped"] += 1
                continue
            se, de = c.entities[skey], c.entities[dkey]
            marker = EDGE_MARKER.get((se.subtype, de.subtype), DEFAULT_MARKER)
            se.markers.add(f"{marker}:{slug(dkey, de.subtype)}")
            c.stats["edges_wired"] += 1
            c.edges.append((skey, dkey, marker, lbl))
            if len(se.arrows) < MAX_ARROWS:
                se.arrows.append(f"→ {dkey}" + (f" ({lbl})" if lbl else ""))
            if len(de.arrows) < MAX_ARROWS:
                de.arrows.append(f"← from {skey}" + (f" ({lbl})" if lbl else ""))
        elif skey or dkey:
            key = skey if skey is not None else dkey
            if key is None:
                continue
            e = c.entities[key]
            other = nodes.get(dst if skey else src)
            other_label = other.label.split("\n")[0] if other else (dst if skey else src)
            direction = "→" if skey else "← from"
            target = other_label if skey else other_label
            if len(e.arrows) < MAX_ARROWS:
                e.arrows.append(
                    f"{direction} {sanitize(target)[:60]}" + (f" ({lbl})" if lbl else "")
                )
            c.stats["half_typed_arrows"] += 1
        else:
            c.stats["untyped_arrows"] += 1


def activation_target(key: str, c: Census) -> str:
    e = c.entities[key]
    if e.subtype == "matter":
        for other in sorted(c.entities):
            oe = c.entities[other]
            if oe.subtype == "judge" and any(
                m.endswith(slug(key, "matter")) for m in oe.markers
            ):
                return slug(other, "judge")
    if e.subtype == "judge":
        for m in sorted(e.markers):
            if m.startswith("presides-over:"):
                return m.split(":", 1)[1]
    for m in sorted(e.markers):
        if ":" in m and not m.startswith(("occurred", "valid")):
            return m.split(":", 1)[1]
    all_keys = sorted(c.entities)
    for other in all_keys:
        if other != key:
            return slug(other, c.entities[other].subtype)
    return slug(key, e.subtype)


def render(key: str, c: Census, produced: str) -> str:
    e = c.entities[key]
    target = activation_target(key, c)
    if target == slug(key, e.subtype):
        c.stats["self_activation"] += 1
    lines = [
        "<!-- neuros:artifact -->",
        f"<!-- neuros:type={e.subtype} -->",
        "<!-- neuros:classification=CLIENT-PRIVILEGED -->",
        "<!-- neuros:visibility=legal-team-only -->",
        "<!-- neuros:scope=legalease-legal -->",
        f"<!-- neuros:produced_date={produced} -->",
        "<!-- neuros:source=mesh-sight/puml-seed (mechanical, no model) -->",
        "",
        f"# {key} ({e.subtype})",
        "",
        f"[[{ACTIVATION[e.subtype]}:{target}]]",
        "",
    ]
    for m in sorted(e.markers):
        token, tgt = m.split(":", 1)
        lines.append(f"[[{token}:{tgt}]]")
    if e.markers:
        lines.append("")
    lines.append("## Mechanical context")
    lines.append("")
    freq = sum(e.mentions.values())
    lines.append(f"- **Frequency (mechanical confidence)**: {freq} node mention(s) "
                 f"across {len(e.diagrams)} diagram(s): "
                 + ", ".join(f"{d}×{n}" for d, n in sorted(e.mentions.items())))
    if e.full_ids:
        lines.append("- **Full identifiers seen in diagrams**: " + ", ".join(sorted(e.full_ids)))
    lines.append("- **Diagrams**: " + ", ".join(sorted(e.diagrams)))
    if e.texts:
        lines.append("- **Node text (verbatim from diagram, sanitized)**:")
        for t in e.texts[:MAX_TEXTS]:
            lines.append(f"  - {sanitize(t).replace(chr(10), ' | ')[:MAX_TEXT_CHARS]}")
    if e.arrows:
        lines.append("- **Arrow context (diagram relationships)**:")
        for a in e.arrows[:MAX_ARROWS]:
            lines.append(f"  - {sanitize(a)}")
    lines.append("- **Seed provenance**: minted by the mesh-sight PUML seed parser "
                 "from the semantic-rich diagrams of this workspace. No model "
                 "adjudication; regenerate by editing the source .puml and re-running.")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--workspace", required=True)
    ap.add_argument("--census", action="store_true")
    ap.add_argument("--mint")
    ap.add_argument("--produced", default="2026-11-19")
    args = ap.parse_args()

    ws = Path(args.workspace)
    pumls = sorted(ws.rglob("*.puml"))
    if not pumls:
        print(f"no .puml files under {ws}", file=sys.stderr)
        return 1

    c = Census()
    for p in pumls:
        parse_file(p, c)

    for diag, nodes in c.diagrams.items():
        for alias, node in nodes.items():
            key = classify_node(node, c)
            if key:
                c.node_entity[(diag, alias)] = key
    merge_accent_duplicates(c)
    wire_arrows(c)

    by_type = Counter(e.subtype for e in c.entities.values())
    print(f"diagrams parsed: {len(pumls)}")
    print(f"nodes: {c.stats['nodes']}  arrows: {c.stats['arrows']}")
    print(f"entities: {len(c.entities)}  " +
          "  ".join(f"{t}={n}" for t, n in sorted(by_type.items())))
    print(f"edges wired: {c.stats['edges_wired']}  "
          f"half-typed arrows (context only): {c.stats['half_typed_arrows']}  "
          f"untyped arrows: {c.stats['untyped_arrows']}")
    print(f"type conflicts (first classification wins): {c.stats['type_conflicts']}")
    print("gaps (cartridge-extension candidates):")
    for kind, n in c.gaps.most_common():
        print(f"  {n:4d}  {kind}")
        for ex in c.gap_examples[kind]:
            print(f"         e.g. {ex}")
    print("canonical parties from diagram comment markers:",
          ", ".join(sorted(c.canonical_parties)) or "(none)")

    if args.census:
        for key in sorted(c.entities):
            e = c.entities[key]
            print(f"  [{e.subtype:14s}] {key}  ({sum(e.mentions.values())} mentions, "
                  f"{len(e.markers)} markers)")
        for s, d, m, l in c.edges:
            print(f"  edge: {s} -{m}-> {d}" + (f" ({l[:40]})" if l else ""))
        return 0

    if args.mint:
        outdir = Path(args.mint)
        written = []
        for key in sorted(c.entities):
            e = c.entities[key]
            fname = slug(key, e.subtype)
            body = render(key, c, args.produced)
            (outdir / fname).write_text(body, encoding="utf-8")
            written.append(fname)
        report = {
            "files": len(written),
            "entities": len(c.entities),
            "by_type": dict(by_type),
            "edges": c.stats["edges_wired"],
            "gaps": dict(c.gaps),
        }
        print(json.dumps(report, indent=2))
        for f in written:
            print(f"  wrote {f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
