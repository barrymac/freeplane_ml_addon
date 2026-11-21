package com.barrymac.freeplane.addons.llm.neuros

import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.ui.UiHelper

/**
 * Draws the dependency closure of one card as a labelled DAG.
 *
 * Only the DEPENDENCY family walks: depends-on, blocked-by, blocks. Depth and
 * fan-out are bounded, truncation is reported honestly, and a dependency cycle
 * is rendered as a defect (red, thick, marked '(cycle!)') rather than hidden.
 * Everything else — identity resolution, by-label disambiguation, gap markers
 * — is the shared MeshDrawing stack, so a dependency target that is missing
 * reads exactly like a missing reference anywhere else in the map.
 */
class NeurosDependencyAction {

    static void run(def c, def ui) {
        LogUtils.info('NeurosDependency action started.')
        try {
            def node = c.selected
            if (node == null) {
                UiHelper.showInformationMessage(ui, 'Please select a card node first.')
                return
            }
            KnowledgeContext context = KnowledgeContext.discover(node.map.file)
            if (!context.isEnabled()) {
                UiHelper.showInformationMessage(ui, context.reason)
                return
            }
            File seedFile = LinkTargets.fileFor(node)
            if (seedFile == null || !seedFile.isFile()) {
                UiHelper.showInformationMessage(ui, LinkTargets.isGapMarker(node) ?
                        'This is a gap marker, not an entry yet. Select a real card.' :
                        'Select a card with a link to its source file.')
                return
            }
            String summary = draw(node, context)
            UiHelper.showInformationMessage(ui, summary)
        } catch (Exception problem) {
            LogUtils.severe("Error in NeurosDependency action: ${problem.message}", problem)
            UiHelper.showErrorMessage(ui, "Dependency DAG Error: ${problem.message}")
        }
    }

    /** Walk and draw; returns the reader-facing summary. */
    static String draw(def node, KnowledgeContext context) {
        File seedFile = LinkTargets.fileFor(node)
        MeshIndex index = MeshIndex.load(context.source.path(''))
        String seedId = MeshIndex.uuidOf(seedFile.name) ?: seedFile.name
        LogUtils.info("NeurosDependency: walking from ${seedFile.name}")

        DependencyWalker.Result result = DependencyWalker.walk(seedId, index) { MeshNode entry ->
            File file = entry.file
            if (file == null || !file.isFile() || file.length() > NeurosMeshAction.MAX_SOURCE_BYTES) return []
            String body = file.getText('UTF-8')
            return MeshMarkers.read(body, MeshIndex.uuidOf(file.name) ?: file.name)
                    .findAll { it.family == MeshVocabulary.DEPENDENCY }
        }

        MeshDrawing drawing = MeshDrawing.draw(node, index, result.edges)
        String summary = "Dependency DAG: ${drawing.summary()}"
        if (result.cycleTargets) {
            summary += "\nCycle detected involving: ${result.cycleTargets.join(', ')} " +
                    '(marked red in the map — this is a defect to repair, not a valid dependency)'
        }
        if (result.truncated) {
            summary += '\nWalk truncated at the depth or edge budget — the closure may be larger.'
        }
        return summary
    }
}
