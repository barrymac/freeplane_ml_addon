package com.barrymac.freeplane.addons.llm.neuros

import com.barrymac.freeplane.addons.llm.ConfigKeys
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.ui.UiHelper

import javax.swing.JOptionPane

/**
 * Resolve candidate entities in the map: confirm / merge / split / none.
 *
 * The review branch lists every candidate as a hollow node with its canonical
 * name, evidence chips and source identifiers. Selecting one opens the decision
 * dialog; the decision is tagged on the node and appended to a local decisions
 * log, and the dry-run diff shows exactly what would be written. No marker lines
 * are written while marker→edge wiring is unfixed — the loop records decisions
 * honestly instead of pretending to write edges.
 */
class NeurosConfirmAction {

    static final String BRANCH_TITLE = 'Candidate entities (review)'
    static final String BRANCH_TAG = 'neurOS candidates'
    static final String DECISIONS_LOG = 'confirmation-decisions.log'

    static void run(def c, def ui, def config) {
        LogUtils.info('NeurosConfirm action started.')
        try {
            def node = c.selected
            if (node == null) {
                UiHelper.showInformationMessage(ui, 'Please select a candidate node first.')
                return
            }
            File candidatesFile = candidatesFile(node)
            List<ConfirmCandidate> candidates = ConfirmationPlan.load(candidatesFile)
            if (candidates.isEmpty()) {
                UiHelper.showInformationMessage(ui,
                        'No candidates to review yet. The next parser run will place them here.')
                return
            }

            def branch = reviewBranch(node, candidates)
            ConfirmCandidate candidate = candidateFor(node, candidates)
            if (candidate == null) {
                UiHelper.showInformationMessage(ui,
                        'Select a candidate node (a name in brackets), then resolve it. ' +
                                'The dry-run diff is in the branch note.')
                branch.note = ConfirmationPlan.diff(candidates)
                return
            }

            String decision = decide(ui, candidate)
            if (decision == null) return
            String mergeTarget = null
            if (decision == 'merge') {
                mergeTarget = JOptionPane.showInputDialog(ui.currentFrame,
                        'Merge into which canonical entity?', candidate.canonical)
                if (!mergeTarget?.trim()) return
            }
            File log = new File(candidatesFile.parentFile, DECISIONS_LOG)
            ConfirmationPlan.recordDecision(log, candidate, decision, mergeTarget)
            tag(node, candidate.status)
            branch.note = ConfirmationPlan.diff(candidates)
            LogUtils.info("NeurosConfirm: ${decision} ${candidate.canonical}" +
                    (mergeTarget ? " -> ${mergeTarget}" : ''))
            UiHelper.showInformationMessage(ui,
                    "${decision == 'merge' ? 'Merged' : decision == 'confirm' ? 'Confirmed' :
                            decision == 'split' ? 'Split' : 'Rejected'}: ${candidate.canonical}\n\n" +
                            ConfirmationPlan.diff(candidates))
        } catch (Exception problem) {
            LogUtils.severe("Error in NeurosConfirm action: ${problem.message}", problem)
            UiHelper.showErrorMessage(ui, "Resolve Candidate Error: ${problem.message}")
        }
    }

    /** Build (or reuse) the review branch: hollow candidates with evidence chips. */
    static def reviewBranch(def node, List<ConfirmCandidate> candidates) {
        def root = node.map.root
        def queue = [root]
        def branch = null
        while (!queue.isEmpty() && branch == null) {
            def current = queue.remove(0)
            if (current.tags?.contains(BRANCH_TAG)) branch = current
            queue.addAll(current.children)
        }
        if (branch == null) {
            branch = root.createChild(BRANCH_TITLE)
            branch.tags.add(BRANCH_TAG)
            branch.note = ConfirmationPlan.diff(candidates)
        }
        candidates.each { candidate ->
            def child = branch.createChild(candidate.displayName())
            child.tags.add('neurOS candidate')
            child.note = candidate.variants.collect { "${it.name}  [${it.chip()}]" }.join('\n')
        }
        return branch
    }

    private static ConfirmCandidate candidateFor(def node, List<ConfirmCandidate> candidates) {
        String text = (node.text ?: '').toString()
        return candidates.find { text.startsWith(it.canonical) }
    }

    private static String decide(def ui, ConfirmCandidate candidate) {
        String[] options = ['Confirm', 'Merge…', 'Split', 'None (reject)', 'Cancel']
        int choice = JOptionPane.showOptionDialog(ui.currentFrame,
                "${candidate.canonical}\n\n" +
                        candidate.variants.collect { "${it.name}  [${it.chip()}]" }.join('\n'),
                'Resolve candidate', JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[0])
        if (choice < 0 || choice == 4) return null
        return ['confirm', 'merge', 'split', 'reject'][choice]
    }

    private static void tag(def node, String status) {
        ['hollow', 'confirmed', 'merged', 'split', 'rejected'].each { String existing ->
            if (node.tags.contains(existing)) node.tags.remove(existing)
        }
        node.tags.add(status)
        String text = (node.text ?: '').toString().replaceAll(/\s*\[[^\]]*\]\s*$/, '')
        node.text = "${text}  [${status}]"
    }

    private static File candidatesFile(def node) {
        String configured = ConfigKeys.read(node.map.storage, 'llm.neuros.candidatesFile', null)
        if (configured?.trim()) return new File(configured)
        File mapFile = node.map.file
        if (mapFile != null) return new File(mapFile.parentFile, 'candidates.json')
        return null
    }
}