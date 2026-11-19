package com.barrymac.freeplane.addons.llm.neuros

import java.awt.Color

/**
 * Reader-facing vocabulary for graph markers.
 *
 * The cartridge decides which markers wire an edge; this table only decides how
 * an edge reads and how it is coloured. Markers absent from the table still
 * render — they are labelled from their own name, because the graph accepts
 * vocabulary that a cartridge has not enumerated yet.
 */
class MeshVocabulary {

    static final String DEPENDENCY = 'dependency'
    static final String BLOCKING = 'blocking'
    static final String STRUCTURE = 'structure'
    static final String TRACKING = 'tracking'
    static final String REFERENCE = 'reference'
    static final String CODE = 'code'
    static final String GOVERNANCE = 'governance'
    static final String CONTRADICTION = 'contradiction'
    static final String AUTHORITY = 'authority'
    static final String CITATION = 'citation'
    static final String PROCEDURE = 'procedure'

    /** marker -> [human label, colour family] */
    static final Map<String, List<String>> DECLARED = [
            'depends-on'   : ['depends on', DEPENDENCY],
            'blocked-by'   : ['blocked by', DEPENDENCY],
            'blocks'       : ['blocks', BLOCKING],
            'child-of'     : ['child of', STRUCTURE],
            'part-of'      : ['part of', STRUCTURE],
            'belongs-to'   : ['belongs to', STRUCTURE],
            'scope-of'     : ['scopes', STRUCTURE],
            'promoted-from': ['promoted from', STRUCTURE],
            'tracks'       : ['tracks', TRACKING],
            'card'         : ['relates to', TRACKING],
            'harness'      : ['harness for', TRACKING],
            'references'   : ['references', REFERENCE],
            'node-ref'     : ['references', REFERENCE],
            'depicts'      : ['depicts', REFERENCE],
            'documented-by': ['documented by', REFERENCE],
            'pr'           : ['pull request', REFERENCE],
            'fixes'        : ['fixes', REFERENCE],
            'closes'       : ['closes', REFERENCE],
            'repo'         : ['repository', REFERENCE],
            'commit'       : ['commit', REFERENCE],
            'reviewed-by'  : ['reviewed by', REFERENCE],
            'reviewer'     : ['reviewer', REFERENCE],
            'author'       : ['authored by', REFERENCE],
            'authored-by'  : ['authored by', REFERENCE],
            'developer'    : ['developer', REFERENCE],
            'tested-by'    : ['tested by', CODE],
            'implements'   : ['implements', CODE],
            'extends'      : ['extends', CODE],
            'calls'        : ['calls', CODE],
            'invokes'      : ['invokes', CODE],
            'contains'     : ['contains', CODE],
            'hotspot'      : ['hotspot of', CODE],
            'violates'     : ['violates', GOVERNANCE],
            'prevents'     : ['prevents', GOVERNANCE],
            'resolves'     : ['resolves', GOVERNANCE],
            'requires'     : ['requires', GOVERNANCE],
            'capability'   : ['needs capability', GOVERNANCE],
            'applies-to'   : ['applies to', GOVERNANCE],
            'visible-to'   : ['visible to', GOVERNANCE],
            'tenet'        : ['tenet', GOVERNANCE],
            'sop'          : ['procedure', GOVERNANCE],
            'alfa-rule'    : ['rule', GOVERNANCE],
            'violation'    : ['violation', GOVERNANCE],
            'anti_pattern' : ['anti-pattern', GOVERNANCE],
            // Disagreement is a first-class relationship in legal and scholarly
            // graphs, so it reads as its own family rather than a reference.
            'contradicts'  : ['contradicts', CONTRADICTION],
            'conflicts-with': ['conflicts with', CONTRADICTION],
            'rebuts'       : ['rebuts', CONTRADICTION],
            'disputes'     : ['disputes', CONTRADICTION],
            'overrules'    : ['overrules', CONTRADICTION],
            // Legal and scholarly relationship vocabulary (hohfeld-style). Kept as
            // ordinary markers, not a special legal mode: the same lens machinery
            // reads them whether the scope is software or a case file.
            'presides-over': ['presides over', AUTHORITY],
            'held-by'      : ['held by', AUTHORITY],
            'authorised-by': ['authorised by', AUTHORITY],
            'governs'      : ['governs', GOVERNANCE],
            'bound-by'     : ['bound by', GOVERNANCE],
            'against'      : ['against', CONTRADICTION],
            'opposes'      : ['opposes', CONTRADICTION],
            'regarding'    : ['regarding', REFERENCE],
            'correlates-to': ['correlates to', REFERENCE],
            'cites'        : ['cites', CITATION],
            'cited-by'     : ['cited by', CITATION],
            'authority-for': ['authority for', CITATION],
            'distinguishes': ['distinguishes', CONTRADICTION],
            'supersedes'   : ['supersedes', CONTRADICTION],
            'decided-by'   : ['decided by', PROCEDURE],
            'relator'      : ['relator of', AUTHORITY],
            'party-to'     : ['party to', PROCEDURE],
            'witness-for'  : ['witness for', PROCEDURE],
            'counsel-for'  : ['counsel for', PROCEDURE]
    ]

    private static final Map<String, Color> COLORS = [
            (DEPENDENCY): new Color(0xC0, 0x6A, 0x00),
            (BLOCKING)  : new Color(0xB0, 0x20, 0x20),
            (STRUCTURE) : new Color(0x40, 0x60, 0xA0),
            (TRACKING)  : new Color(0x2E, 0x7D, 0x32),
            (REFERENCE) : new Color(0x70, 0x70, 0x70),
            (CODE)      : new Color(0x00, 0x70, 0x70),
            (GOVERNANCE): new Color(0x70, 0x40, 0x90),
            (CONTRADICTION): new Color(0xB0, 0x10, 0x40),
            (AUTHORITY)   : new Color(0x1F, 0x4E, 0x8C),
            (CITATION)    : new Color(0x6A, 0x50, 0xA0),
            (PROCEDURE)   : new Color(0x2E, 0x6E, 0x5E)
    ]

    static boolean isDeclared(String marker) {
        return DECLARED.containsKey(normalize(marker))
    }

    /** A human reading of the marker, even when the cartridge does not name it. */
    static String label(String marker) {
        String key = normalize(marker)
        List<String> declared = DECLARED[key]
        if (declared != null) return declared[0]
        return key.replace('-', ' ').replace('_', ' ').trim()
    }

    static String family(String marker) {
        String key = normalize(marker)
        List<String> declared = DECLARED[key]
        return declared != null ? declared[1] : REFERENCE
    }

    static Color color(String family) {
        return COLORS[family] ?: COLORS[REFERENCE]
    }

    /** Contradictions are drawn dashed so disagreement reads at a glance. */
    static boolean isDashed(String family) {
        return family == CONTRADICTION
    }

    /** Dash pattern for a family, or null when the line is solid. */
    static int[] dashFor(String family) {
        return isDashed(family) ? ([6, 4] as int[]) : null
    }

    private static String normalize(String marker) {
        return (marker ?: '').trim().toLowerCase()
    }
}