// Save a selected mind-map branch to the map's configured neurOS write
// destination as a work-graph card. The user confirms the destination first;
// identifiers and transport details stay out of the interface.
//
// Core Freeplane imports
import org.freeplane.core.util.LogUtils

// Standard Java/Swing imports
import javax.swing.*

// neurOS bridge imports
import com.barrymac.freeplane.addons.llm.neuros.MapKnowledgeSettings
import com.barrymac.freeplane.addons.llm.neuros.KnowledgeSource
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
    // --- This map's connection, scope and write destination ---
    def node = c.selected
    if (node == null) {
        UiHelper.showInformationMessage(ui, "Please select a node first.")
        return
    }
    MapKnowledgeSettings settings = MapKnowledgeSettings.load(node.map.storage)

    String readProblem = settings.missingForRead()
    if (readProblem) {
        UiHelper.showErrorMessage(ui, readProblem)
        return
    }
    String writeProblem = settings.missingForWrite()
    if (writeProblem) {
        UiHelper.showErrorMessage(ui, writeProblem)
        return
    }
    KnowledgeSource source = settings.openSource()

    String title = (node.text ?: '').split('\n')[0].trim() ?: 'Untitled card'
    String destinationName = new File(settings.writeDestination).name

    // --- Confirm the exact save ---
    int confirm = JOptionPane.showConfirmDialog(ui.currentFrame,
            "Save this branch to neurOS?\n\n" +
                    "Title: ${title}\n" +
                    "${source.description()}\n" +
                    "Destination: ${destinationName}/${settings.cardFolder}\n" +
                    "Children: ${(node.children ?: []).size()}",
            'Save Branch to neurOS', JOptionPane.OK_CANCEL_OPTION)
    if (confirm != JOptionPane.OK_OPTION) {
        LogUtils.info("NeurosPushCard cancelled by user.")
        return
    }

    // --- Build & write ---
    String mapName = node.map?.name ?: 'unnamed'
    String nodeId = node.id ?: 'unknown'
    String content = CardBuilder.build(node.text ?: '', branchData(node).children, mapName, nodeId)
    String uuid = (content =~ /\[\[card:([0-9a-fA-F-]+)\]\]/)[0][1]
    String fileName = CardBuilder.fileName(uuid, title)

    File written = NeurosClient.writeCard(settings.writeDestination,
            settings.cardFolder, fileName, content)
    LogUtils.info("NeurosPushCard: wrote a card to the configured destination.")

    // --- Confirm using whatever availability surface the source provides ---
    Map confirmation = NeurosClient.confirmWrite(source, written, settings.writeWaitSeconds)

    // --- Mark the source node without exposing identifiers ---
    node.tags.add('neurOS card')
    String availability = confirmation.available ?
            'available in neurOS' :
            'waiting for the knowledge source to confirm availability'
    node.note = (node.note ? node.note + '\n\n' : '') +
            "Saved to neurOS\nTitle: ${title}\n${source.description()}\nStatus: ${availability}"

    // The link carries the source location without showing an identifier.
    node.link.file = source.byIdPath(uuid)

    if (confirmation.available) {
        UiHelper.showInformationMessage(ui,
                "Saved and available in neurOS.\n\nTitle: ${title}\n${source.description()}")
    } else {
        UiHelper.showInformationMessage(ui,
                "Saved to your neurOS write destination.\n\n" +
                        "The knowledge source has not confirmed availability yet — " +
                        'you can retry or open the source later.')
    }
    LogUtils.info("NeurosPushCard finished: status=${confirmation.status}.")

} catch (Exception e) {
    LogUtils.severe("Error in NeurosPushCard script: ${e.message}", e)
    UiHelper.showErrorMessage(ui, "Save to neurOS Error: ${e.message}")
}
