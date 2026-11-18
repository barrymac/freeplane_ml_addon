package com.barrymac.freeplane.addons.llm.neuros

import org.freeplane.core.util.LogUtils

/**
 * The semantic channel for enmeshment candidates.
 *
 * Lexical overlap finds entries that use the same words; this asks the substrate
 * what is *near* an idea regardless of wording. A graph traversal surface
 * (`by-dep`, `related`) would be better still — it answers "what is connected"
 * rather than "what reads alike" — but where the mount does not expose one, this
 * is the strongest retrieval available.
 *
 * The surface returns TSV: display name, identifier, type. Names are resolved to
 * files by the caller, because a name is what a reader sees.
 */
class SemanticCandidates {

    static final int MAX_RESULTS = 12

    /** Names the substrate considers near the question, best first. */
    static List<String> query(File scopeRoot, String question, int limit = MAX_RESULTS) {
        if (scopeRoot == null || !question?.trim()) return []
        File surface = new File(scopeRoot, 'by-query')
        if (!surface.isDirectory()) return []
        String safe = question.trim().replace('/', ' ').replace('\n', ' ').take(300)
        File answer = new File(surface, safe)
        try {
            if (!answer.exists()) return []
            return parse(answer.getText('UTF-8')).take(Math.max(limit, 1))
        } catch (Exception problem) {
            LogUtils.warn("SemanticCandidates: the search surface did not answer: ${problem.message}")
            return []
        }
    }

    /** TSV rows: name, identifier, type. Blank lines and headers are skipped. */
    static List<String> parse(String text) {
        if (!text?.trim()) return []
        List<String> names = []
        text.readLines().each { String line ->
            String trimmed = line.trim()
            if (!trimmed || trimmed.startsWith('#')) return
            String[] columns = trimmed.split('\t')
            if (columns.length < 2 || !columns[0].trim()) return
            String type = columns.length >= 3 ? columns[2].trim().toLowerCase() : ''
            if (type && !USEFUL_TYPES.contains(type)) return
            names << columns[0].trim()
        }
        return names.unique()
    }

    /**
     * A query that describes the entry's *work*, not its bookkeeping.
     *
     * Card metadata (`list:`, `priority:`, `source:`) is what makes a semantic
     * query useless: it appears in every card, so it matches everything. The ask
     * is the part that distinguishes one card from another, and the opening of it
     * is what retrieval should see.
     */
    static String questionFor(String title, String text) {
        String body = (text ?: '')
                .replaceAll(/(?m)^\s*(list|priority|type|source|node):.*$/, ' ')
                .replaceAll(/(?m)^\s*DAG:.*$/, ' ')
                .replaceAll(/\[\[[^\]]+\]\]/, ' ')
                .replaceAll(/[^A-Za-z0-9\s-]/, ' ')
                .replaceAll(/\s+/, ' ')
                .trim()
        // Short queries score; long ones return nothing (measured: a 240-character
        // body produced 0 hits where the title alone produced 10).
        Set<String> seen = [] as Set
        List<String> terms = []
        body.toLowerCase().split(/\s+/).each { String word ->
            if (terms.size() >= 6) return
            if (word.length() < 4) return
            if (STOP.contains(word)) return
            if (!seen.add(word)) return
            terms << word
        }
        return ([title ?: 'entry'] + terms).join(' ').take(120)
    }

    private static final Set<String> STOP = [
            'the', 'and', 'for', 'that', 'this', 'with', 'from', 'into', 'when', 'then', 'than',
            'them', 'they', 'there', 'these', 'those', 'each', 'must', 'should', 'would', 'could',
            'have', 'has', 'are', 'was', 'were', 'will', 'can', 'not', 'but', 'its', 'their',
            'which', 'what', 'where', 'while', 'only', 'also', 'more', 'most', 'some', 'such'
    ] as Set

    /** Only entry kinds worth a relationship: work cards, code, diagrams. */
    static final Set<String> USEFUL_TYPES = ['card', 'class', 'diagram', 'file', 'harness'] as Set
}