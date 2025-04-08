package com.barrymac.freeplane.addons.llm.ui

import com.barrymac.freeplane.addons.llm.ApiConfig
import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException
import com.barrymac.freeplane.addons.llm.maps.NodeOperations
import com.barrymac.freeplane.addons.llm.models.MessageArea
import com.barrymac.freeplane.addons.llm.models.MessageItem
import com.barrymac.freeplane.addons.llm.prompts.MessageExpander
import com.barrymac.freeplane.addons.llm.prompts.MessageFileHandler
import com.barrymac.freeplane.addons.llm.utils.JsonUtils
import com.barrymac.freeplane.addons.llm.utils.UiHelper
import groovy.swing.SwingBuilder
import org.freeplane.core.util.LogUtils

import javax.swing.*
import java.awt.*
import java.util.Hashtable
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
    static String showComparisonDialog(ui, config, contextNode, String message,
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
    static JDialog createProgressDialog(ui, String title, String message) {
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
     * Shows the main dialog for interacting with the LLM via AskGpt.
     * Handles UI creation, event handling, API calls, and configuration saving.
     */
    static void showAskGptDialog(
            Object ui,
            Object config,
            Object c, // Controller for selected node access (c.selected)
            ApiConfig apiConfig,
            List systemMessages,
            List userMessages,
            int initialSystemIndex,
            int initialUserIndex,
            String systemMessagesFilePath,
            String userMessagesFilePath,
            Closure make_api_call,
            Closure tagWithModel
    ) {
        try {
            def swingBuilder = new SwingBuilder()
            swingBuilder.edt { // Ensure GUI runs on Event Dispatch Thread
                def dialog = swingBuilder.dialog(title: 'Chat GPT Communicator', owner: ui.currentFrame, modal: true) { // Make modal
                    swingBuilder.panel(layout: new GridBagLayout()) {
                        def constraints = new GridBagConstraints()
                        constraints.fill = GridBagConstraints.BOTH
                        constraints.weightx = 1.0
                        constraints.gridx = 0
                        constraints.gridy = -1  // Will be incremented

                        // Create message sections using the helper method
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
                            def askGptButton = swingBuilder.button(action: swingBuilder.action(name: 'Prompt LLM') { actionEvent ->
                                try {
                                    // 1. Get current values from GUI
                                    def currentApiKey = String.valueOf(apiKeyField.password)
                                    def currentSystemMessage = systemMessageArea.textArea.text
                                    def currentUserMessageTemplate = userMessageArea.textArea.text
                                    def currentModel = gptModelBox.selectedItem
                                    def currentMaxTokens = responseLengthField.value
                                    def currentTemperature = temperatureSlider.value / 100.0
                                    def currentProvider = apiProviderBox.selectedItem

                                    // 2. Get selected node (using passed controller 'c')
                                    def node = c.selected
                                    if (node == null) {
                                        UiHelper.showInformationMessage(ui, "Please select a node first.")
                                        return
                                    }

                                    // 3. Expand user message template
                                    def expandedUserMessage = MessageExpander.expandTemplate(
                                            currentUserMessageTemplate,
                                            MessageExpander.createBinding(node, null, null, null, null)
                                    )

                                    // 4. Prepare API Payload Map
                                    def messagesList = [
                                            [role: 'system', content: currentSystemMessage],
                                            [role: 'user', content: expandedUserMessage]
                                    ]
                                    Map<String, Object> payload = [
                                            'model'      : currentModel,
                                            'messages'   : messagesList,
                                            'temperature': currentTemperature,
                                            'max_tokens' : currentMaxTokens,
                                            'response_format': (currentProvider == 'openai' && currentModel.contains("gpt")) ? [type: "text"] : null
                                    ].findAll { key, value -> value != null }

                                    LogUtils.info("AskGpt Dialog: Sending payload: ${payload}")

                                    // 5. Call API (using passed closure)
                                    // Consider showing a progress indicator here
                                    def progressDialog = DialogHelper.createProgressDialog(ui, "LLM Request", "Sending prompt to ${currentModel}...")
                                    progressDialog.visible = true
                                    def rawApiResponse
                                    try {
                                        rawApiResponse = make_api_call(currentProvider, currentApiKey, payload)
                                    } finally {
                                        progressDialog.dispose() // Ensure dialog closes
                                    }


                                    if (rawApiResponse == null || rawApiResponse.isEmpty()) {
                                        throw new LlmAddonException("Received empty or null response from API.")
                                    }

                                    // 6. Process Response
                                    def responseContent = JsonUtils.extractLlmContent(rawApiResponse)
                                    LogUtils.info("AskGpt Dialog: Received response content:\n${responseContent}")

                                    // 7. Update Map (using NodeOperations and passed tagger)
                                    NodeOperations.addAnalysisBranch(
                                            node,                   // Parent node
                                            null,                   // No analysis map
                                            responseContent,        // The raw text content
                                            currentModel,           // Model used
                                            tagWithModel,           // Tagger function (passed closure)
                                            "LLM Prompt Result"     // Optional type string
                                    )

                                    UiHelper.showInformationMessage(ui, "Response added as a new branch.")
                                    // Optionally close dialog after successful prompt
                                    // SwingUtilities.getWindowAncestor(actionEvent.source).dispose()

                                } catch (Exception ex) {
                                    LogUtils.severe("Error during 'Prompt LLM' action: ${ex.message}", ex)
                                    UiHelper.showErrorMessage(ui, "Prompt LLM Error: ${ex.message.split('\n').head()}")
                                }
                            })
                            // Set default button
                            dialog.rootPane.defaultButton = askGptButton

                            // --- Save Changes Button ---
                            swingBuilder.button(action: swingBuilder.action(name: 'Save Changes') { actionEvent ->
                                try {
                                    // Ensure the current text area content is saved to the list before saving files/config
                                    systemMessageArea.updateSelectedItemFromTextArea() // Use helper method
                                    userMessageArea.updateSelectedItemFromTextArea()   // Use helper method

                                    // Save messages to files (using passed paths)
                                    MessageFileHandler.saveMessagesToFile(systemMessagesFilePath, systemMessages)
                                    MessageFileHandler.saveMessagesToFile(userMessagesFilePath, userMessages)

                                    // Save configuration properties (using passed config object)
                                    config.setProperty('openai.key', String.valueOf(apiKeyField.password))
                                    config.setProperty('openai.gpt_model', gptModelBox.selectedItem)
                                    config.setProperty('openai.max_response_length', responseLengthField.value)
                                    config.setProperty('openai.temperature', temperatureSlider.value / 100.0)
                                    config.setProperty('openai.system_message_index', systemMessageArea.comboBox.selectedIndex)
                                    config.setProperty('openai.user_message_index', userMessageArea.comboBox.selectedIndex)
                                    config.setProperty('openai.api_provider', apiProviderBox.selectedItem)

                                    UiHelper.showInformationMessage(ui, "Changes saved.") // Provide feedback
                                } catch (Exception ex) {
                                    LogUtils.severe("Error during 'Save Changes' action: ${ex.message}", ex)
                                    UiHelper.showErrorMessage(ui, "Save Error: ${ex.message.split('\n').head()}")
                                }
                            })

                            // --- Close Button ---
                            swingBuilder.button(text: 'Close', actionPerformed: { actionEvent ->
                                // Get the button source, find its window (the dialog), and dispose it
                                SwingUtilities.getWindowAncestor(actionEvent.source).dispose()
                            })
                        }
                    }
                }
                dialog.pack()
                // Adjust minimum size if needed
                dialog.minimumSize = new Dimension(600, 500) // Adjust as necessary
                ui.setDialogLocationRelativeTo(dialog, ui.currentFrame)
                dialog.visible = true // Show the modal dialog
            }
        } catch (Exception e) {
            LogUtils.severe("Error showing AskGpt dialog: ${e.message}", e)
            UiHelper.showErrorMessage(ui, "Dialog Error: ${e.message.split('\n').head()}")
        }
    }
}
