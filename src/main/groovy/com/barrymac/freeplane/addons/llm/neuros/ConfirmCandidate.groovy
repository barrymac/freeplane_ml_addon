package com.barrymac.freeplane.addons.llm.neuros

/**
 * One candidate entity awaiting human resolution.
 *
 * A candidate is NOT an entity yet. It carries a canonical name and the variants
 * that may refer to the same thing, each with its source class and identifier —
 * the evidence chips a non-coder needs to decide merge / split / none without
 * ever reading raw text. `hollow` is the only honest starting state.
 */
class ConfirmCandidate {

    static class Variant {
        String name
        String sourceClass
        String sourceId
        /** Calibration anchor id, PJe document number, or SHA-256 when no footer id. */
        String evidenceId
        double weight

        String chip() {
            return "${sourceClass} (${sourceId})"
        }

        /** Independence is by anchor/document, never by the variant's spelling. */
        String independenceKey() {
            return "${sourceClass}|${evidenceId ?: sourceId}"
        }
    }

    String id
    String canonical
    String proposedType
    List<Variant> variants = []
    /** hollow until a person decides; never silently promoted. */
    String status = 'hollow'
    String decision
    String mergeTarget
    Set<String> excludedVariants = [] as Set

    /** The strongest evidence for the canonical form, for the confidence chip. */
    double strongest() {
        return variants.collect { it.weight }.max() ?: 0d
    }

    /**
     * Independent evidence count: two Escavador copies of the same decision are
     * ONE source, however many spellings of the name they carry. Distinct anchor
     * ids or document hashes are what make corroboration independent.
     */
    int independentSources() {
        return variants.collect { it.independenceKey() }.unique().size()
    }

    String displayName() {
        return "${canonical}  [${status}]"
    }
}