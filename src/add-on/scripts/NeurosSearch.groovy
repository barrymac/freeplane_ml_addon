// Search the map's connected neurOS knowledge scope. One context-rich query,
// one bounded result branch — no UUIDs or implementation details in the UI.
//
// Core Freeplane imports
import org.freeplane.core.util.LogUtils

// Standard Java/Swing imports
import java.util.List
import javax.swing.*
import javax.swing.event.*
import java.awt.*

// neurOS bridge imports
import com.barrymac.freeplane.addons.llm.neuros.MapKnowledgeSettings
import com.barrymac.freeplane.addons.llm.neuros.KnowledgeSource
import com.barrymac.freeplane.addons.llm.neuros.NeurosClient
import com.barrymac.freeplane.addons.llm.neuros.SearchContextBuilder
import com.barrymac.freeplane.addons.llm.ui.UiHelper

LogUtils.info("NeurosSearch script started.")

try {
    // --- This map's connection and scope ---
    MapKnowledgeSettings settings = MapKnowledgeSettings.load(c.selected?.map?.storage)
    String readProblem = settings.missingForRead()
    if (readProblem) {
        UiHelper.showErrorMessage(ui, readProblem)
        return
    }
    KnowledgeSource source = settings.openSource()

    // --- Selected node ---
    def node = c.selected
    if (node == null) {
        UiHelper.showInformationMessage(ui, "Please select a node first.")
        return
    }

    // --- Bounded map context (one query, never one query per neighbour) ---
    List<String> ancestors = []
    if (settings.ancestorLimit > 0) {
        def parent = node.parent
        while (parent != null && ancestors.size() < settings.ancestorLimit) {
            ancestors.add(0, SearchContextBuilder.titleFragment(parent.text ?: ''))
            parent = parent.parent
        }
    }
    List<String> children = (node.children ?: [])
            .findAll { !isGeneratedResult(it) }
            .take(settings.childLimit)
            .collect { SearchContextBuilder.titleFragment(it.text ?: '') }
    String note = node.note ? node.note.plain : ''

    String initialQuestion = (node.text ?: '').trim()
    JTextField questionField = new JTextField(initialQuestion, 42)
    JCheckBox contextBox = new JCheckBox('Include map context for greater precision',
            settings.useContext)
    JTextArea preview = new JTextArea(7, 46)
    preview.editable = false
    preview.lineWrap = true
    preview.wrapStyleWord = true

    Closure composeQuery = { ->
        if (!contextBox.selected) {
            return questionField.text.trim()
        }
        return SearchContextBuilder.build(questionField.text, ancestors, children, note,
                settings.contextOptions())
    }
    Closure refreshPreview = { ->
        try {
            preview.text = composeQuery()
        } catch (Exception previewProblem) {
            preview.text = previewProblem.message
        }
    }
    questionField.document.addDocumentListener(new DocumentListener() {
        void insertUpdate(DocumentEvent e) { refreshPreview() }
        void removeUpdate(DocumentEvent e) { refreshPreview() }
        void changedUpdate(DocumentEvent e) { refreshPreview() }
    })
    contextBox.addActionListener { refreshPreview() }
    refreshPreview()

    JPanel panel = new JPanel(new GridBagLayout())
    GridBagConstraints gbc = new GridBagConstraints()
    gbc.insets = new Insets(4, 4, 4, 4)
    gbc.anchor = GridBagConstraints.WEST
    gbc.fill = GridBagConstraints.HORIZONTAL
    gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2
    panel.add(new JLabel('Question:'), gbc)
    gbc.gridy = 1
    panel.add(questionField, gbc)
    gbc.gridy = 2
    panel.add(contextBox, gbc)
    gbc.gridy = 3
    panel.add(new JLabel('Exactly what will be searched:'), gbc)
    gbc.gridy = 4; gbc.fill = GridBagConstraints.BOTH
    panel.add(new JScrollPane(preview), gbc)
    gbc.gridy = 5; gbc.gridwidth = 2
    panel.add(new JLabel(source.description()), gbc)

    Object[] options = ['Search', 'Cancel']
    int choice = JOptionPane.showOptionDialog(
            ui.currentFrame, panel, "Search neurOS Knowledge",
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
            options, options[0])
    if (choice != 0) {
        LogUtils.info("NeurosSearch cancelled by user.")
        return
    }

    String question
    try {
        question = composeQuery()
    } catch (Exception questionProblem) {
        UiHelper.showErrorMessage(ui, questionProblem.message)
        return
    }
    if (!question?.trim()) {
        UiHelper.showInformationMessage(ui, "The question is empty — nothing to search.")
        return
    }

    // --- One search, current scope only ---
    LogUtils.info("NeurosSearch: searching current knowledge scope with a context-rich query.")
    List hits = NeurosClient.search(source, question, settings.resultsLimit)

    if (hits.isEmpty()) {
        UiHelper.showInformationMessage(ui,
                "No results for this question.\n\n" +
                        "(An empty answer right after connecting usually means the knowledge " +
                                        "source is still preparing its search index — retry in a minute.)")
        return
    }

    // --- Attach results as a human-readable branch ---
    String branchTitle = SearchContextBuilder.titleFragment(question) ?: 'neurOS results'
    def parentNode = node.createChild("neurOS results: ${branchTitle}")
    parentNode.tags.add('neurOS search')
    parentNode.note = "Searched ${new Date()}\n" +
            "Question: ${question}\n" +
            "${source.description()}\n" +
            "Map context: ${contextBox.selected ? 'included' : 'not included'}"

    hits.each { hit ->
        String title = hit.displayName?.trim() ?: "Untitled ${hit.subtype ?: 'knowledge'}"
        def hitNode = parentNode.createChild(title)
        hitNode.tags.add('neurOS result')
        hitNode.note = "Type: ${hit.subtype ?: 'unknown'}\n" +
                "${source.description()}\n" +
                'Use the link on this node to open the source material.'
        hitNode.link.file = source.byIdPath(hit.uuid)
    }

    LogUtils.info("NeurosSearch: attached ${hits.size()} result(s).")
    UiHelper.showInformationMessage(ui,
            "Added ${hits.size()} result(s) from the map's knowledge scope.")

} catch (Exception e) {
    LogUtils.severe("Error in NeurosSearch script: ${e.message}", e)
    UiHelper.showErrorMessage(ui, "Search neurOS Knowledge Error: ${e.message}")
}

/** Previous search result branches must never feed back into later queries. */
boolean isGeneratedResult(def candidate) {
    def tags = candidate?.tags?.tags ?: []
    return tags.any {
        it == 'neuros:search' || it == 'neuros:hit' ||
                it == 'neurOS search' || it == 'neurOS result'
    }
}
