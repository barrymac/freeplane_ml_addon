// Push a selected mind-map branch into the neurOS substrate as a work-graph
// card (.card.md written into the watched workspace; auto-ingested; opt-in).
//
// Core Freeplane imports
import org.freeplane.core.util.LogUtils

// Standard Java/Swing imports
import javax.swing.*

// neurOS bridge imports
import com.barrymac.freeplane.addons.llm.neuros.NeurosConfig
import com.barrymac.freeplane.addons.llm.neuros.NeurosClient
import com.barrymac.freeplane.addons.llm.neuros.CardBuilder
import com.barrymac.freeplane.addons.llm.ui.UiHelper

LogUtils.info("NeurosPushCard script started.")

/** Recursive node → [text:, children:] data for CardBuilder. */
Map branchData(def n) {
    [
            text    : n.text ?: '',
            children: (n.children ?: []).collect { branchData(it) }
    ]
}

try {
    // --- Configuration gate (loud, no silent defaults) ---
    String notConfigured = NeurosConfig.missingConfiguration(config)
    if (notConfigured) {
        UiHelper.showErrorMessage(ui, notConfigured)
        return
    }
    String mountPath = NeurosConfig.getMountPath(config)
    String workspacePath = NeurosConfig.getWorkspacePath(config)
    String cardsDir = NeurosConfig.getCardsDir(config)

    // --- Selected node ---
    def node = c.selected
    if (node == null) {
        UiHelper.showInformationMessage(ui, "Please select a node first.")
        return
    }
    String title = (node.text ?: '').split('\n')[0].trim() ?: 'Untitled card'

    // --- Confirm ---
    int confirm = JOptionPane.showConfirmDialog(ui.currentFrame,
            "Push this branch to neurOS as a card?\n\n" +
                    "Title: ${title}\n" +
                    "Target: ${workspacePath}/${cardsDir}/\n" +
                    "Children: ${(node.children ?: []).size()}",
            'Push Branch as Card', JOptionPane.OK_CANCEL_OPTION)
    if (confirm != JOptionPane.OK_OPTION) {
        LogUtils.info("NeurosPushCard cancelled by user.")
        return
    }

    // --- Build & write ---
    String mapName = node.map?.title ?: 'unnamed'
    String nodeId = node.id ?: 'unknown'
    String content = CardBuilder.build(node.text ?: '', branchData(node).children, mapName, nodeId)
    String uuid = (content =~ /\[\[card:([0-9a-fA-F-]+)\]\]/)[0][1]
    String fileName = CardBuilder.fileName(uuid, title)

    File written = NeurosClient.writeCard(workspacePath, cardsDir, fileName, content)
    LogUtils.info("NeurosPushCard: wrote ${written.path}")

    // --- Wait for the substrate to ingest it ---
    Map ingest = NeurosClient.waitForIngest(mountPath, fileName, 15)

    // --- Tag the source node with the new card identity ---
    node.tags.add('neuros:card')
    String provenance = "neuros card: [[card:${uuid}]]\nfile: ${written.name}\n" +
            "ingest status: ${ingest.status}"
    node.note = (node.note ? node.note + '\n\n' : '') + provenance

    if (ingest.visible) {
        UiHelper.showInformationMessage(ui,
                "Card ingested into neurOS.\n\nuuid: ${uuid}\nstatus: ${ingest.status}\nfile: ${written.name}")
    } else {
        UiHelper.showInformationMessage(ui,
                "Card written to ${written.path}\nbut ingest had not confirmed within 15s " +
                                        "(status: ${ingest.status}).\n\n" +
                                        "The file will still be ingested — check " +
                                        "'${mountPath}/ingest-status/${fileName}' on the mount.")
    }
    LogUtils.info("NeurosPushCard finished: uuid=${uuid}, ingest=${ingest}")

} catch (Exception e) {
    LogUtils.severe("Error in NeurosPushCard script: ${e.message}", e)
    UiHelper.showErrorMessage(ui, "neurOS Push Error: ${e.message}")
}
