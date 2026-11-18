package com.barrymac.freeplane.addons.llm.neuros

/**
 * Reads typed relationships out of a knowledge document.
 *
 * The document is the source of truth: every `[[marker:target]]` pair it carries
 * is an edge, whether or not the active cartridge enumerates that marker. The
 * document's own identity marker is not an edge and is skipped.
 */
class MeshMarkers {

    static final int MAX_EDGES = 200

    private static final java.util.regex.Pattern MARKER =
            ~/\[\[([A-Za-z0-9_\-]+):([^\]\s]+)\]\]/

    static List<MeshEdge> read(String text, String selfId) {
        if (!text?.trim()) return []
        List<MeshEdge> edges = []
        Set<String> seen = new HashSet<>()
        def matcher = MARKER.matcher(text)
        while (matcher.find() && edges.size() < MAX_EDGES) {
            String marker = matcher.group(1).toLowerCase()
            String target = matcher.group(2).trim()
            if (!target) continue
            if (marker == 'card' && selfId && target.equalsIgnoreCase(selfId)) continue
            String key = "${marker}|${target.toLowerCase()}"
            if (!seen.add(key)) continue
            edges << new MeshEdge(
                    source: selfId,
                    target: target,
                    marker: marker,
                    label: MeshVocabulary.label(marker),
                    family: MeshVocabulary.family(marker),
                    declared: MeshVocabulary.isDeclared(marker))
        }
        return edges
    }
}