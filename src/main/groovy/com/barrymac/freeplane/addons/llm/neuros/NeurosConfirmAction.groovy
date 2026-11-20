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
            KnowledgeContext context = KnowledgeContext.discover(node.map.file)
            if (!context.isEnabled()) {
                UiHelper.showInformationMessage(ui, context.reason)
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

            Map decisionResult = decide(ui, candidate)
            if (decisionResult == null) return
            String decision = decisionResult.decision
            Set<String> excluded = decisionResult.excluded
            if (candidate.decision) {
                LogUtils.info("NeurosConfirm: revising ${candidate.decision} on ${candidate.canonical}")
            }
            String mergeTarget = null
            if (decision == 'merge') {
                mergeTarget = JOptionPane.showInputDialog(ui.currentFrame,
                        'Merge into which canonical entity?', candidate.canonical)
                if (!mergeTarget?.trim()) return
            }
            // The human half of acceptance is signed: who attests this merge?
            // Remember the name for the next decision so it is one prompt, not a chore.
            String attestedBy = JOptionPane.showInputDialog(ui.currentFrame,
                    'Attested by (who confirms this decision?)',
                    ConfigKeys.read('llm.attestedBy', 'barrymac'))
            if (attestedBy?.trim()) {
                ConfigKeys.write('llm.attestedBy', attestedBy.trim())
            }
            File log = new File(candidatesFile.parentFile, DECISIONS_LOG)
            ConfirmationPlan.recordDecision(log, candidate, decision, mergeTarget,
                    attestedBy?.trim(), excluded)
            tag(node, candidate.status)
            branch.note = ConfirmationPlan.diff(candidates)
            LogUtils.info("NeurosConfirm: ${decision} ${candidate.canonical}" +
                    (mergeTarget ? " -> ${mergeTarget}" : ''))
            StringBuilder message = new StringBuilder(
                    "${decision == 'merge' ? 'Merged' : decision == 'confirm' ? 'Confirmed' :
                            decision == 'split' ? 'Split' : 'Rejected'}: ${candidate.canonical}\n\n")
            boolean dryRun = ConfigKeys.read(WriteBack.DRY_RUN_KEY, 'true') != 'false'
            message << ConfirmationPlan.diff(candidates, dryRun)

            // Dry-run is the default. When it is explicitly turned off, confirmed
            // candidates are written to the LOCAL watched workspace — never to the
            // read-only knowledge surface — and availability is reported honestly.
            if (!dryRun) {
                // Try the mount first (inotify auto-ingest); if it refuses writes,
                // fall back to the local workspace (the map's directory).
                File mount = context.source.path('')
                WriteBack.Outcome outcome
                File writtenTo = null
                try {
                    outcome = WriteBack.write([candidate], mount, attestedBy?.trim())
                    writtenTo = mount
                } catch (Exception mountProblem) {
                    LogUtils.info("NeurosConfirm: mount refused write (${mountProblem.message}), " +
                            'falling back to local workspace')
                    outcome = WriteBack.write([candidate], candidatesFile.parentFile,
                            attestedBy?.trim())
                    writtenTo = candidatesFile.parentFile
                }
                    message << '\n\n' << outcome.summary
                    if (outcome.written) {
                        File statusFile = new File(writtenTo,
                                "ingest-status/${outcome.written[0].name}")
                        if (statusFile.isFile()) {
                            String status = statusFile.getText('UTF-8')
                                    .readLines().find { it.startsWith('status:') }
                            message << '\n' << status
                            if (status?.contains('VISIBLE')) {
                                // Invalidate the cached index so the next
                                // Browse/Draw/Enmesh sees the new entity
                                MeshIndex.invalidate()
                                message << '\nCache refreshed — re-run Draw or Browse to see the new connection.'
                            }
                        } else {
                            message << '\nIngestion pending — the substrate will pick it up via inotify.'
                        }
                    }
            }
            UiHelper.showInformationMessage(ui, message.toString())
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
        Map<String, Object> existing = [:]
        branch.children.each { child ->
            String text = (child.text ?: '').toString()
            candidates.each { candidate ->
                if (text.startsWith(candidate.canonical)) {
                    existing[candidate.id] = child
                }
            }
        }
        candidates.each { candidate ->
            def child = existing[candidate.id]
            if (child == null) {
                child = branch.createChild(candidate.displayName())
                child.tags.add('neurOS candidate')
            } else {
                child.text = candidate.displayName()
            }
            child.note = candidate.variants.collect { "${it.name}  [${it.chip()}]" }.join('\n')
        }
        return branch
    }

    private static ConfirmCandidate candidateFor(def node, List<ConfirmCandidate> candidates) {
        String text = (node.text ?: '').toString()
        return candidates.find { text.startsWith(it.canonical) }
    }

    /**
     * Choose the decision AND which variants it applies to. A merge is rarely
     * all-or-nothing: three variants may be the same judge while a fourth is a
     * misspelling that must stay out. Each variant gets a checkbox so the outlier
     * is excluded explicitly rather than folded in by default.
     */
    private static Map decide(def ui, ConfirmCandidate candidate) {
        javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.GridLayout(0, 1))
        panel.add(new javax.swing.JLabel("<html><b>${candidate.canonical}</b> — choose which variants apply:</html>"))
        Map<String, javax.swing.JCheckBox> boxes = [:]
        candidate.variants.each { variant ->
            javax.swing.JCheckBox box = new javax.swing.JCheckBox(
                    "${variant.name}  [${variant.chip()}]", true)
            boxes[variant.name] = box
            panel.add(box)
        }
        panel.add(new javax.swing.JLabel('<html><br>Uncheck any outlier to exclude it from this decision.</html>'))
        String[] options = ['Merge into existing entity…', 'Keep as separate entity (split)',
                            'Confirm as new entity', 'None (reject)', 'Cancel']
        int choice = JOptionPane.showOptionDialog(ui.currentFrame, panel,
                'Resolve candidate', JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[0])
        if (choice < 0 || choice == 4) return null
        Set<String> excluded = boxes.findAll { name, box -> !box.selected }.keySet()
        return [decision: ['merge', 'split', 'confirm', 'reject'][choice], excluded: excluded]
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
        String configured = ConfigKeys.read('llm.neuros.candidatesFile', null)
        if (configured?.trim()) return new File(configured)
        File mapFile = node.map.file
        if (mapFile != null) return new File(mapFile.parentFile, 'candidates.json')
        return null
    }
}