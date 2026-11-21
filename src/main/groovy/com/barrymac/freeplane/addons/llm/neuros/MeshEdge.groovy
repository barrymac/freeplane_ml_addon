package com.barrymac.freeplane.addons.llm.neuros

/**
 * One typed relationship written in a knowledge document.
 *
 * The marker text is what the substrate reads; the label is what a reader sees.
 * `declared` records whether the active cartridge names this marker, so an
 * emerging relationship type is shown rather than silently dropped.
 */
class MeshEdge {

    String source
    String target
    String marker
    String label
    String family
    boolean declared
    /** Provisional: proposed by a judgement, not yet accepted. */
    boolean proposed
    /** This edge closes a dependency cycle — a defect, not a family relation. */
    boolean cycle
}