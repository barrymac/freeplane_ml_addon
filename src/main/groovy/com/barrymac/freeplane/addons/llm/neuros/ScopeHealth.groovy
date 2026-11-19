package com.barrymac.freeplane.addons.llm.neuros

/**
 * The state a knowledge read actually returned, not just what it contained.
 *
 * Three states, because two of them look identical from the listing alone:
 *   - DEGRADED: the scope is working (a backfill drain, a warming projection) and
 *     the read was refused with EAGAIN / "transient not-ready". Empty here means
 *     TRY LATER, never "the mesh is empty".
 *   - EMPTY: a healthy scope genuinely has nothing to show.
 *   - OK: the scope answered with content.
 *
 * The distinction matters because a user who sees a blank map during activation
 * would conclude the fix failed, when actually hundreds of edges are wiring.
 */
class ScopeHealth {

    static final String OK = 'ok'
    static final String EMPTY = 'empty'
    static final String DEGRADED = 'degraded'

    static final String CHIP = 'scope degraded / transient not-ready'

    /** Classify from what the read produced and any failure it reported. */
    static String classify(int entryCount, Throwable failure) {
        if (isNotReady(failure)) return DEGRADED
        if (failure != null) return DEGRADED
        return entryCount > 0 ? OK : EMPTY
    }

    /** EAGAIN / "not ready" is a drain, not a fault. */
    static boolean isNotReady(Throwable failure) {
        if (failure == null) return false
        String message = (failure.message ?: '').toLowerCase()
        return message.contains('eagain') || message.contains('errno 11') ||
                message.contains('not-ready') || message.contains('not ready') ||
                message.contains('degraded') || message.contains('try again')
    }

    /** What the reader should be told, with the degraded reason when we have it. */
    static String describe(String state, String degradedReason = null) {
        switch (state) {
            case DEGRADED:
                return 'The knowledge source is busy right now (a backfill drain or a warming ' +
                        'projection). Empty here means try again shortly, NOT that the mesh is empty.' +
                        (degradedReason ? " Reason reported: ${degradedReason}" : '')
            case EMPTY:
                return 'The scope is healthy and returned nothing. This is a real absence, ' +
                        'not a drain.'
            default:
                return 'The scope answered normally.'
        }
    }
}