package com.barrymac.freeplane.addons.llm.neuros

import com.barrymac.freeplane.addons.llm.Keys
import com.barrymac.freeplane.addons.llm.judgement.EnmeshProposal
import com.barrymac.freeplane.addons.llm.judgement.Judgement
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.ui.UiHelper

import javax.swing.*

/**
 * Enmesh this node: ask a judgement model which relationships are missing, and
 * draw the answer as provisional connectors.
 *
 * The chosen lens decides what to look for, so the mission has a brief. Nothing
 * is written to the source material, and every proposal reads as pencilled-in
 * until a person accepts it.
 */
class NeurosEnmeshAction {

    static final String TAG = 'enmesh proposals'

    static void run(def c, def ui) {
        LogUtils.info('NeurosEnmesh action started.')
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
            File seedFile = LinkTargets.fileFor(node)
            if (seedFile == null || !seedFile.isFile()) {
                UiHelper.showInformationMessage(ui,
                        'Select a knowledge entry that has a link, then enmesh it.')
                return
            }
            String key = Keys.jev()
            if (!key) {
                UiHelper.showInformationMessage(ui,
                        'Enmeshment needs a judgement model. Set JEV_API_KEY in the environment.')
                return
            }

            MeshIndex index = MeshIndex.load(context.source.path(''))
            MeshNode seed = index.forFile(seedFile)
            List<MeshNode> candidates = candidates(index, seed, seedFile)
            if (candidates.isEmpty()) {
                UiHelper.showInformationMessage(ui,
                        'There is nothing left in this scope to enmesh with.')
                return
            }

            Set<String> lens = chooseLens(ui, node.map.storage)
            if (lens == null) return
            MeshLens.write(node.map.storage, lens)

            Map seedState = [id: seed?.id, title: seed?.title ?: node.text,
                             text: EnmeshProposal.trim(NeurosContradictionAction.entryText(seedFile))]
            List<Map> candidateState = candidates.collect { MeshNode candidate ->
                [id: candidate.id, title: candidate.title,
                 text: EnmeshProposal.trim(NeurosContradictionAction.entryText(candidate.file))]
            }
            Map battery = EnmeshProposal.battery(candidateState, seedState.title)
            List<Judgement.Answer> answers = Judgement.ask(battery.state, battery.questions, key)
            List<MeshEdge> proposals = EnmeshProposal.proposals(
                    candidateState, seedState.id, answers).findAll { lens.contains(it.family) }

            if (proposals.isEmpty()) {
                UiHelper.showInformationMessage(ui,
                        "No missing ${lens.size()} lens relationships were supported by the text. " +
                                'That is a real answer: the entry may already be enmeshed as far as ' +
                                'this scope can tell.')
                return
            }
            MeshDrawing drawing = MeshDrawing.draw(node, index, proposals)
            node.tags.remove(TAG)
            node.tags.add(TAG)
            LogUtils.info("NeurosEnmesh: ${proposals.size()} proposals, ${drawing.summary()}")
            UiHelper.showInformationMessage(ui,
                    "${proposals.size()} proposed relationship${proposals.size() == 1 ? '' : 's'} " +
                            'drawn dashed and grey — accept or delete them before trusting the map.\n\n' +
                            drawing.summary())
        } catch (Exception problem) {
            LogUtils.severe("Error in NeurosEnmesh action: ${problem.message}", problem)
            UiHelper.showErrorMessage(ui, "Enmesh This Node Error: ${problem.message}")
        }
    }

    /** Entries the seed does not already point at, bounded. */
    static List<MeshNode> candidates(MeshIndex index, MeshNode seed, File seedFile) {
        Set<String> existing = NeurosMeshAction.readEdges(seedFile, seed)
                .collect { index.find(it.target)?.file?.name }
                .findAll { it != null } as Set
        return index.entries
                .findAll { it.file != seedFile && !existing.contains(it.file.name) }
                .take(EnmeshProposal.MAX_CANDIDATES)
    }

    /** Family picker; returns null when the user cancels. */
    static Set<String> chooseLens(def ui, def storage) {
        Set<String> current = MeshLens.read(storage)
        Map<String, JCheckBox> boxes = [:]
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1))
        panel.add(new JLabel('<html>Which lenses should the mission look through?</html>'))
        MeshLens.FAMILIES.each { String family, String label ->
            JCheckBox box = new JCheckBox(label, current.contains(family))
            boxes[family] = box
            panel.add(box)
        }
        int choice = JOptionPane.showConfirmDialog(ui.currentFrame, panel,
                'Enmesh This Node', JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        if (choice != JOptionPane.OK_OPTION) return null
        return boxes.findAll { family, box -> box.selected }.keySet()
    }
}