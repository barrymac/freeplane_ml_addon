package com.barrymac.freeplane.addons.llm.ui

import com.barrymac.freeplane.addons.llm.ApiConfig
import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException // Keep for potential internal UI errors if needed
import com.barrymac.freeplane.addons.llm.maps.NodeOperations // Remove - No longer used here
import com.barrymac.freeplane.addons.llm.models.MessageArea
import com.barrymac.freeplane.addons.llm.models.MessageItem
import com.barrymac.freeplane.addons.llm.prompts.MessageExpander // Remove - No longer used here
import com.barrymac.freeplane.addons.llm.prompts.MessageFileHandler // Remove - No longer used here
import com.barrymac.freeplane.addons.llm.utils.JsonUtils // Remove - No longer used here
import com.barrymac.freeplane.addons.llm.utils.UiHelper // Keep for potential internal UI messages if needed
import groovy.swing.SwingBuilder
import org.freeplane.core.util.LogUtils
import org.freeplane.core.ui.components.UITools // Keep for ui.currentFrame, setDialogLocationRelativeTo
import org.freeplane.plugin.script.proxy.ControllerProxy // Remove - No longer used here
import org.freeplane.plugin.script.FreeplaneScriptBaseClass.ConfigProperties // Remove - No longer used here

import javax.swing.*
import java.awt.*
import java.util.Hashtable
import java.util.List // Keep explicit import for method signature and internal list copies
import javax.swing.DefaultComboBoxModel
import javax.swing.JTextArea
import javax.swing.JComboBox
import javax.swing.JPasswordField
import javax.swing.JFormattedTextField
import javax.swing.JSlider
import java.awt.GridBagLayout
import java.awt.GridBagConstraints
import java.awt.FlowLayout


/**
 * Helper class for creating and managing UI dialogs
 */
class DialogHelper {
    /**
     * Shows a dialog for selecting or entering a comparison type
     *
     * @param ui The Freeplane UI object
     * @param config The Freeplane config object
     * @param contextNode The node model for context
     * @param message The message to display
     * @param defaultTypes Default comparison type options
     * @param configKey The config key to store custom types
     * @return The selected comparison type or null if cancelled
     */
    static String showComparisonDialog(UITools ui, ConfigProperties config, contextNode, String message,
                                       java.util.List<String> defaultTypes,
                                       String configKey = "promptLlmAddOn.comparisonTypes") {
        try {
            // Load previously saved custom types
            def savedTypesString = config.getProperty(configKey, '')
            def customTypes = savedTypesString ? savedTypesString.split('\\|').toList() : []
            customTypes = customTypes.findAll { !it.trim().isEmpty() } // Remove empty entries

            // Combine defaults and custom, ensuring defaults come first and no duplicates
            def allTypes = (defaultTypes + customTypes).unique()
            LogUtils.info("Showing comparison dialog with ${allTypes.size()} types (${defaultTypes.size()} defaults, ${customTypes.size()} custom)")

            def selectedType = null // Variable to store the result
            def swing = new SwingBuilder()

            // Build the dialog
            def dialog = swing.dialog(
                    title: "Define Comparison Type",
                    modal: true, // Make it modal
                    owner: ui.currentFrame, // Parent frame
                    pack: true, // Size based on content
                    locationRelativeTo: ui.currentFrame // Center on frame
            ) {
                panel(layout: new BorderLayout(5, 5), border: BorderFactory.createEmptyBorder(10, 10, 10, 10)) {
                    // Message Label
                    label(text: "<html>${message.replaceAll('\n', '<br>')}</html>", constraints: BorderLayout.NORTH)
                    // Use HTML for multi-line

                    // Editable ComboBox
                    comboBox(id: 'typeCombo',
                            items: allTypes,
                            editable: true,
                            selectedItem: allTypes.isEmpty() ? "" : allTypes[0], // Select first item or empty
                            constraints: BorderLayout.CENTER)

                    // Button Panel
                    panel(layout: new FlowLayout(FlowLayout.RIGHT), constraints: BorderLayout.SOUTH) {
                        button(text: 'OK', defaultButton: true, actionPerformed: {
                            // Get selected/entered item
                            selectedType = typeCombo.editor.item?.toString()?.trim() ?: ""
                            if (!selectedType.isEmpty()) {
                                // Check if it's a new custom type
                                if (!defaultTypes.contains(selectedType) && !customTypes.contains(selectedType)) {
                                    customTypes.add(selectedType)
                                    // Save updated custom types list
                                    config.setProperty(configKey, customTypes.join('|'))
                                    LogUtils.info("Added new comparison type: ${selectedType}")
                                }
                            }
                            // Get the button source, find its window (the dialog), and dispose it
                            SwingUtilities.getWindowAncestor(it.source).dispose()
                        })
                        button(text: 'Cancel', actionPerformed: {
                            selectedType = null // Indicate cancellation
                            // Get the button source, find its window (the dialog), and dispose it
                            SwingUtilities.getWindowAncestor(it.source).dispose()
                        })
                    }
                }
            }

            // Show the dialog (it's modal, so execution waits here)
            dialog.visible = true

            if (selectedType) {
                LogUtils.info("User selected comparison type: ${selectedType}")
            } else {
                LogUtils.info("User cancelled comparison type selection")
            }

            // Return the selected type (or null if cancelled)
            return selectedType
        } catch (Exception e) {
            LogUtils.severe("Error showing comparison dialog: ${e.message}")
            ui.errorMessage("Error showing dialog: ${e.message}")
            return null
        }
    }

    /**
     * Creates a progress dialog for long-running operations
     *
     * @param ui The Freeplane UI object
     * @param title The dialog title
     * @param message The message to display
     * @return The created dialog (not yet visible)
     */
    static JDialog createProgressDialog(UITools ui, String title, String message) {
        try {
            LogUtils.info("Creating progress dialog: ${title}")
            def swingBuilder = new SwingBuilder()
            def dialog = swingBuilder.dialog(
                    title: title,
                    owner: ui.currentFrame,
                    modal: false, // Non-modal
                    resizable: true, // Allow resizing for longer messages
                    defaultCloseOperation: WindowConstants.DO_NOTHING_ON_CLOSE) { // Prevent manual closing
                swingBuilder.panel(layout: new BorderLayout(10, 10), border: BorderFactory.createEmptyBorder(10, 10, 10, 10)) {
                    // Use text area instead of label for multi-line support
                    scrollPane(constraints: BorderLayout.CENTER) {
                        textArea(
                                text: message,
                                lineWrap: true,
                                wrapStyleWord: true,
                                editable: false,
                                margin: new Insets(5, 5, 5, 5),
                                font: new Font(Font.SANS_SERIF, Font.PLAIN, 12)
                        )
                    }
                }
            }
            dialog.pack()
            // Set minimum size to prevent overly narrow dialogs
            dialog.minimumSize = new Dimension(300, 150)
            ui.setDialogLocationRelativeTo(dialog, ui.currentFrame) // Center on frame

            return dialog
        } catch (Exception e) {
            LogUtils.severe("Error creating progress dialog: ${e.message}")
            // Return a minimal dialog in case of error
            return new JDialog(ui.currentFrame, title, false)
        }
    }

    /**
     * Helper method to create a message section (ComboBox + TextArea + Buttons)
     */
    private static MessageArea createMessageSection(def swingBuilder, def messages, def title, int initialIndex, def constraints, def weighty) {
        def comboBoxModel = new DefaultComboBoxModel()
        messages.each { comboBoxModel.addElement(new MessageItem(it)) }
        def messageComboBox, messageText
        def selectedIndex = initialIndex

        constraints.gridy++
        swingBuilder.label("${title}:", constraints: constraints)
        constraints.gridy++
        messageComboBox = swingBuilder.comboBox(model: comboBoxModel, constraints: constraints)
        // Ensure initial index is valid
        if (initialIndex >= 0 && initialIndex < messages.size()) {
            messageComboBox.selectedIndex = initialIndex
        } else if (!messages.isEmpty()) {
            messageComboBox.selectedIndex = 0 // Default to first if initial is invalid
            selectedIndex = 0
        } else {
            messageComboBox.selectedIndex = -1 // No selection if empty
            selectedIndex = -1
        }


        constraints.gridy++
        constraints.weighty = 1.0 * weighty
        swingBuilder.scrollPane(constraints: constraints) {
            // Initialize text area with the selected message or empty if none
            def initialText = (selectedIndex != -1) ? messages[selectedIndex] : ""
            messageText = swingBuilder.textArea(rows: 5 * weighty, columns: 80, tabSize: 3, lineWrap: true, wrapStyleWord: true, text: initialText, caretPosition: 0) {}
        }
        messageComboBox.addActionListener { actionEvent ->
            def newlySelectedIndex = messageComboBox.selectedIndex
            // Check if selection actually changed and is valid
            if (newlySelectedIndex != selectedIndex && newlySelectedIndex != -1) {
                // Save the text from the text area to the *previous* index in the underlying list
                if (selectedIndex != -1 && selectedIndex < messages.size()) {
                    messages[selectedIndex] = messageText.text
                    // Update the corresponding MessageItem in the ComboBox model
                    def oldItem = comboBoxModel.getElementAt(selectedIndex)
                    // Check if the item exists and its value differs from the text area
                    if (oldItem instanceof MessageItem && oldItem.value != messageText.text) {
                        // Create a new MessageItem with the updated text for display
                        def updatedDisplayItem = new MessageItem(messageText.text)
                        comboBoxModel.removeElementAt(selectedIndex)
                        comboBoxModel.insertElementAt(updatedDisplayItem, selectedIndex)
                        // Re-select the item after update
                        messageComboBox.setSelectedIndex(selectedIndex)
                    }
                }

                // Update the text area with the content of the *newly* selected item
                messageText.text = messages[newlySelectedIndex]
                messageText.caretPosition = 0
                selectedIndex = newlySelectedIndex // Update the tracked selected index
            } else if (newlySelectedIndex == -1) {
                // Handle deselection (e.g., when deleting the last item)
                messageText.text = ""
                selectedIndex = -1
            }
        }
        constraints.gridy++
        constraints.weighty = 0.0
        swingBuilder.panel(layout: new FlowLayout(), constraints: constraints) {
            button(action: swingBuilder.action(name: "Reset ${title}".toString()) {
                if (selectedIndex != -1 && selectedIndex < messages.size()) {
                    messageText.text = messages[selectedIndex]
                    messageText.caretPosition = 0
                }
            })
            button(action: swingBuilder.action(name: "Duplicate ${title}".toString()) {
                def textToDuplicate = messageText.text // Duplicate current text area content
                messages.add(textToDuplicate)
                def newItem = new MessageItem(textToDuplicate)
                comboBoxModel.addElement(newItem)
                // Select the newly added item
                messageComboBox.selectedIndex = comboBoxModel.getSize() - 1
                // selectedIndex will be updated by the action listener
            })
            button(action: swingBuilder.action(name: "Delete ${title}".toString()) {
                if (selectedIndex != -1) {
                    def indexToRemove = selectedIndex
                    messages.remove(indexToRemove)
                    comboBoxModel.removeElementAt(indexToRemove)
                    // Adjust selection if needed (select previous or first, or none if empty)
                    if (comboBoxModel.getSize() > 0) {
                        messageComboBox.selectedIndex = Math.max(0, indexToRemove - 1)
                    } else {
                        messageComboBox.selectedIndex = -1
                    }
                    // selectedIndex will be updated by the action listener based on the new selection
                }
            })
        }
        return new MessageArea(textArea: messageText, comboBox: messageComboBox)
    }


    /**
     * Shows the main dialog for interacting with the LLM via AskLm.
     * Handles UI creation and data collection, returning the result in a Map.
     */
    // --- Updated Signature: Returns Map, fewer parameters ---
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

        try {
            def swingBuilder = new SwingBuilder()
            swingBuilder.edt { // Ensure GUI runs on Event Dispatch Thread
                // Rename 'dialog' to 'askLmDialogWindow'
                // --- Assign to the pre-declared variable (removed 'def') ---
                askLmDialogWindow = swingBuilder.dialog(title: 'Chat GPT Communicator', owner: uiTools.currentFrame, modal: true) { // <<< ASSIGNED HERE
                    swingBuilder.panel(layout: new GridBagLayout()) {
                        def constraints = new GridBagConstraints()
                        constraints.fill = GridBagConstraints.BOTH
                        constraints.weightx = 1.0
                        constraints.gridx = 0
                        constraints.gridy = -1  // Will be incremented

                        // Create message sections using the helper method
                        // Pass the original List objects (they might be modified by the UI actions like duplicate/delete)
                        MessageArea systemMessageArea = createMessageSection(swingBuilder, systemMessages, "System Message", initialSystemIndex, constraints, 4)
                        MessageArea userMessageArea = createMessageSection(swingBuilder, userMessages, "User Message", initialUserIndex, constraints, 1)

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
                            def askLmButton = swingBuilder.button(action: swingBuilder.action(name: 'Prompt LLM') { actionEvent ->
                                try { // Add inner try-catch for safety during data collection
                                    // Ensure latest text area content is reflected in the underlying list before copying
                                    systemMessageArea.updateSelectedItemFromTextArea()
                                    userMessageArea.updateSelectedItemFromTextArea()

                                    // Populate resultMap with current UI state and action
                                    resultMap = [
                                            action              : 'Prompt',
                                            apiKey              : String.valueOf(apiKeyField.password),
                                            systemMessage       : systemMessageArea.textArea.text, // Current text
                                            userMessage         : userMessageArea.textArea.text,   // Current text (template)
                                            model               : gptModelBox.selectedItem,
                                            maxTokens           : responseLengthField.value,
                                            temperature         : temperatureSlider.value / 100.0,
                                            provider            : apiProviderBox.selectedItem,
                                            systemMessageIndex  : systemMessageArea.comboBox.selectedIndex, // Current index
                                            userMessageIndex    : userMessageArea.comboBox.selectedIndex,   // Current index
                                            // Return copies of the lists as they might have been modified (duplicate/delete)
                                            updatedSystemMessages: new ArrayList(systemMessages),
                                            updatedUserMessages : new ArrayList(userMessages)
                                    ]
                                    // Close the dialog
                                    SwingUtilities.getWindowAncestor(actionEvent.source).dispose()
                                } catch (Exception ex) {
                                     LogUtils.severe("Error collecting data in Prompt action: ${ex.message}", ex)
                                     resultMap = [action: 'Error', message: "Error collecting data: ${ex.message}"] // Indicate error
                                     SwingUtilities.getWindowAncestor(actionEvent.source).dispose()
                                }
                            })
                            // Set default button
                            askLmDialogWindow.rootPane.defaultButton = askLmButton

                            // --- Save Changes Button ---
                            swingBuilder.button(action: swingBuilder.action(name: 'Save Changes') { actionEvent ->
                                 try { // Add inner try-catch
                                    // Ensure latest text area content is reflected in the underlying list before copying
                                    systemMessageArea.updateSelectedItemFromTextArea()
                                    userMessageArea.updateSelectedItemFromTextArea()

                                    // Populate resultMap with relevant UI state and action
                                    resultMap = [
                                            action              : 'Save',
                                            apiKey              : String.valueOf(apiKeyField.password),
                                            model               : gptModelBox.selectedItem,
                                            maxTokens           : responseLengthField.value,
                                            temperature         : temperatureSlider.value / 100.0,
                                            provider            : apiProviderBox.selectedItem,
                                            systemMessageIndex  : systemMessageArea.comboBox.selectedIndex, // Current index
                                            userMessageIndex    : userMessageArea.comboBox.selectedIndex,   // Current index
                                            // Return copies of the lists as they might have been modified (duplicate/delete)
                                            updatedSystemMessages: new ArrayList(systemMessages),
                                            updatedUserMessages : new ArrayList(userMessages)
                                    ]
                                    // Close the dialog
                                    SwingUtilities.getWindowAncestor(actionEvent.source).dispose()
                                } catch (Exception ex) {
                                     LogUtils.severe("Error collecting data in Save action: ${ex.message}", ex)
                                     resultMap = [action: 'Error', message: "Error collecting data: ${ex.message}"] // Indicate error
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
                }
                // Pack, set minimum size, and location (now using the correctly scoped variable)
                askLmDialogWindow.pack()
                askLmDialogWindow.minimumSize = new Dimension(600, 500) // Adjust as necessary
                uiTools.setDialogLocationRelativeTo(askLmDialogWindow, uiTools.currentFrame)
                askLmDialogWindow.visible = true // Show the modal dialog (blocks until closed)
            }
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
