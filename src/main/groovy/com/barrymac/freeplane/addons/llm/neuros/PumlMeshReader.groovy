package com.barrymac.freeplane.addons.llm.neuros

/**
 * Reads relationships out of a PlantUML diagram.
 *
 * A diagram already states its mesh twice: each declaration carries the card it
 * documents, and each arrow carries the relationship in its label. Reading both
 * lets one diagram contribute real edges between the cards it depicts, without
 * treating the diagram itself as the source of every edge.
 */
class PumlMeshReader {

    static final int MAX_EDGES = 200

    private static final java.util.regex.Pattern DECLARATION =
            ~/^\s*(?:rectangle|component|class|interface|node|usecase|artifact|database|entity)\s+"(.*?)"\s+as\s+([A-Za-z0-9_]+)/

    private static final java.util.regex.Pattern IDENTITY =
            ~/\[\[card:([^\]\s]+)\]\]/

    /** Standalone markers, in PlantUML ('), C-style (//) or bare lines. */
    private static final java.util.regex.Pattern STANDALONE =
            ~/^\s*(?:'|\/\/)?\s*\[\[([a-z][a-z0-9-]*):([^\]\s]+)\]\]/

    private static final java.util.regex.Pattern RELATIONSHIP =
            ~/^\s*([A-Za-z0-9_]+)\s*(?:-->|\.\.>|--\|>|-\.->|==>)\s*([A-Za-z0-9_]+)\s*(?::\s*(.*?))?\s*$/

    static List<MeshEdge> read(String text, int maxEdges = MAX_EDGES) {
        if (!text?.trim()) return []
        Map<String, String> aliasToCard = [:]
        List<MeshEdge> edges = []
        Set<String> seen = new HashSet<>()

        text.readLines().each { line ->
            def declaration = DECLARATION.matcher(line)
            if (declaration.find()) {
                def identity = IDENTITY.matcher(declaration.group(1))
                if (identity.find()) {
                    aliasToCard[declaration.group(2)] = identity.group(1)
                }
                return
            }
            // A standalone marker states the diagram's own relationship to its
            // target: [[references:src/foo.ts]], [[depicts:plane]]. The edge
            // source is the diagram itself (null = the seed); the target is a
            // file path, normalised to its basename so identity matching sees
            // the same name the scope indexes.
            def standalone = STANDALONE.matcher(line)
            if (standalone.find()) {
                String kind = standalone.group(1)
                String target = standalone.group(2)
                // 'card' is identity (declarations carry the card they
                // document), handled by the alias mapping — not a relation.
                if (kind != 'card' && target && edges.size() < maxEdges) {
                    String normalised = target.contains('/') ?
                            target.substring(target.lastIndexOf('/') + 1) : target
                    String key = "|${normalised}|${kind}"
                    if (seen.add(key)) {
                        edges << new MeshEdge(
                                source: null,
                                target: normalised,
                                marker: kind,
                                label: MeshVocabulary.label(kind),
                                family: MeshVocabulary.family(kind),
                                declared: MeshVocabulary.isDeclared(kind))
                    }
                }
                return
            }
            def relationship = RELATIONSHIP.matcher(line)
            if (!relationship.find()) return
            String from = aliasToCard[relationship.group(1)]
            String to = aliasToCard[relationship.group(2)]
            if (!from || !to || edges.size() >= maxEdges) return
            String stated = (relationship.group(3) ?: '').trim()
            String marker = stated ? stated.toLowerCase().replace(' ', '-') : 'relates-to'
            String key = "${from}|${to}|${marker}"
            if (!seen.add(key)) return
            edges << new MeshEdge(
                    source: from,
                    target: to,
                    marker: marker,
                    label: stated ?: MeshVocabulary.label('relates-to'),
                    family: MeshVocabulary.family(marker),
                    declared: MeshVocabulary.isDeclared(marker))
        }
        return edges
    }
}