package com.barrymac.freeplane.addons.llm.neuros

import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.ui.UiHelper

/**
 * Draws the relationships a knowledge entry declares as connectors in the map.
 *
 * The entry — a card or a diagram — is the source of truth: every typed link it
 * carries becomes an edge, its target becomes a node linked to that target, and
 * the relationship reads in words on the connector. Nothing is invented: links
 * that point outside the current scope are counted, not guessed at.
 */
class NeurosMeshAction {

    static final int MAX_SOURCE_BYTES = 2_000_000

    static void run(def c, def ui) {
        LogUtils.info('NeurosMesh action started.')
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
                        'Select a knowledge entry or diagram that has a link, then draw its ' +
                                'relationships.')
                return
            }

            String summary = draw(node, context)
            UiHelper.showInformationMessage(ui, summary)
        } catch (Exception problem) {
            LogUtils.severe("Error in NeurosMesh action: ${problem.message}", problem)
            UiHelper.showErrorMessage(ui, "Draw neurOS Relationships Error: ${problem.message}")
        }
    }

    /** Draw the selected entry's relationships; returns a reader-facing summary. */
    static String draw(def node, KnowledgeContext context) {
        File seedFile = node.link?.file
        MeshIndex index = MeshIndex.load(context.source.path(''))
        MeshNode seed = index.forFile(seedFile)
        List<MeshEdge> edges = readEdges(seedFile, seed)
        if (edges.isEmpty()) {
            return 'This entry declares no relationships yet. Add a typed link to its ' +
                    'source material first.'
        }
        MeshDrawing drawing = MeshDrawing.draw(node, index, edges)
        LogUtils.info("NeurosMesh: ${drawing.summary()}")
        return drawing.summary()
    }

    static List<MeshEdge> readEdges(File file, MeshNode seed) {
        String text = readBounded(file)
        if (file.name.endsWith('.puml')) {
            return PumlMeshReader.read(text)
        }
        return MeshMarkers.read(text, seed?.id)
    }

    private static String readBounded(File file) {
        byte[] data = new byte[MAX_SOURCE_BYTES + 1]
        int count
        file.withInputStream { input -> count = input.read(data, 0, data.length) }
        if (count > MAX_SOURCE_BYTES) {
            throw new IOException('This entry is too large to read safely.')
        }
        return new String(data, 0, Math.max(count, 0), 'UTF-8')
    }
}