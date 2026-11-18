package com.barrymac.freeplane.addons.llm.neuros

import com.barrymac.freeplane.addons.llm.Keys
import com.barrymac.freeplane.addons.llm.judgement.ContradictionPlanner
import com.barrymac.freeplane.addons.llm.judgement.Judgement
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.ui.UiHelper

/**
 * Asks a judgement model whether the selected entry disagrees with the entries
 * it links to, and draws only what the judgement supports.
 *
 * Detection alone is not useful: each pair ends as a contradiction, a
 * reconciliation under a condition, or unresolved. Unresolved pairs are
 * reported and never drawn, and no source file is touched.
 */
class NeurosContradictionAction {

    static final int MAX_SOURCE_BYTES = 2_000_000

    static void run(def c, def ui) {
        LogUtils.info('NeurosContradictions action started.')
        try {
            def node = c.selected
            if (node == null) {
                UiHelper.showInformationMessage(ui, 'Please select a node first.')
                return
            }
            KnowledgeContext context = KnowledgeContext.discover(node.map.file)
            if (!context.isEnabled()) {
                UiHelper.showInformationMessage(ui, context.reason)
                return
            }
            File seedFile = node.link?.file
            if (seedFile == null || !seedFile.isFile()) {
                UiHelper.showInformationMessage(ui,
                        'Select a knowledge entry that has a link, then look for disagreements.')
                return
            }
            String key = Keys.jev()
            if (!key) {
                UiHelper.showInformationMessage(ui,
                        'This needs a judgement model. Set JEV_API_KEY in the environment, then ' +
                                'try again.')
                return
            }

            MeshIndex index = MeshIndex.load(context.source.path(''))
            MeshNode seed = index.forFile(seedFile)
            List<MeshEdge> declared = NeurosMeshAction.readEdges(seedFile, seed)
            List<MeshNode> candidates = declared
                    .collect { index.find(it.target) }
                    .findAll { it != null && it.file != seedFile }
                    .unique { it.file }
            if (candidates.isEmpty()) {
                UiHelper.showInformationMessage(ui,
                        'This entry has no resolved neighbours to compare. Draw its relationships ' +
                                'first, or add typed links to related entries.')
                return
            }

            Map seedState = [id: seed?.id, title: seed?.title ?: node.text,
                             text: entryText(seedFile)]
            List<Map> candidateState = candidates.collect { MeshNode candidate ->
                [id: candidate.id, title: candidate.title, text: entryText(candidate.file)]
            }
            List<ContradictionPlanner.Pair> pairs =
                    ContradictionPlanner.pairs(seedState, candidateState)
            Map battery = ContradictionPlanner.battery(pairs)

            List<Judgement.Answer> answers = Judgement.ask(battery.state, battery.questions, key)
            List<ContradictionPlanner.Resolution> resolutions =
                    ContradictionPlanner.resolve(pairs, answers)

            List<MeshEdge> edges = resolutions.findAll { it.drawable }.collect { resolution ->
                new MeshEdge(
                        source: resolution.pair.leftId,
                        target: resolution.pair.rightId,
                        marker: resolution.kind,
                        label: resolution.label,
                        family: resolution.family,
                        declared: MeshVocabulary.isDeclared(resolution.kind))
            }
            if (edges.isEmpty()) {
                UiHelper.showInformationMessage(ui, summary(resolutions))
                return
            }
            MeshDrawing drawing = MeshDrawing.draw(node, index, edges)
            UiHelper.showInformationMessage(ui, summary(resolutions) + '\n\n' + drawing.summary())
        } catch (Exception problem) {
            LogUtils.severe("Error in NeurosContradictions action: ${problem.message}", problem)
            UiHelper.showErrorMessage(ui,
                    "Find neurOS Disagreements Error: ${problem.message}")
        }
    }

    /** Reader-facing summary: what was decided, and on what evidence. */
    static String summary(List<ContradictionPlanner.Resolution> resolutions) {
        int contradictions = resolutions.count { it.kind == ContradictionPlanner.CONTRADICTS }
        int reconciliations = resolutions.count { it.kind == ContradictionPlanner.RECONCILES }
        int unresolved = resolutions.count { it.kind == ContradictionPlanner.UNRESOLVED }
        List<String> parts = []
        if (contradictions) parts << "${contradictions} contradict${contradictions == 1 ? 'ion' : 'ions'}"
        if (reconciliations) parts << "${reconciliations} reconciled under a condition"
        if (unresolved) parts << "${unresolved} left unresolved"
        if (parts.isEmpty()) return 'Nothing to report.'
        StringBuilder text = new StringBuilder(parts.join(', ') + '.')
        resolutions.findAll { it.kind != ContradictionPlanner.UNRESOLVED }.each { resolution ->
            text << '\n• ' << (resolution.pair.rightTitle ?: 'entry') << ' — ' << resolution.ground
        }
        resolutions.findAll { it.kind == ContradictionPlanner.UNRESOLVED }.each { resolution ->
            text << '\n• ' << (resolution.pair.rightTitle ?: 'entry') << ' — ' << resolution.ground
        }
        return text.toString()
    }

    /** The entry as a reader sees it, bounded, without markers or metadata. */
    static String entryText(File file) {
        if (file == null || !file.isFile()) return ''
        String raw
        byte[] data = new byte[MAX_SOURCE_BYTES + 1]
        int count
        file.withInputStream { input -> count = input.read(data, 0, data.length) }
        if (count > MAX_SOURCE_BYTES) {
            raw = new String(data, 0, MAX_SOURCE_BYTES, 'UTF-8')
        } else {
            raw = new String(data, 0, Math.max(count, 0), 'UTF-8')
        }
        return MarkdownPreviewSanitizer.clean(raw)
    }
}