package com.barrymac.freeplane.addons.llm.neuros

import com.barrymac.freeplane.addons.llm.ConfigKeys
import com.barrymac.freeplane.addons.llm.ui.UiHelper
import org.freeplane.core.util.LogUtils

import javax.swing.*

/**
 * The neurOS settings page: the few options a person should be able to change
 * without editing config by hand. Everything here is persisted through ConfigKeys
 * so it survives restarts, and nothing is written into a map file.
 */
class NeurosSettingsAction {

    static void run(def c, def ui, def config) {
        LogUtils.info('NeurosSettings action started.')
        try {
            JCheckBox dryRun = new JCheckBox('Dry run only (no files written)', true)
            JTextField attestedBy = new JTextField('', 24)
            JTextField candidatesFile = new JTextField('', 32)
            JSpinner timeout = new JSpinner(new SpinnerNumberModel(20, 1, 300, 1))

            dryRun.selected = ConfigKeys.read(config, WriteBack.DRY_RUN_KEY, 'true') != 'false'
            attestedBy.text = ConfigKeys.read(config, 'llm.attestedBy', 'barrymac')
            candidatesFile.text = ConfigKeys.read(config, 'llm.neuros.candidatesFile', '')
            timeout.value = ConfigKeys.read(config, 'llm.neuros.listTimeoutSeconds', '20') as int

            JPanel panel = new JPanel(new java.awt.GridLayout(0, 1))
            panel.add(new JLabel('<html><b>neurOS settings</b></html>'))
            panel.add(dryRun)
            panel.add(new JLabel('Attested by (who confirms merges):'))
            panel.add(attestedBy)
            panel.add(new JLabel('Candidates file (optional):'))
            panel.add(candidatesFile)
            panel.add(new JLabel('Listing timeout (seconds):'))
            panel.add(timeout)
            panel.add(new JLabel('<html><i>Dry-run shows the diff without writing. ' +
                    'Turn it off only when you mean to write confirmed entities ' +
                    'into the local watched workspace.</i></html>'))

            int choice = JOptionPane.showConfirmDialog(ui.currentFrame, panel,
                    'neurOS Settings', JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
            if (choice != JOptionPane.OK_OPTION) return

            ConfigKeys.write(config, WriteBack.DRY_RUN_KEY, dryRun.selected ? 'true' : 'false')
            ConfigKeys.write(config, 'llm.attestedBy', attestedBy.text.trim() ?: 'barrymac')
            ConfigKeys.write(config, 'llm.neuros.candidatesFile', candidatesFile.text.trim())
            ConfigKeys.write(config, 'llm.neuros.listTimeoutSeconds', timeout.value.toString())
            String summary = 'Settings saved. Dry-run is ' + (dryRun.selected ? 'ON' : 'OFF')
            if (dryRun.selected) {
                summary += ' — nothing will be written.'
            } else {
                summary += ' — confirmed candidates will be written to the local watched workspace.'
            }
            UiHelper.showInformationMessage(ui, summary)
        } catch (Exception problem) {
            LogUtils.severe("Error in NeurosSettings action: ${problem.message}", problem)
            UiHelper.showErrorMessage(ui, "neurOS Settings Error: ${problem.message}")
        }
    }
}