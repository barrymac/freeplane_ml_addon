// Semantic search of a neurOS mount from a selected node (opt-in bridge).
//
// Core Freeplane imports
import org.freeplane.core.util.LogUtils

// Standard Java/Swing imports
import javax.swing.*

// neurOS bridge imports
import com.barrymac.freeplane.addons.llm.neuros.NeurosConfig
import com.barrymac.freeplane.addons.llm.neuros.NeurosClient
import com.barrymac.freeplane.addons.llm.ui.UiHelper

LogUtils.info("NeurosSearch script started.")

try {
    // --- Configuration gate (loud, no silent defaults) ---
    String notConfigured = NeurosConfig.missingConfiguration(config)
    if (notConfigured) {
        UiHelper.showErrorMessage(ui, notConfigured)
        return
    }
    String mountPath = NeurosConfig.getMountPath(config)
    int limit = NeurosConfig.getResultsLimit(config)

    // --- Selected node & query ---
    def node = c.selected
    if (node == null) {
        UiHelper.showInformationMessage(ui, "Please select a node first.")
        return
    }

    String initialQuery = node.text?.trim() ?: ''
    String query = JOptionPane.showInputDialog(ui.currentFrame,
            'neurOS semantic search — query:', 'Search neurOS knowledge',
            JOptionPane.QUESTION_MESSAGE, null, null, initialQuery)
    if (query == null) return           // user cancelled
    if (!query.trim()) {
        UiHelper.showInformationMessage(ui, "Empty query — nothing to search.")
        return
    }
    query = query.trim()

    // --- Search ---
    LogUtils.info("NeurosSearch: querying mount ${mountPath} with: ${query}")
    List hits = NeurosClient.search(mountPath, query, limit)

    if (hits.isEmpty()) {
        UiHelper.showInformationMessage(ui,
                "No results for '${query}'.\n\n" +
                        "(An empty answer right after mount setup usually means the semantic " +
                                        "projections are still warming — retry in a minute.)")
        return
    }

    // --- Attach results as a branch ---
    def parentNode = node.createChild("neurOS results: ${query}")
    parentNode.tags.add('neuros:search')
    parentNode.note = "neurOS search on ${new Date()}\nquery: ${query}\nmount: ${mountPath}"

    hits.each { hit ->
        def hitNode = parentNode.createChild(hit.displayName ?: hit.uuid)
        hitNode.tags.add('neuros:hit')
        hitNode.note = "neuros uuid: ${hit.uuid}\nsubtype: ${hit.subtype}\n" +
                "read on the mount: ${mountPath}/by-id/${hit.uuid}"
    }

    LogUtils.info("NeurosSearch: attached ${hits.size()} result(s) for '${query}'.")
    UiHelper.showInformationMessage(ui, "Attached ${hits.size()} neurOS result(s) under the selected node.")

} catch (Exception e) {
    LogUtils.severe("Error in NeurosSearch script: ${e.message}", e)
    UiHelper.showErrorMessage(ui, "neurOS Search Error: ${e.message}")
}
