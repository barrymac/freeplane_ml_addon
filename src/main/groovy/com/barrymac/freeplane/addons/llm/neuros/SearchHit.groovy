package com.barrymac.freeplane.addons.llm.neuros

/**
 * One row of a neurOS search/<question> result set.
 *
 * The row's name is whatever the substrate answered with — sometimes a
 * perfectly human file name, sometimes an internal store name whose segments
 * are hashes. The UI never shows machine text, so {@link #prettyName()}
 * collapses hashed segments away and keeps the human words: an evidence row
 * like <pre>5c6c…48ea….claimjudgements.sha256-….evidence</pre> reads as
 * "Claimjudgements evidence".
 */
class SearchHit {

    String displayName
    String uuid
    String subtype

    /** Segments that are pure machine text: long hex, uuid, hash labels. */
    private static final java.util.regex.Pattern MACHINE_SEGMENT =
            ~/^(?:[0-9a-fA-F]{16,}|sha\d{2,3}|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$/

    /**
     * A name a person reads: a name with no machine segments passes through
     * exactly as answered; a name whose segments are hashes collapses to its
     * human words; a name that is nothing but machine text falls back to the
     * subtype, and to a plain 'Knowledge entry' when even that is absent.
     */
    String prettyName() {
        String cleaned = (displayName ?: '').trim()
        if (!cleaned) {
            return subtype ? pretty(subtype) : 'Knowledge entry'
        }
        List<String> segments = cleaned.split(/[._-]+/).findAll { it }
        boolean wholeIsMachine = cleaned ==~ /^[0-9a-fA-F]{16,}$/ ||
                cleaned ==~ /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/
        if (wholeIsMachine) {
            return subtype ? pretty(subtype) : 'Knowledge entry'
        }
        boolean anyMachine = segments.any { MACHINE_SEGMENT.matcher(it).matches() }
        if (!anyMachine) {
            return displayName
        }
        List<String> human = segments.findAll { !MACHINE_SEGMENT.matcher(it).matches() }
        if (human.isEmpty()) {
            return subtype ? pretty(subtype) : 'Knowledge entry'
        }
        return pretty(human.join(' '))
    }

    private static String pretty(String words) {
        String spaced = words.trim().replaceAll(/\s+/, ' ')
        if (!spaced) return spaced
        return spaced.substring(0, 1).toUpperCase() + spaced.substring(1)
    }
}
