package com.barrymac.freeplane.addons.llm.neuros

import groovy.json.JsonSlurper

/**
 * The confirmation loop's plan: what WOULD be written, before anything is written.
 *
 * Reading candidates and producing a dry-run diff is a separate step from applying
 * it, because a non-coder must be able to see the change and undo it. Confirmed
 * candidates produce marker lines; splits and rejections produce none. And a
 * confirmation is only writable when it is evidence-bearing: a named counterpart
 * and evidence above the floor — the same law as everywhere else in this add-on.
 */
class ConfirmationPlan {

    static final double EVIDENCE_FLOOR = 0.75

    /** Load candidates from a JSON file of {id, canonical, proposedType, variants:[…]}. */
    static List<ConfirmCandidate> load(File file) {
        if (file == null || !file.isFile()) return []
        def parsed = new JsonSlurper().parse(file)
        List rows = parsed instanceof List ? parsed : (parsed.candidates ?: [])
        return rows.collect { row -> fromRow(row) }
    }

    static ConfirmCandidate fromRow(Map row) {
        ConfirmCandidate candidate = new ConfirmCandidate(
                id: row.id ?: row.canonical,
                canonical: row.canonical,
                proposedType: row.proposedType ?: row.type ?: 'entity',
                status: row.status ?: 'hollow',
                decision: row.decision,
                mergeTarget: row.mergeTarget)
        (row.variants ?: []).each { variant ->
            candidate.variants << new ConfirmCandidate.Variant(
                    name: variant.name,
                    sourceClass: variant.sourceClass ?: variant.source ?: 'unknown',
                    sourceId: variant.sourceId ?: variant.doc ?: variant.id ?: 'unknown',
                    evidenceId: variant.evidenceId ?: variant.anchor ?: variant.pje ?: variant.sha256,
                    weight: (variant.weight ?: 0d) as double)
        }
        return candidate
    }

    /** True when a decision may be written: named counterpart + strong evidence. */
    static boolean isWritable(ConfirmCandidate candidate) {
        if (candidate.status == 'rejected' || candidate.status == 'split') return false
        if (candidate.status != 'confirmed' && candidate.status != 'merged') return false
        if (!candidate.canonical?.trim()) return false
        if (candidate.status == 'merged' && !candidate.mergeTarget?.trim()) return false
        return candidate.strongest() >= EVIDENCE_FLOOR
    }

    /** The dry-run diff: the marker lines this plan would write, with reasons. */
    static String diff(List<ConfirmCandidate> candidates) {
        List<String> lines = ['DRY RUN — nothing has been written.', '']
        int writable = 0
        candidates.each { candidate ->
            if (isWritable(candidate)) {
                writable++
                String target = candidate.status == 'merged' ? candidate.mergeTarget : candidate.canonical
                lines << "[[${candidate.proposedType}:${target}]]  ← ${candidate.canonical} " +
                        "(evidence ${String.format('%.2f', candidate.strongest())})"
                candidate.variants.each { variant ->
                    lines << "    variant: ${variant.name}  [${variant.chip()}]"
                }
            } else if (candidate.status == 'split') {
                lines << "split: ${candidate.canonical} — kept as separate candidates, no write"
            } else if (candidate.status == 'rejected') {
                lines << "none: ${candidate.canonical} — rejected, no write"
            } else {
                lines << "hollow: ${candidate.canonical} — awaiting confirmation, no write"
            }
        }
        lines << ''
        lines << "${writable} of ${candidates.size()} candidates would write marker lines."
        return lines.join('\n')
    }

    /**
     * Record a decision without writing any markers (wiring is not yet fixed).
     *
     * `attestedBy` names the person attesting the human half of acceptance: the
     * gate bank proves existence, but only a named human can attest that a
     * PRESIDES_OVER means relator, that no variant was folded wrongly, or that no
     * negative control merged. A decision with no attesting name is incomplete.
     */
    static File recordDecision(File log, ConfirmCandidate candidate, String decision, String mergeTarget = null,
                               String attestedBy = null) {
        candidate.decision = decision
        candidate.mergeTarget = mergeTarget
        candidate.status = decision == 'confirm' ? 'confirmed' :
                decision == 'merge' ? 'merged' :
                decision == 'split' ? 'split' : 'rejected'
        if (log != null) {
            log.parentFile?.mkdirs()
            String revision = candidate.decision ? " (revises ${candidate.decision})" : ''
            log << "${new Date().format('yyyy-MM-dd HH:mm:ss')}\t${decision}${revision}\t" +
                    "${candidate.canonical}\t${mergeTarget ?: ''}\t" +
                    "${candidate.strongest()}\t${attestedBy ?: 'unattested'}\n"
        }
        return log
    }
}