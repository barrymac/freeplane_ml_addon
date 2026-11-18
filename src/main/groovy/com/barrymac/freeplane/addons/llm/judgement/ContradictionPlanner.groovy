package com.barrymac.freeplane.addons.llm.judgement

import com.barrymac.freeplane.addons.llm.neuros.MeshVocabulary

/**
 * Turns a pair of entries into a typed judgement about disagreement, and the
 * answer into a resolution.
 *
 * Detection is not the deliverable: a pair either contradicts, reconciles under
 * a condition, or stays unresolved. Nothing is drawn on a guess, and an escape
 * is reported rather than spent.
 */
class ContradictionPlanner {

    static final int MAX_PAIRS = 8
    static final int MAX_CHARS = 900
    static final double DECISIVE = 0.75

    static final String CONTRADICTS = 'contradicts'
    static final String RECONCILES = 'reconciles-under-condition'
    static final String UNRESOLVED = 'unresolved'

    /** One entry compared with the selected entry. */
    static class Pair {
        String leftId
        String leftTitle
        String leftText
        String rightId
        String rightTitle
        String rightText
    }

    /** What the pair turned out to be, with the evidence that decided it. */
    static class Resolution {
        String kind
        String label
        String family
        double confidence
        String ground
        Pair pair

        boolean isDrawable() {
            return kind == CONTRADICTS || kind == RECONCILES
        }
    }

    /**
     * Bounded pairs: the selected entry against each candidate, never the full
     * cross-product, and the entry text is cut to what a judgement needs.
     */
    static List<Pair> pairs(Map seed, List<Map> candidates, int max = MAX_PAIRS) {
        if (seed == null || !candidates) return []
        return candidates.take(Math.max(max, 1)).collect { candidate ->
            new Pair(
                    leftId: seed.id, leftTitle: seed.title, leftText: trim(seed.text),
                    rightId: candidate.id, rightTitle: candidate.title, rightText: trim(candidate.text))
        }
    }

    /** State and questions for one call, keyed by pair index. */
    static Map battery(List<Pair> pairs) {
        Map state = [:]
        Map questions = [:]
        pairs.eachWithIndex { Pair pair, int index ->
            state["c${index}".toString()] = [
                    first : [title: pair.leftTitle, text: pair.leftText],
                    second: [title: pair.rightTitle, text: pair.rightText]
            ]
            String first = "`c${index}.first`".toString()
            String second = "`c${index}.second`".toString()
            questions["q${index}".toString()] = [
                    type        : 'choice',
                    instructions: "Do ${first} and ${second} make claims that cannot both hold? " +
                            'Judge only compatibility, not which is correct.',
                    criteria    : [
                            contradicts      : 'Both cannot be true at the same time',
                            not_contradiction: 'Both can be true',
                            cannot_tell      : 'Not enough information to judge'
                    ]
            ]
            questions["n${index}".toString()] = [
                    type        : 'noul',
                    instructions: "Can ${first} and ${second} both hold under a condition stated in " +
                            'one of them, such as a different date, scope or party?'
            ]
        }
        return [state: state, questions: questions]
    }

    /** Map answers back onto their pairs; anything unclear stays unresolved. */
    static List<Resolution> resolve(List<Pair> pairs, List<Judgement.Answer> answers) {
        Map<String, Judgement.Answer> byQuestion = [:]
        answers.each { byQuestion[it.question] = it }
        List<Resolution> resolutions = []
        pairs.eachWithIndex { Pair pair, int index ->
            def verdict = byQuestion["q${index}".toString()]
            def conditional = byQuestion["n${index}".toString()]
            resolutions << toResolution(pair, verdict, conditional)
        }
        return resolutions
    }

    private static Resolution toResolution(Pair pair, Judgement.Answer verdict,
                                           Judgement.Answer conditional) {
        if (verdict == null) {
            return new Resolution(kind: UNRESOLVED, ground: 'no verdict returned', pair: pair)
        }
        double confidence = verdict.confidence ?: 0d
        if (verdict.escape || !verdict.isDecisive(DECISIVE)) {
            return new Resolution(kind: UNRESOLVED, pair: pair,
                    confidence: confidence,
                    ground: verdict.escape ?
                            "judged unclear (${verdict.choice})" :
                            "confidence ${fmt(confidence)} below ${DECISIVE}")
        }
        if (verdict.choice == 'contradicts') {
            return new Resolution(kind: CONTRADICTS, label: 'contradicts',
                    family: MeshVocabulary.CONTRADICTION, confidence: confidence, pair: pair,
                    ground: "cannot both hold (confidence ${fmt(confidence)})")
        }
        double reconcile = conditional?.probability ?: 0d
        if (reconcile >= DECISIVE) {
            return new Resolution(kind: RECONCILES, label: 'reconciles under a condition',
                    family: MeshVocabulary.TRACKING, confidence: reconcile, pair: pair,
                    ground: "both hold under a condition (confidence ${fmt(reconcile)})")
        }
        return new Resolution(kind: UNRESOLVED, pair: pair, confidence: confidence,
                ground: 'compatible, but not shown to reconcile')
    }

    static String trim(String text) {
        String clean = (text ?: '').replaceAll(/\s+/, ' ').trim()
        return clean.length() <= MAX_CHARS ? clean : clean.substring(0, MAX_CHARS) + '…'
    }

    private static String fmt(double value) {
        return String.format('%.2f', value)
    }
}