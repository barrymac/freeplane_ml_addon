package com.barrymac.freeplane.addons.llm.neuros

import java.time.LocalDate
import java.util.UUID

/**
 * Builds a neurOS work-graph card (.card.md, sys:software-v1 / sys:work-graph-v1
 * convention) from plain map data — no Freeplane types, fully unit-testable.
 *
 * Emitted shape (matches live fleet cards):
 *
 *   [[card:<uuid>]]
 *   # <title>
 *
 *   list: Backlog
 *   type: idea
 *   source: freeplane-map:<mapName>
 *   node: <nodeId>
 *
 *   ---
 *
 *   <body + children outline>
 *
 * 'list: Backlog' is the initial state of the card state machine, so the
 * file is legal on strict (enforce) mounts from the first write.
 */
class CardBuilder {

    static final int SLUG_MAX_LENGTH = 60

    /** UUID minted for a new card. Package-visible seam for tests. */
    static String newUuid() { UUID.randomUUID().toString() }

    /** File name: <uuid>-<slug>.card.md */
    static String fileName(String uuid, String title) {
        "${uuid}-${slugify(title)}.card.md"
    }

    /** Lowercase-hyphen slug of a title, capped at SLUG_MAX_LENGTH. */
    static String slugify(String title) {
        String base = (title ?: 'card')
                .split('\n')[0]
                .replaceAll(/[^A-Za-z0-9]+/, '-')
                .replaceAll(/^-+|-+$/, '')
                .toLowerCase()
        if (base.length() > SLUG_MAX_LENGTH) base = base[0..<SLUG_MAX_LENGTH]
        if (!base) base = 'card'
        // keep the tail of an over-long slug trimmed to a clean boundary
        return base.replaceAll(/-+$/, '') ?: 'card'
    }

    /**
     * Full card content for one mind-map branch.
     *
     * @param title    selected node's text — the FIRST line becomes the card
     *                 title, any remaining lines open the body
     * @param children nested outline: list of maps [text: String, children: [...]]
     * @param mapName  provenance: source map name
     * @param nodeId   provenance: Freeplane node id
     * @param date     provenance date (ISO), defaults to today
     * @return card file content
     */
    static String build(String title, List children,
                        String mapName, String nodeId, String date = null) {
        String uuid = newUuid()
        List titleLines = (title ?: '').split('\n')*.trim().findAll { it }
        String titleLine = titleLines ? titleLines[0] : 'Untitled card'
        String details = titleLines.size() > 1 ? titleLines[1..-1].join('\n') : ''
        String isoDate = date ?: LocalDate.now().toString()

        StringBuilder body = new StringBuilder()
        body.append("[[card:${uuid}]]\n")
        body.append("# ${titleLine}\n\n")
        body.append("list: Backlog\n")
        body.append("type: idea\n")
        body.append("source: freeplane-map:${mapName ?: 'unnamed'}\n")
        body.append("node: ${nodeId ?: 'unknown'}\n")
        body.append("\n---\n\n")
        if (details?.trim()) {
            body.append(details.trim()).append('\n')
        }
        appendChildren(body, children, 0)
        body.append('\n---\n')
        body.append("Pushed from Freeplane map '${mapName ?: 'unnamed'}' node ${nodeId ?: '?'} ")
        body.append("on ${isoDate} via the LLM AddOn neurOS bridge.\n")
        return body.toString()
    }

    private static void appendChildren(StringBuilder out, List children, int depth) {
        (children ?: []).each { child ->
            String indent = '  ' * depth
            String firstLine = ((child?.text ?: '') ?: '(empty)').split('\n')[0].trim()
            out.append(indent).append('- ').append(firstLine).append('\n')
            String extra = (child?.text ?: '').split('\n').size() > 1 ?
                    (child.text.split('\n')[1..-1].join('\n').trim()) : ''
            if (extra) {
                extra.readLines().each { out.append(indent).append('  ').append(it).append('\n') }
            }
            appendChildren(out, child?.children, depth + 1)
        }
    }
}
