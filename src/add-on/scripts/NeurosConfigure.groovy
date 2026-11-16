// neurOS bridge configuration dialog (opt-in — nothing is configured by default).
//
// Core Freeplane imports
import org.freeplane.core.util.LogUtils

// Standard Java/Swing imports
import javax.swing.*
import java.awt.*

// neurOS bridge imports
import com.barrymac.freeplane.addons.llm.neuros.NeurosConfig
import com.barrymac.freeplane.addons.llm.ui.UiHelper

LogUtils.info("NeurosConfigure script started.")

try {
    // --- Load current values ---
    String mountPath = NeurosConfig.getMountPath(config)
    String workspacePath = NeurosConfig.getWorkspacePath(config)
    String resultsLimit = String.valueOf(NeurosConfig.getResultsLimit(config))
    String cardsDir = NeurosConfig.getCardsDir(config)

    // --- Build the form panel ---
    JPanel panel = new JPanel(new GridBagLayout())
    GridBagConstraints gbc = new GridBagConstraints()
    gbc.insets = new Insets(4, 4, 4, 4)
    gbc.anchor = GridBagConstraints.WEST
    gbc.fill = GridBagConstraints.HORIZONTAL

    JLabel mountLabel = new JLabel('<html>neurOS mount path (FUSE root, e.g. /mnt/neuros/software/&lt;scope&gt;):</html>')
    JTextField mountField = new JTextField(mountPath, 40)
    JLabel workspaceLabel = new JLabel('<html>Watched workspace path (where cards are written):</html>')
    JTextField workspaceField = new JTextField(workspacePath, 40)
    JLabel limitLabel = new JLabel('Search results limit:')
    JTextField limitField = new JTextField(resultsLimit, 5)
    JLabel cardsLabel = new JLabel('Card directory (relative to workspace):')
    JTextField cardsField = new JTextField(cardsDir, 20)

    gbc.gridx = 0; gbc.gridy = 0; panel.add(mountLabel, gbc)
    gbc.gridx = 1; gbc.gridy = 0; panel.add(mountField, gbc)
    gbc.gridx = 0; gbc.gridy = 1; panel.add(workspaceLabel, gbc)
    gbc.gridx = 1; gbc.gridy = 1; panel.add(workspaceField, gbc)
    gbc.gridx = 0; gbc.gridy = 2; panel.add(limitLabel, gbc)
    gbc.gridx = 1; gbc.gridy = 2; panel.add(limitField, gbc)
    gbc.gridx = 0; gbc.gridy = 3; panel.add(cardsLabel, gbc)
    gbc.gridx = 1; gbc.gridy = 3; panel.add(cardsField, gbc)

    Object[] options = ['Save Changes', 'Cancel']
    int choice = JOptionPane.showOptionDialog(
            ui.currentFrame, panel, 'Configure neurOS bridge',
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
            options, options[0])

    if (choice != 0) {
        LogUtils.info("NeurosConfigure cancelled by user.")
        return
    }

    // --- Validate ---
    String newMount = mountField.text.trim()
    String newWorkspace = workspaceField.text.trim()
    String newLimit = limitField.text.trim()
    String newCards = cardsField.text.trim()

    List problems = []
    if (newMount && !(new File(newMount, 'search').exists() || new File(newMount, 'README.cartridge').exists())) {
        problems << "'${newMount}' does not look like a live neurOS mount (no search/ or README.cartridge)."
    }
    if (newWorkspace && !new File(newWorkspace).isDirectory()) {
        problems << "Workspace '${newWorkspace}' is not an existing directory."
    }
    if (newLimit && !(newLimit ==~ /\d+/)) {
        problems << "Results limit must be a number."
    }
    if (problems) {
        UiHelper.showErrorMessage(ui, problems.join('\n'))
        LogUtils.warn("NeurosConfigure validation failed: ${problems.join(' | ')}")
        return
    }

    // --- Save ---
    NeurosConfig.save(config, newMount, newWorkspace, newLimit, newCards)
    UiHelper.showInformationMessage(ui,
            "neurOS bridge saved.\n\nMount: ${newMount ?: '(not set — read features disabled)'}\n" +
                    "Workspace: ${newWorkspace ?: '(not set — push disabled)'}")
    LogUtils.info("NeurosConfigure saved: mount=${newMount}, workspace=${newWorkspace}")

} catch (Exception e) {
    LogUtils.severe("Error in NeurosConfigure script: ${e.message}", e)
    UiHelper.showErrorMessage(ui, "Configure neurOS Error: ${e.message}")
}
