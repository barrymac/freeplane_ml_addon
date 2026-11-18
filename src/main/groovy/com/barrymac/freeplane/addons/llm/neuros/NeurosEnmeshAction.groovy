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
                LogUtils.info('NeurosEnmesh: no node selected')
                UiHelper.showInformationMessage(ui, 'Please select a node first.')
                return
            }
            KnowledgeContext context = KnowledgeContext.discover(node.map.file)
            if (!context.isEnabled()) {
                LogUtils.info("NeurosEnmesh: neurOS not enabled — ${context.reason}")
                UiHelper.showInformationMessage(ui, context.reason)
                return
            }
            File seedFile = LinkTargets.fileFor(node)
            if (seedFile == null || !seedFile.isFile()) {
                LogUtils.info("NeurosEnmesh: link unresolved — hasLink=${LinkTargets.hasLink(node)} " +
                        "link=${LinkTargets.describe(node)}")
                UiHelper.showInformationMessage(ui, LinkTargets.hasLink(node) ?
                        "The link on this node does not point at a readable file: " +
                                LinkTargets.describe(node) :
                        'Select a knowledge entry that has a link.')
                return
            }
            String key = Keys.jev()
            if (!key) {
                LogUtils.info("NeurosEnmesh: no Jev key. env JEV_API_KEY=" +
                        "${Keys.environment(Keys.JEV) ? 'present' : 'absent'} " +
                        "snapshot=${Keys.environmentSnapshot().size()} vars")
                UiHelper.showInformationMessage(ui,
                        'Enmeshment needs a judgement model. Set JEV_API_KEY in the environment.')
                return
            }

            MeshIndex index = MeshIndex.load(context.source.path(''))
            MeshNode seed = index.forFile(seedFile)
            List<MeshNode> candidates = candidates(index, seed, seedFile)
            LogUtils.info("NeurosEnmesh: seed='${seed?.title}' entries=${index.entries.size()} " +
                    "candidates=${candidates.size()}")
            if (candidates.isEmpty()) {
                LogUtils.info('NeurosEnmesh: every entry already has a declared link to this one')
                UiHelper.showInformationMessage(ui,
                        'There is nothing left in this scope to enmesh with.')
                return
            }

            Set<String> lens = chooseLens(ui, node.map.storage)
            if (lens == null) {
                LogUtils.info('NeurosEnmesh: cancelled at the lens picker')
                return
            }
            MeshLens.write(node.map.storage, lens)
            LogUtils.info("NeurosEnmesh: lens=${lens}")

            String seedText = NeurosContradictionAction.entryText(seedFile)
            Map seedState = [id: seed?.id, title: seed?.title ?: node.text,
                             text: EnmeshProposal.trim(seedText)]
            List<Map> candidateState = candidates.collect { MeshNode candidate ->
                [id: candidate.id, title: candidate.title,
                 text: EnmeshProposal.trim(NeurosContradictionAction.entryText(candidate.file))]
            }
            // Retrieval is code's job, and it has three channels here: what the
            // entry names, what shares its vocabulary, and what the substrate
            // considers semantically near. The model never sees the rest.
            List<String> semantic = SemanticCandidates.query(
                    context.source.path(''),
                    SemanticCandidates.questionFor(seedState.title, seedText))
            LogUtils.info("NeurosEnmesh: semantic=${semantic.size()} " + semantic.take(5).join(' | '))
            // The semantic surface names both cards and code; resolve against
            // everything the index now holds (cards first, then code files).
            List<Map> semanticState = semantic.collect { String name -> index.find(name) }
                    .findAll { it != null }
                    .collect { MeshNode match ->
                        [id: match.id ?: match.file.name, title: match.title,
                         text: EnmeshProposal.trim(NeurosContradictionAction.entryText(match.file))]
                    }

            List<Map> pooled = (candidateState + semanticState)
                    .unique { it.id ?: it.title }
            candidateState = EnmeshCandidates.rank(seedState.text, pooled)
            LogUtils.info("NeurosEnmesh: ranked candidates=" +
                    candidateState.collect { it.title }.join(' | '))
            // Evidence first: files and diagrams the entry names outright need no
            // judgement. The code/files live outside the card index, so extend the
            // drawing index after the card candidates have been chosen.
            List<File> scopeFiles = TextReferences.scopeFiles(context.source.path(''))
            scopeFiles.each { index.add(it) }
            // Retrieval sees the whole entry; only the judgement call is trimmed.
            List<MeshEdge> named = TextReferences.proposals(seedText, seedState.id, scopeFiles)
            LogUtils.info("NeurosEnmesh: named-in-text=${named.size()} " +
                    named.collect { it.target }.join(', '))

            if (candidateState.isEmpty()) {
                LogUtils.info('NeurosEnmesh: no entry shares vocabulary with this one')
            }
            // Stage one (NS): which of these deserves an edge at all? Ranked, so
            // only the strongest few reach the naming question.
            Map whether = EnmeshProposal.shortlistBattery(candidateState, seedState.title)
            List<Judgement.Answer> worth = Judgement.ask(whether.state, whether.questions, key)
            List<Integer> shortlist = EnmeshProposal.shortlisted(
                    worth, candidateState.size(), 5, 0.5)
            LogUtils.info("NeurosEnmesh: worth=" + worth.collect {
                "${it.question}:${it.probability}".toString()
            }.join(' ') + " → shortlist=${shortlist}")

            List<MeshEdge> proposals = []
            if (!shortlist.isEmpty()) {
                List<Map> chosen = shortlist.collect { candidateState[it] }
                Map battery = EnmeshProposal.battery(chosen, seedState.title)
                List<Judgement.Answer> answers = Judgement.ask(battery.state, battery.questions, key)
                LogUtils.info("NeurosEnmesh: answers=" + answers.collect {
                    "${it.question}:${it.choice ?: it.probability}@${it.confidence ?: ''}".toString()
                }.join(' '))
                proposals = EnmeshProposal.proposals(chosen, seedState.id, answers)
                        .findAll { lens.contains(it.family) }
            }
            List<MeshEdge> drawn = (named.findAll { lens.contains(it.family) } + proposals)

            if (drawn.isEmpty()) {
                LogUtils.info('NeurosEnmesh: nothing named in the text and no proposal passed the threshold')
                UiHelper.showInformationMessage(ui,
                        'No relationship in this lens was supported by the text ' +
                                "(${lens.size()} families checked, " +
                                "${candidateState.size()} entries compared). " +
                                'That is a real answer: nothing in those entries states a link, even ' +
                                'if one exists in the work.')
                return
            }
            MeshDrawing drawing = MeshDrawing.draw(node, index, drawn)
            node.tags.remove(TAG)
            node.tags.add(TAG)
            MeshReveal.reveal(c, drawing)
            LogUtils.info("NeurosEnmesh: ${drawn.size()} edges " +
                    "(named=${named.size()}, judged=${proposals.size()}), ${drawing.summary()}")
            StringBuilder message = new StringBuilder(
                    "${drawn.size()} proposed relationship${drawn.size() == 1 ? '' : 's'} " +
                            "drawn dashed and grey (${named.size()} named in the text, " +
                            "${proposals.size()} judged). Accept or delete them before trusting the map.\n")
            drawn.each { MeshEdge edge ->
                def target = index.find(edge.target)
                message << '\n• ' << edge.label << ' → ' << (target?.title ?: edge.target)
            }
            message << '\n\n' << drawing.summary()
            UiHelper.showInformationMessage(ui, message.toString())
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