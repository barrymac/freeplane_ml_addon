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