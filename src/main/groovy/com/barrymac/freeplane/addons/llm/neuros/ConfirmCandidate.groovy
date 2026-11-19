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
        double weight

        String chip() {
            return "${sourceClass} (${sourceId})"
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

    /** The strongest evidence for the canonical form, for the confidence chip. */
    double strongest() {
        return variants.collect { it.weight }.max() ?: 0d
    }

    String displayName() {
        return "${canonical}  [${status}]"
    }
}