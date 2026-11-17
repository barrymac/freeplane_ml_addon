package com.barrymac.freeplane.addons.llm.neuros

/**
 * Produces the reader-facing Markdown used by previews and rollover notes.
 * Work-graph markers and metadata stay in the card file; they are not content.
 */
class MarkdownPreviewSanitizer {

    static String clean(String source) {
        if (!source?.trim()) return ''
        List<String> lines = source.readLines()
        boolean card = lines.find { it.trim() }?.trim()?.startsWith('[[card:') ?: false
        List<String> result = []
        boolean inDiagramReferences = false

        lines.each { raw ->
            String trimmed = raw.trim()
            if (trimmed.startsWith('## Diagram References')) {
                inDiagramReferences = true
                return
            }
            if (inDiagramReferences) {
                if (trimmed.startsWith('## ')) {
                    inDiagramReferences = false
                } else {
                    return
                }
            }
            if (trimmed ==~ /^\[\[[^\]]+\]\]$/) return
            if (card && trimmed ==~ /^(list|priority|type|source|node):.*/) return
            if (trimmed == 'DAG:') return
            String cleanedLine = raw.replaceAll(/\[\[[^\]]+\]\]/, '')
                    .replaceAll(/[ \t]+$/, '')
            if (!cleanedLine.trim() && result && !result.last().trim()) return
            result << cleanedLine
        }

        while (result && !result.first().trim()) result.remove(0)
        while (result && !result.last().trim()) result.remove(result.size() - 1)
        return result.join('\n').trim()
    }
}