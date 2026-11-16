// Configure this map's neurOS knowledge connection (opt-in — every map starts
// unconnected, and no setting is inherited from another map).
//
// Core Freeplane imports
import org.freeplane.core.util.LogUtils

// Standard Java/Swing imports
import javax.swing.*
import java.awt.*

// neurOS bridge imports
import com.barrymac.freeplane.addons.llm.neuros.MapKnowledgeSettings
import com.barrymac.freeplane.addons.llm.neuros.KnowledgeSource
import com.barrymac.freeplane.addons.llm.ui.UiHelper

LogUtils.info("NeurosConfigure script started.")

try {
    def node = c.selected
    if (node == null) {
        UiHelper.showInformationMessage(ui, "Please select a node first.")
        return
    }
    def storage = node.map.storage
    MapKnowledgeSettings current = MapKnowledgeSettings.load(storage)

    // --- Build the form panel ---
    JPanel panel = new JPanel(new GridBagLayout())
    GridBagConstraints gbc = new GridBagConstraints()
    gbc.insets = new Insets(4, 4, 4, 4)
    gbc.anchor = GridBagConstraints.WEST
    gbc.fill = GridBagConstraints.HORIZONTAL

    JLabel locationLabel = new JLabel('<html>Knowledge location:</html>')
    JTextField locationField = new JTextField(current.knowledgeLocation, 40)
    JLabel scopeLabel = new JLabel('<html>Knowledge scope (chosen by you, never guessed):</html>')
    JTextField scopeField = new JTextField(current.scope, 30)
    JLabel destinationLabel = new JLabel('<html>Write destination (optional — needed for saving branches):</html>')
    JTextField destinationField = new JTextField(current.writeDestination, 40)
    JLabel cardFolderLabel = new JLabel('Card folder inside the write destination:')
    JTextField cardFolderField = new JTextField(current.cardFolder, 20)
    JLabel resultsLabel = new JLabel('Search results per question:')
    JTextField resultsField = new JTextField(String.valueOf(current.resultsLimit), 5)
    JLabel waitLabel = new JLabel('Availability wait after saving (seconds):')
    JTextField waitField = new JTextField(String.valueOf(current.writeWaitSeconds), 5)
    JCheckBox contextBox = new JCheckBox('Use map context to improve search precision',
            current.useContext)

    int row = 0
    [locationLabel, locationField, scopeLabel, scopeField,
     destinationLabel, destinationField, cardFolderLabel, cardFolderField,
     resultsLabel, resultsField, waitLabel, waitField].each { component ->
        gbc.gridx = (row % 2 == 0) ? 0 : 1
        gbc.gridy = row / 2
        panel.add(component, gbc)
        row++
    }
    gbc.gridx = 0
    gbc.gridy = row / 2
    gbc.gridwidth = 2
    panel.add(contextBox, gbc)

    Object[] options = ['Save Changes', 'Cancel']
    int choice = JOptionPane.showOptionDialog(
            ui.currentFrame, panel, 'Configure neurOS Knowledge',
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
            options, options[0])

    if (choice != 0) {
        LogUtils.info("NeurosConfigure cancelled by user.")
        return
    }

    // --- Read and validate ---
    MapKnowledgeSettings updated = new MapKnowledgeSettings(
            knowledgeLocation: locationField.text,
            scope: scopeField.text,
            writeDestination: destinationField.text,
            cardFolder: cardFolderField.text,
            resultsLimit: parseInteger(resultsField.text, 'Search results'),
            writeWaitSeconds: parseInteger(waitField.text, 'Availability wait'),
            useContext: contextBox.selected,
            ancestorLimit: current.ancestorLimit,
            childLimit: current.childLimit,
            noteLimit: current.noteLimit,
            contextLimit: current.contextLimit
    )

    List problems = []
    try {
        MapKnowledgeSettings.validate(updated)
    } catch (Exception validation) {
        problems << validation.message
    }
    if (!problems) {
        try {
            KnowledgeSource.open(updated.knowledgeLocation, updated.scope)
        } catch (Exception source) {
            problems << source.message
        }
    }
    if (problems) {
        UiHelper.showErrorMessage(ui, problems.join('\n'))
        LogUtils.warn("NeurosConfigure validation failed: ${problems.join(' | ')}")
        return
    }

    // --- Save to this map ---
    MapKnowledgeSettings.save(storage, updated)
    String saving = updated.writeDestination ?
            "${updated.writeDestination}/${updated.cardFolder}" :
            'not configured (saving branches is disabled)'
    UiHelper.showInformationMessage(ui,
            "neurOS knowledge is ready for this map.\n\n" +
                    "Knowledge scope: ${updated.scope}\n" +
                    "Search results: ${updated.resultsLimit}\n" +
                    "Map context: ${updated.useContext ? 'on' : 'off'}\n" +
                    "Saving branches: ${saving}")
    LogUtils.info("NeurosConfigure saved settings for map '${node.map.name}'.")

} catch (Exception e) {
    LogUtils.severe("Error in NeurosConfigure script: ${e.message}", e)
    UiHelper.showErrorMessage(ui, "Configure neurOS Knowledge Error: ${e.message}")
}

int parseInteger(String raw, String label) {
    String clean = (raw ?: '').trim()
    if (!clean) {
        throw new IllegalArgumentException("${label} must be a number.")
    }
    try {
        return Integer.parseInt(clean)
    } catch (NumberFormatException ignored) {
        throw new IllegalArgumentException("${label} must be a whole number.")
    }
}
