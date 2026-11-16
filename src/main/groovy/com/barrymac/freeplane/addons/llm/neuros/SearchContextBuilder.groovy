package com.barrymac.freeplane.addons.llm.neuros

/**
 * Builds ONE context-rich semantic query from the selected node and a bounded
 * slice of its map surroundings.
 *
 * The add-on never issues one search per ancestor, child or note. Map structure
 * is folded into a single natural-language query so the substrate's retrieval
 * and graph ranking receive the operator's local context without turning one
 * user action into a combinatorial set of queries.
 */
class SearchContextBuilder {

    static final int MAX_QUESTION_CHARS = 2000

    /**
     * @param question  the operator's primary question / selected node text
     * @param ancestors parent titles, ordered root → immediate parent
     * @param children  immediate child titles (generated result branches must
     *                  already be excluded by the caller)
     * @param note      the selected node's note text
     * @param options   [ancestorLimit, childLimit, noteLimit, totalLimit]
     * @return the exact query string sent to the knowledge source
     */
    static String build(String question, List<String> ancestors = [],
                        List<String> children = [], String note = '',
                        Map options = [:]) {
        String cleanQuestion = normalize(question)
        if (!cleanQuestion) {
            throw new IllegalArgumentException('The search question is empty.')
        }
        if (cleanQuestion.length() > MAX_QUESTION_CHARS) {
            throw new IllegalArgumentException(
                    "The search question is too long (maximum ${MAX_QUESTION_CHARS} characters).")
        }

        int ancestorLimit = nonNegative(options.ancestorLimit,
                NeurosLimits.DEFAULT_ANCESTOR_LIMIT)
        int childLimit = nonNegative(options.childLimit,
                NeurosLimits.DEFAULT_CHILD_LIMIT)
        int noteLimit = nonNegative(options.noteLimit,
                NeurosLimits.DEFAULT_NOTE_LIMIT)
        int totalLimit = positive(options.totalLimit,
                NeurosLimits.DEFAULT_CONTEXT_LIMIT)

        List<String> contextLines = []
        Set<String> contextSeen = new LinkedHashSet<>()
        List<String> ancestorText = distinctFragments(ancestors, ancestorLimit,
                cleanQuestion, contextSeen)
        if (ancestorText) {
            contextLines << "Context: ${ancestorText.join(' > ')}"
        }
        List<String> childText = distinctFragments(children, childLimit,
                cleanQuestion, contextSeen)
        if (childText) {
            contextLines << "Related ideas: ${childText.join('; ')}"
        }
        String noteText = normalize(note)
        if (noteText && noteText != cleanQuestion) {
            if (noteLimit > 0) {
                noteText = capFragment(noteText, noteLimit)
                contextLines << "Notes: ${noteText}"
            }
        }

        String context = contextLines.join('\n')
        if (!context) return cleanQuestion

        int remaining = totalLimit - cleanQuestion.length()
        if (remaining <= 0) return cleanQuestion
        if (context.length() > remaining) {
            context = truncateWords(context, remaining)
        }
        return cleanQuestion + '\n\n' + context
    }

    /** First line only, normalized — map text can be long and multi-line. */
    static String titleFragment(String text) {
        String firstLine = (text ?: '').split('\n')[0]
        return normalize(firstLine)
    }

    private static List<String> distinctFragments(List<String> values,
                                                  int limit, String question,
                                                  Set<String> seen) {
        if (limit <= 0 || values == null) return []
        List<String> accepted = []
        for (value in values) {
            String fragment = value instanceof CharSequence ? value.toString() : (value ?: '')
            fragment = titleFragment(fragment)
            if (fragment && fragment != question && !seen.contains(fragment)) {
                seen << fragment
                accepted << fragment
            }
            if (accepted.size() >= limit) break
        }
        return accepted
    }

    private static String normalize(String text) {
        return (text ?: '').replaceAll(/\s+/, ' ').trim()
    }

    private static String capFragment(String text, int limit) {
        if (text.length() <= limit) return text
        return truncateWords(text, limit)
    }

    private static String truncateWords(String text, int limit) {
        if (limit <= 0) return ''
        if (text.length() <= limit) return text
        String cut = text.substring(0, limit)
        int lastSpace = cut.lastIndexOf(' ')
        if (lastSpace > 0) {
            cut = cut.substring(0, lastSpace)
        }
        return cut.replaceAll(/[ \t]+$/, '') + '…'
    }

    private static int nonNegative(Object value, int defaultValue) {
        int parsed = value == null ? defaultValue : value as int
        if (parsed < 0) {
            throw new IllegalArgumentException('Context limits cannot be negative.')
        }
        return parsed
    }

    private static int positive(Object value, int defaultValue) {
        int parsed = value == null ? defaultValue : value as int
        if (parsed <= 0) {
            throw new IllegalArgumentException('The total context limit must be positive.')
        }
        return parsed
    }
}
