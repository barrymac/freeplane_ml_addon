package com.barrymac.freeplane.addons.llm.neuros

/**
 * Picks the entries worth asking about.
 *
 * A judgement model asked "does this relate to that?" over an arbitrary set will
 * mostly answer "no", and be right. Retrieval belongs in code: rank the entries by
 * shared vocabulary with the selected one, take the strongest, and let the model
 * only confirm the relationship. This is the same split as every other judgement
 * in the add-on — code finds candidates, the model judges them.
 */
class EnmeshCandidates {

    static final int MAX_CANDIDATES = 8

    private static final Set<String> STOPWORDS = [
            'the', 'and', 'for', 'that', 'this', 'with', 'from', 'into', 'when', 'then', 'than',
            'them', 'they', 'there', 'these', 'those', 'each', 'must', 'should', 'would', 'could',
            'have', 'has', 'are', 'was', 'were', 'will', 'can', 'not', 'but', 'its', 'their',
            'which', 'what', 'where', 'while', 'only', 'also', 'more', 'most', 'some', 'such',
            'one', 'two', 'all', 'any', 'own', 'same', 'other', 'others', 'over', 'under',
            'about', 'after', 'before', 'between', 'through', 'without', 'within', 'does'
    ] as Set

    /** Lowercase word set, long enough to carry meaning. */
    static Set<String> vocabulary(String text) {
        if (!text?.trim()) return [] as Set
        return text.toLowerCase()
                .replaceAll(/[^a-z0-9\s]/, ' ')
                .split(/\s+/)
                .findAll { it.length() >= 4 && !STOPWORDS.contains(it) } as Set
    }

    /** Overlap relative to the smaller side, so a short entry is not ignored. */
    static double similarity(Set<String> left, Set<String> right) {
        if (!left || !right) return 0d
        int shared = left.count { right.contains(it) }
        return shared / (double) Math.min(left.size(), right.size())
    }

    /**
     * The strongest textual neighbours, best first. Entries below the floor are
     * left out entirely: asking about them only manufactures "no" answers.
     */
    static List<Map> rank(String seedText, List<Map> candidates, int limit = MAX_CANDIDATES,
                          double floor = 0.08) {
        Set<String> seed = vocabulary(seedText)
        return candidates
                .collect { Map candidate -> [candidate: candidate, score: similarity(seed, vocabulary(candidate.text))] }
                .findAll { it.score >= floor }
                .sort { -it.score }
                .take(Math.max(limit, 1))
                .collect { it.candidate }
    }
}