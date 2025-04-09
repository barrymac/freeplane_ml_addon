package com.barrymac.freeplane.addons.llm.ui

import com.barrymac.freeplane.addons.llm.ApiConfig
import com.barrymac.freeplane.addons.llm.models.MessageArea
import groovy.swing.SwingBuilder
import org.freeplane.core.ui.components.UITools
import org.freeplane.core.util.LogUtils

import javax.swing.*
import java.awt.*
import java.util.List

class AskLmDialogueHelper {


    /**
     * Shows the main dialog for interacting with the LLM via AskLm.
     * Handles UI creation and data collection, returning the result in a Map.
     */
    static Map showAskLmDialog(
            Object ui,                     // Keep as Object for flexibility from script
            ApiConfig apiConfig,
            List systemMessages,           // Keep for display/modification
            List userMessages,             // Keep for display/modification
            int initialSystemIndex,
            int initialUserIndex
            // Removed: config, c, file paths, make_api_call, tagWithModel
    ) {
        // Cast ui to UITools for checks and subsequent calls
        def uiTools = ui as UITools
        // Null check for parent frame should happen in the calling script

        Map resultMap = null // Initialize result map
        def askLmDialogWindow // <<< DECLARED HERE
        def askLmButton       // <<< DECLARED HERE

        try {
            def swingBuilder = new SwingBuilder()
            swingBuilder.edt { // Ensure GUI runs on Event Dispatch Thread
                // Rename 'dialog' to 'askLmDialogWindow'
                // --- Assign to the pre-declared variable (removed 'def') ---
                askLmDialogWindow = swingBuilder.dialog(title: 'Chat GPT Communicator', owner: uiTools.currentFrame, modal: true) {
                    // <<< ASSIGNED HERE
                    swingBuilder.panel(layout: new GridBagLayout()) {
                        def constraints = new GridBagConstraints()
                        constraints.fill = GridBagConstraints.BOTH
                        constraints.weightx = 1.0
                        constraints.gridx = 0
                        constraints.gridy = -1  // Will be incremented

                        // Create message sections using the helper method
                        // Pass the original List objects (they might be modified by the UI actions like duplicate/delete)
                        MessageArea systemMessageArea = DialogHelper.createMessageSection(swingBuilder, systemMessages, "System Message", initialSystemIndex, constraints, 4)
                        MessageArea userMessageArea = DialogHelper.createMessageSection(swingBuilder, userMessages, "User Message", initialUserIndex, constraints, 1)

                        constraints.gridy++
                        JPasswordField apiKeyField
                        JFormattedTextField responseLengthField
                        JComboBox gptModelBox
                        JSlider temperatureSlider
                        JComboBox apiProviderBox

                        swingBuilder.panel(constraints: constraints, layout: new GridBagLayout()) {
                            def cPanel = new GridBagConstraints()
                            cPanel.fill = GridBagConstraints.HORIZONTAL // Use HORIZONTAL fill for controls
                            cPanel.weightx = 1.0
                            cPanel.insets = new Insets(2, 2, 2, 2) // Add some padding
                            cPanel.gridy = 0

                            cPanel.gridx = 0
                            swingBuilder.panel(constraints: cPanel, layout: new BorderLayout(), border: BorderFactory.createTitledBorder('API Key')) {
                                apiKeyField = swingBuilder.passwordField(columns: 10, text: apiConfig.apiKey)
                            }
                            cPanel.gridx++
                            swingBuilder.panel(constraints: cPanel, layout: new BorderLayout(), border: BorderFactory.createTitledBorder('Max Tokens')) {
                                responseLengthField = swingBuilder.formattedTextField(columns: 5, value: apiConfig.maxTokens)
                            }
                            cPanel.gridx++
                            swingBuilder.panel(constraints: cPanel, layout: new BorderLayout(), border: BorderFactory.createTitledBorder('Model')) {
                                gptModelBox = swingBuilder.comboBox(
                                        items: apiConfig.availableModels,
                                        selectedItem: apiConfig.model,
                                        prototypeDisplayValue: apiConfig.availableModels.max { it.length() } ?: "DefaultModelName" // Provide prototype
                                )
                            }
                            cPanel.gridx++
                            swingBuilder.panel(constraints: cPanel, layout: new BorderLayout(), border: BorderFactory.createTitledBorder('Provider')) {
                                apiProviderBox = swingBuilder.comboBox(items: ['openai', 'openrouter'], selectedItem: apiConfig.provider)
                            }
                            cPanel.gridx++
                            cPanel.gridwidth = 2 // Make slider span 2 columns if needed
                            swingBuilder.panel(constraints: cPanel, layout: new BorderLayout(), border: BorderFactory.createTitledBorder('Temperature')) {
                                temperatureSlider = swingBuilder.slider(minimum: 0, maximum: 100, minorTickSpacing: 10, majorTickSpacing: 50, snapToTicks: false, // Allow finer control
                                        paintTicks: true, paintLabels: true, value: (int) (apiConfig.temperature * 100.0 + 0.5))
                                // Add labels for 0.0 and 1.0
                                def labels = new Hashtable()
                                labels.put(0, new JLabel("0.0"))
                                labels.put(100, new JLabel("1.0"))
                                temperatureSlider.setLabelTable(labels)
                            }
                        }
                        constraints.gridy++
                        constraints.fill = GridBagConstraints.NONE // Don't stretch buttons
                        constraints.anchor = GridBagConstraints.EAST // Align buttons to the right
                        swingBuilder.panel(constraints: constraints, layout: new FlowLayout(FlowLayout.RIGHT)) {
                            // --- Prompt LLM Button ---
                            // Assign to outer variable (removed 'def')
                            askLmButton = swingBuilder.button(action: swingBuilder.action(name: 'Prompt LLM') { actionEvent -> // <<< ASSIGN HERE
                                try { // Add inner try-catch for safety during data collection
                                    // Ensure latest text area content is reflected in the underlying list before copying
                                    systemMessageArea.updateSelectedItemFromTextArea()
                                    userMessageArea.updateSelectedItemFromTextArea()

                                    // Populate resultMap with current UI state and action
                                    resultMap = [
                                            action               : 'Prompt',
                                            apiKey               : String.valueOf(apiKeyField.password),
                                            systemMessage        : systemMessageArea.textArea.text, // Current text
                                            userMessage          : userMessageArea.textArea.text,   // Current text (template)
                                            model                : gptModelBox.selectedItem,
                                            maxTokens            : responseLengthField.value,
                                            temperature          : temperatureSlider.value / 100.0,
                                            provider             : apiProviderBox.selectedItem,
                                            systemMessageIndex   : systemMessageArea.comboBox.selectedIndex, // Current index
                                            userMessageIndex     : userMessageArea.comboBox.selectedIndex,   // Current index
                                            // Return copies of the lists as they might have been modified (duplicate/delete)
                                            updatedSystemMessages: new ArrayList(systemMessages),
                                            updatedUserMessages  : new ArrayList(userMessages)
                                    ]
                                    // Close the dialog
                                    SwingUtilities.getWindowAncestor(actionEvent.source).dispose()
                                } catch (Exception ex) {
                                    LogUtils.severe("Error collecting data in Prompt action: ${ex.message}", ex)
                                    resultMap = [action: 'Error', message: "Error collecting data: ${ex.message}"]
                                    // Indicate error
                                    SwingUtilities.getWindowAncestor(actionEvent.source).dispose()
                                }
                            })
                            // --- REMOVED default button assignment from here ---

                            // --- Save Changes Button ---
                            swingBuilder.button(action: swingBuilder.action(name: 'Save Changes') { actionEvent ->
                                try { // Add inner try-catch
                                    // Ensure latest text area content is reflected in the underlying list before copying
                                    systemMessageArea.updateSelectedItemFromTextArea()
                                    userMessageArea.updateSelectedItemFromTextArea()

                                    // Populate resultMap with relevant UI state and action
                                    resultMap = [
                                            action               : 'Save',
                                            apiKey               : String.valueOf(apiKeyField.password),
                                            model                : gptModelBox.selectedItem,
                                            maxTokens            : responseLengthField.value,
                                            temperature          : temperatureSlider.value / 100.0,
                                            provider             : apiProviderBox.selectedItem,
                                            systemMessageIndex   : systemMessageArea.comboBox.selectedIndex, // Current index
                                            userMessageIndex     : userMessageArea.comboBox.selectedIndex,   // Current index
                                            // Return copies of the lists as they might have been modified (duplicate/delete)
                                            updatedSystemMessages: new ArrayList(systemMessages),
                                            updatedUserMessages  : new ArrayList(userMessages)
                                    ]
                                    // Close the dialog
                                    SwingUtilities.getWindowAncestor(actionEvent.source).dispose()
                                } catch (Exception ex) {
                                    LogUtils.severe("Error collecting data in Save action: ${ex.message}", ex)
                                    resultMap = [action: 'Error', message: "Error collecting data: ${ex.message}"]
                                    // Indicate error
                                    SwingUtilities.getWindowAncestor(actionEvent.source).dispose()
                                }
                            })

                            // --- Close Button ---
                            swingBuilder.button(text: 'Close', actionPerformed: { actionEvent ->
                                // Populate resultMap with close action
                                resultMap = [action: 'Close']
                                // Close the dialog
                                SwingUtilities.getWindowAncestor(actionEvent.source).dispose()
                            })
                        }
                    }
                } // End dialog definition closure

                // --- Set the default button AFTER the dialog is created ---
                if (askLmDialogWindow != null && askLmButton != null) { // Add null checks for safety
                    askLmDialogWindow.rootPane.defaultButton = askLmButton // <<< SET DEFAULT BUTTON HERE
                } else {
                    LogUtils.warn("Could not set default button: dialog or button was null.")
                }

                // Pack, set minimum size, and location (now using the correctly scoped variable)
                askLmDialogWindow.pack()
                askLmDialogWindow.minimumSize = new Dimension(600, 500) // Adjust as necessary
                uiTools.setDialogLocationRelativeTo(askLmDialogWindow, uiTools.currentFrame)
                askLmDialogWindow.visible = true // Show the modal dialog (blocks until closed)
            } // End edt block
        } catch (Exception e) {
            LogUtils.severe("Error showing askLm dialog: ${e.message}", e)
            // Cannot use UiHelper here reliably if uiTools caused the error
            // Return an error indicator map
            return [action: 'Error', message: "Dialog creation failed: ${e.message.split('\n').head()}"]
        }

        // Return the map populated by button actions (or error map)
        return resultMap
    }
}
