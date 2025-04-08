import com.barrymac.freeplane.addons.llm.exceptions.*
import groovy.swing.SwingBuilder

import javax.swing.*
import java.awt.*

import com.barrymac.freeplane.addons.llm.exceptions.*
import groovy.swing.SwingBuilder
import javax.swing.*
import java.awt.*

// Core LLM Addon Classes
import com.barrymac.freeplane.addons.llm.ApiCallerFactory
import com.barrymac.freeplane.addons.llm.ConfigManager
import com.barrymac.freeplane.addons.llm.MessageExpander
import com.barrymac.freeplane.addons.llm.prompts.MessageFileHandler // Corrected import
import com.barrymac.freeplane.addons.llm.prompts.MessageLoader // Corrected import
import com.barrymac.freeplane.addons.llm.maps.NodeTagger
import com.barrymac.freeplane.addons.llm.ApiConfig
import com.barrymac.freeplane.addons.llm.utils.JsonUtils
import com.barrymac.freeplane.addons.llm.maps.NodeOperations
import com.barrymac.freeplane.addons.llm.utils.UiHelper
import org.freeplane.core.util.LogUtils

// --- Moved Class and Method Definitions ---
class MessageItem {
    String value

    MessageItem(String value) {
        this.value = value.replaceAll(/\s+/, ' ').take(120)
    }

    @Override
    boolean equals(Object o) {
        // Use identity comparison for unique items in ComboBox
        return this.is(o)
    }

    @Override
    int hashCode() {
        return System.identityHashCode(this)
    }

    @Override
    String toString() {
        return value
    }
}


class MessageArea {
    JTextArea textArea
    JComboBox comboBox

    void updateSelectedItemFromTextArea() {
        int selectedIndex = comboBox.selectedIndex
        if (selectedIndex == -1) return // Nothing selected

        def text = textArea.text
        def currentItem = comboBox.getItemAt(selectedIndex)

        // Only update if the text actually changed
        if (currentItem instanceof MessageItem && currentItem.value != text) {
            // Create a new MessageItem to reflect the change
            def newItem = new MessageItem(text)
            // Update the model directly
            def model = comboBox.getModel()
            if (model instanceof DefaultComboBoxModel) {
                model.removeElementAt(selectedIndex)
                model.insertElementAt(newItem, selectedIndex)
                comboBox.setSelectedIndex(selectedIndex) // Re-select the updated item
            }
        }
    }
}

MessageArea createMessageSection(def swingBuilder, def messages, def title, int initialIndex, def constraints, def weighty) {
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
        messageText = textArea(rows: 5 * weighty, columns: 80, tabSize: 3, lineWrap: true, wrapStyleWord: true, text: initialText, caretPosition: 0) {}
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
                if (oldItem instanceof MessageItem && oldItem.value != messageText.text) {
                    comboBoxModel.removeElementAt(selectedIndex)
                    comboBoxModel.insertElementAt(new MessageItem(messageText.text), selectedIndex)
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
// --- End Moved Definitions ---


// --- Initialize Core Components ---
LogUtils.info("AskGpt script started.")
try {
    // Load configuration FIRST
    ApiConfig apiConfig = ConfigManager.loadBaseConfig(config)

    // Use ConfigManager directly for indices
    def selectedSystemMessageIndex = ConfigManager.getSystemMessageIndex(config)
    def selectedUserMessageIndex = ConfigManager.getUserMessageIndex(config)

    // Define file paths directly
    String systemMessagesFilePath = "${config.freeplaneUserDirectory}/chatGptSystemMessages.txt"
    String userMessagesFilePath = "${config.freeplaneUserDirectory}/chatGptUserMessages.txt"

    // Instantiate ApiCaller and get NodeTagger reference
    def apiCallerClosures = ApiCallerFactory.createApiCaller([ui: ui])
    if (!apiCallerClosures) {
        throw new Exception("Failed to create API caller closures.")
    }
    // Get the specific make_api_call closure from the returned map
    Closure make_api_call = apiCallerClosures.make_api_call
    Closure tagWithModel = NodeTagger.&tagWithModel // Get method reference


    def swingBuilder = new SwingBuilder()
    swingBuilder.edt { // edt method makes sure the GUI is built on the Event Dispatch Thread.
        def dialog = swingBuilder.dialog(title: 'Chat GPT Communicator', owner: ui.currentFrame) {
            swingBuilder.panel(layout: new GridBagLayout()) {
                def constraints = new GridBagConstraints()
                constraints.fill = GridBagConstraints.BOTH
                constraints.weightx = 1.0
                constraints.gridx = 0
                constraints.gridy = -1  // Will be incremented to 0 in the first call to createSection

                // Load messages using MessageFileHandler and MessageLoader
                def systemMessages = MessageFileHandler.loadMessagesFromFile(
                        systemMessagesFilePath,
                        "/defaultSystemMessages.txt",
                        MessageLoader.&loadDefaultMessages // Pass method reference
                )
                def userMessages = MessageFileHandler.loadMessagesFromFile(
                        userMessagesFilePath,
                        "/defaultUserMessages.txt",
                        MessageLoader.&loadDefaultMessages // Pass method reference
                )

                // Use the indices loaded earlier via ConfigManager
                MessageArea systemMessageArea = createMessageSection(swingBuilder, systemMessages, "System Message", selectedSystemMessageIndex, constraints, 4)
                MessageArea userMessageArea = createMessageSection(swingBuilder, userMessages, "User Message", selectedUserMessageIndex, constraints, 1)

                constraints.gridy++
                def apiKeyField
                def responseLengthField
                def gptModelBox
                def temperatureSlider
                def apiProviderBox // Define apiProviderBox here
                swingBuilder.panel(constraints: constraints, layout: new GridBagLayout()) {
                    def c = new GridBagConstraints()
                    c.fill = GridBagConstraints.BOTH
                    c.weightx = 1.0
                    c.weighty = 1.0
                    c.gridx = 0
                    c.gridy = 0
                    swingBuilder.panel(constraints: c, layout: new BorderLayout(), border: BorderFactory.createTitledBorder('API Key')) {
                        apiKeyField = passwordField(columns: 10, text: apiConfig.apiKey)
                    }
                    c.gridx++
                    swingBuilder.panel(constraints: c, layout: new BorderLayout(), border: BorderFactory.createTitledBorder('Max Response Length'), toolTipText: 'Maximum Response Length') {
                        responseLengthField = formattedTextField(columns: 5, value: apiConfig.maxTokens)
                    }
                    c.gridx++
                    swingBuilder.panel(constraints: c, layout: new BorderLayout(), border: BorderFactory.createTitledBorder('Language Model')) {
                        gptModelBox = comboBox(
                            items: apiConfig.availableModels,
                            selectedItem: apiConfig.model,
                            prototypeDisplayValue: apiConfig.availableModels.max { it.length() }
                        )
                    }
                    c.gridx++
                    swingBuilder.panel(constraints: c, layout: new BorderLayout(), border: BorderFactory.createTitledBorder('API Provider')) {
                        apiProviderBox = comboBox(items: ['openai', 'openrouter'], selectedItem: apiConfig.provider)
                    }
                    c.gridx++
                    swingBuilder.panel(constraints: c, layout: new BorderLayout(), border: BorderFactory.createTitledBorder('Randomness')) {
                        temperatureSlider = slider(minimum: 0, maximum: 100, minorTickSpacing: 5, majorTickSpacing: 50, snapToTicks: true,
                                paintTicks: true, paintLabels: true, value: (int) (apiConfig.temperature * 100.0 + 0.5))
                    }
                }
                constraints.gridy++
                swingBuilder.panel(constraints: constraints) {
                    def c = new GridBagConstraints() // Define constraints for buttons
                    def askGptButton = swingBuilder.button(constraints: c, action: swingBuilder.action(name: 'Prompt LLM') {
                        try {
                            // 1. Get current values from GUI
                            def currentApiKey = String.valueOf(apiKeyField.password)
                            def currentSystemMessage = systemMessageArea.textArea.text
                            def currentUserMessageTemplate = userMessageArea.textArea.text
                            def currentModel = gptModelBox.selectedItem
                            def currentMaxTokens = responseLengthField.value
                            def currentTemperature = temperatureSlider.value / 100.0
                            def currentProvider = apiProviderBox.selectedItem

                            // 2. Get selected node
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

                            LogUtils.info("AskGpt: Sending payload: ${payload}")

                            // 5. Call API
                            UiHelper.showInformationMessage(ui, "Sending prompt to ${currentModel}...")

                            def rawApiResponse = make_api_call(currentProvider, currentApiKey, payload)

                            if (rawApiResponse == null || rawApiResponse.isEmpty()) {
                                throw new Exception("Received empty or null response from API.")
                            }

                            // 6. Process Response
                            def responseContent = JsonUtils.extractLlmContent(rawApiResponse)
                            LogUtils.info("AskGpt: Received response content:\n${responseContent}")

                            // 7. Update Map
                            NodeOperations.addAnalysisBranch(
                                    node,                   // Parent node
                                    null,                   // No analysis map
                                    responseContent,        // The raw text content
                                    currentModel,           // Model used
                                    tagWithModel,           // Tagger function
                                    "LLM Prompt Result"     // Optional type string
                            )

                            UiHelper.showInformationMessage(ui, "Response added as a new branch.")

                        } catch (Exception ex) {
                            LogUtils.severe("Error during 'Prompt LLM' action: ${ex.message}", ex)
                            UiHelper.showErrorMessage(ui, "Prompt LLM Error: ${ex.message.split('\n').head()}")
                        }
                    })
                    askGptButton.rootPane.defaultButton = askGptButton
                    swingBuilder.button(constraints: c, action: swingBuilder.action(name: 'Save Changes') {
                        // Ensure the current text area content is saved to the list before saving files/config
                        if (systemMessageArea.comboBox.selectedIndex != -1) {
                            systemMessages[systemMessageArea.comboBox.selectedIndex] = systemMessageArea.textArea.text
                        }
                        if (userMessageArea.comboBox.selectedIndex != -1) {
                            userMessages[userMessageArea.comboBox.selectedIndex] = userMessageArea.textArea.text
                        }

                        // Save messages to files
                        MessageFileHandler.saveMessagesToFile(systemMessagesFilePath, systemMessages)
                        MessageFileHandler.saveMessagesToFile(userMessagesFilePath, userMessages)

                        // Save configuration properties
                        config.setProperty('openai.key', String.valueOf(apiKeyField.password))
                        config.setProperty('openai.gpt_model', gptModelBox.selectedItem)
                        config.setProperty('openai.max_response_length', responseLengthField.value)
                        config.setProperty('openai.temperature', temperatureSlider.value / 100.0)
                        config.setProperty('openai.system_message_index', systemMessageArea.comboBox.selectedIndex)
                        config.setProperty('openai.user_message_index', userMessageArea.comboBox.selectedIndex)
                        config.setProperty('openai.api_provider', apiProviderBox.selectedItem)

                        // Update the ComboBox items to reflect potentially changed text
                        systemMessageArea.updateSelectedItemFromTextArea()
                        userMessageArea.updateSelectedItemFromTextArea()

                        UiHelper.showInformationMessage(ui, "Changes saved.") // Provide feedback
                    })
                }
            }
        }
        dialog.pack()
        ui.setDialogLocationRelativeTo(dialog, ui.currentFrame)
        dialog.show()
    }

} catch (Exception e) {
    LogUtils.severe("Error initializing AskGpt script: ${e.message}", e)
    UiHelper.showErrorMessage(ui, "Initialization Error: ${e.message.split('\n').head()}")
} finally {
    LogUtils.info("AskGpt script finished.") // Add logging if desired
}
