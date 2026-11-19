package com.barrymac.freeplane.addons.llm.neuros

/**
 * Turns an empty expansion into a diagnosis.
 *
 * A branch that looks interesting and expands to nothing is evidence. Either the
 * entry declares no relationships at all (an enmeshment gap: the material is
 * richer than its links), or it declares links that cannot be resolved (a
 * substrate gap: identities or surfaces), or it is wired and simply outside the
 * sample you asked for.
 */
class MeshGaps {

    static final String LINKED = 'linked'
    static final String NO_LINKS = 'no-links'
    static final String UNRESOLVED = 'unresolved'
    /** The edge exists, walks, and asserts something false. Not a gap: a defect. */
    static final String WRONG = 'wired-and-wrong'

    static final String TAG_NO_LINKS = 'mesh gap: no links'
    static final String TAG_UNRESOLVED = 'mesh gap: unresolved'

    static class Diagnosis {
        String kind
        int declared
        int resolved
        int unresolved
        String remedy

        /** The remedy this gap class names, so a user knows which lane fixes it. */
        String remedyText() {
            if (remedy) return remedy
            switch (kind) {
                case NO_LINKS: return 'Human writes the missing markers (enmeshment gap).'
                case UNRESOLVED: return 'Wiring arrives with the image upgrade (by-image).'
                case WRONG: return 'Explicit deletion required — activation or wiring cannot fix a wrong edge.'
                default: return 'Scoped backfill pending (by-gate).'
            }
        }

        String message() {
            switch (kind) {
                case NO_LINKS:
                    return 'This entry declares no relationships, so there is nothing to draw. ' +
                            'That is an enmeshment gap: the entry is probably richer than its links.'
                case UNRESOLVED:
                    return "This entry declares ${declared} relationship" +
                            "${declared == 1 ? '' : 's'}, but none resolve inside this scope. " +
                            'That is a substrate gap: the links point at identities or surfaces this ' +
                            'mount does not expose. ' + remedyText()
                case WRONG:
                    return 'This edge exists and walks, but asserts something false, unverified ' +
                            'or mis-merged. That is an evidentiary defect, not a pending task. ' +
                            remedyText()
                default:
                    return "This entry declares ${declared} relationship" +
                            "${declared == 1 ? '' : 's'}, ${resolved} of them inside this scope."
            }
        }

        String tag() {
            return kind == NO_LINKS ? TAG_NO_LINKS : kind == UNRESOLVED ? TAG_UNRESOLVED : null
        }
    }

    /** Classify one entry, honouring a degraded read before judging absence. */
    static Diagnosis diagnose(int declared, int resolved, String health) {
        if (health == ScopeHealth.DEGRADED) {
            return new Diagnosis(kind: LINKED, declared: declared, resolved: resolved,
                    unresolved: Math.max(declared - resolved, 0))
        }
        return diagnose(declared, resolved)
    }

    /** Classify one entry from what it declared and what resolved. */
    static Diagnosis diagnose(int declared, int resolved) {
        int unresolved = Math.max(declared - resolved, 0)
        String kind = declared == 0 ? NO_LINKS : resolved == 0 ? UNRESOLVED : LINKED
        return new Diagnosis(kind: kind, declared: declared, resolved: resolved,
                unresolved: unresolved)
    }

    /** Diagnosis for one entry's edges, resolving each target against the scope. */
    static Diagnosis diagnose(List<MeshEdge> edges, MeshIndex index) {
        int resolved = (edges ?: []).count { index?.find(it.target) != null }
        return diagnose((edges ?: []).size(), resolved)
    }

    /**
     * A scope-wide report. Three buckets, because "no links" and "some links
     * dangle" are different problems: the first is authoring, the second is
     * identity or surface.
     */
    static Map<String, List<String>> report(MeshIndex index) {
        List<String> noLinks = []
        List<String> partial = []
        List<String> unresolved = []
        index.entries.each { MeshNode entry ->
            List<MeshEdge> declared = entry.file.name.endsWith('.puml') ?
                    PumlMeshReader.read(entry.file.getText('UTF-8')) :
                    MeshMarkers.read(entry.file.getText('UTF-8'), entry.id)
            Diagnosis diagnosis = diagnose(declared, index)
            if (diagnosis.kind == NO_LINKS) {
                noLinks << entry.title
            } else if (diagnosis.kind == UNRESOLVED) {
                unresolved << entry.title
            } else if (diagnosis.unresolved > 0) {
                partial << "${entry.title} (${diagnosis.unresolved} of ${diagnosis.declared} dangling)".toString()
            }
        }
        // A mount can expose the same entry at the root and under a surface;
        // report each problem once.
        return [noLinks: noLinks.unique(), partial: partial.unique(),
                unresolved: unresolved.unique()]
    }
}