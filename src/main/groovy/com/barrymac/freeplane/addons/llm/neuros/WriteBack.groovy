package com.barrymac.freeplane.addons.llm.neuros

import org.freeplane.core.util.LogUtils

/**
 * Writes confirmed candidates as ordinary card text into a LOCAL watched workspace.
 *
 * The knowledge surface itself is never written. The card file is the same shape
 * any other card has: an identity marker, a title, the decision, and the variants
 * that were merged — with outliers excluded explicitly rather than folded in.
 * Availability is reported honestly: if the source exposes ingest-status for the
 * file, we read it; otherwise we say the file was written locally, unconfirmed.
 */
class WriteBack {

    static final String DRY_RUN_KEY = 'llm.neuros.dryRun'

    static class Outcome {
        List<File> written = []
        String summary
    }

    /** Write the confirmed/merged candidates into the local watched workspace. */
    static Outcome write(List<ConfirmCandidate> candidates, File workspace, String attestedBy) {
        if (workspace == null) {
            throw new IllegalArgumentException(
                    'No local watched workspace to write to. Save the map in a watched ' +
                            'workspace with a *_KNOWLEDGE link first.')
        }
        if (!workspace.isDirectory() && !workspace.mkdirs()) {
            throw new IOException("Cannot create the workspace ${workspace}")
        }
        Outcome outcome = new Outcome()
        List<ConfirmCandidate> writable = candidates.findAll { ConfirmationPlan.isWritable(it) }
        writable.each { candidate ->
            File file = cardFile(workspace, candidate)
            file.text = cardText(candidate, attestedBy)
            outcome.written << file
            LogUtils.info("WriteBack: wrote ${file.name}")
        }
        outcome.summary = outcome.written.isEmpty() ?
                'Nothing confirmed enough to write (evidence below 0.75, or only splits/rejections).' :
                "${outcome.written.size()} card file(s) written to ${workspace.path}. " +
                        'Availability is read back through ingest-status when the source exposes it.'
        return outcome
    }

    static File cardFile(File workspace, ConfirmCandidate candidate) {
        String uuid = UUID.randomUUID().toString()
        String slug = candidate.canonical.toLowerCase()
                .replaceAll(/[^a-z0-9]+/, '-').replaceAll(/^-+|-+$/, '')
        return new File(workspace, "CARD-${uuid}-${slug}.card.md")
    }

    static String cardText(ConfirmCandidate candidate, String attestedBy) {
        StringBuilder text = new StringBuilder()
        String target = candidate.status == 'merged' && candidate.mergeTarget ?
                candidate.mergeTarget : candidate.canonical
        text << "[[${candidate.proposedType}:${target}]]\n"
        text << "# ${candidate.canonical}\n\n"
        text << "list: Backlog\n"
        text << "priority: P2\n"
        text << "type: ${candidate.proposedType}\n"
        text << "source: freeplane-addon:confirmation\n\n"
        text << "---\n\n"
        text << "${candidate.decision == 'merge' ? 'Merged' : candidate.decision.capitalize()}."
        if (candidate.mergeTarget) text << " into ${candidate.mergeTarget}"
        text << " (evidence ${String.format('%.2f', candidate.strongest())}).\n\n"
        text << "Variants merged:\n"
        candidate.variants.each { variant ->
            boolean excluded = candidate.excludedVariants?.contains(variant.name)
            text << (excluded ? "- EXCLUDED: ${variant.name} [${variant.chip()}]\n" :
                    "- ${variant.name} [${variant.chip()}]\n")
        }
        text << "\nAttested by: ${attestedBy ?: 'unattested'}\n"
        return text.toString()
    }
}