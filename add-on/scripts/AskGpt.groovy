import com.barrymac.freeplane.addons.llm.exceptions.*
import groovy.swing.SwingBuilder

import javax.swing.*
import java.awt.*

// ADD these imports
import com.barrymac.freeplane.addons.llm.ApiCallerFactory
import com.barrymac.freeplane.addons.llm.BranchGeneratorFactory
import com.barrymac.freeplane.addons.llm.ConfigManager
import com.barrymac.freeplane.addons.llm.Dependencies
import com.barrymac.freeplane.addons.llm.MessageExpander
import com.barrymac.freeplane.addons.llm.MessageFileHandler
import com.barrymac.freeplane.addons.llm.MessageLoader
import com.barrymac.freeplane.addons.llm.NodeTagger
import com.barrymac.freeplane.addons.llm.ApiConfig // Ensure ApiConfig is imported
import com.barrymac.freeplane.addons.llm.Message // Ensure Message is imported
import org.freeplane.core.util.LogUtils // Ensure LogUtils is imported


// REPLACE deps.configManager calls with ConfigManager static calls
def apiConfig = ConfigManager.loadBaseConfig(config)
// Use ConfigManager directly for indices
def selectedSystemMessageIndex = ConfigManager.getSystemMessageIndex(config)
def selectedUserMessageIndex = ConfigManager.getUserMessageIndex(config)

// Define file paths directly
String systemMessagesFilePath = "${config.freeplaneUserDirectory}/chatGptSystemMessages.txt"
String userMessagesFilePath = "${config.freeplaneUserDirectory}/chatGptUserMessages.txt"

// Instantiate ApiCaller and get NodeTagger reference
// Pass logger if available, otherwise null
def apiCaller = ApiCallerFactory.createApiCaller([ui: ui, logger: (binding.variables.containsKey('logger') ? logger : null)])
def nodeTagger = NodeTagger.&tagWithModel // Get method reference

// REPLACE deps.branchGeneratorFactory call:
// 1. Call BranchGeneratorFactory.createGenerateBranches directly
// 2. Pass required dependencies (apiCaller, nodeTagger) in a Dependencies object
def generateBranches = BranchGeneratorFactory.createGenerateBranches(
        [c: c, ui: ui], // Pass context needed by the *generated* closure
        // Pass only the required dependencies for the factory
        new Dependencies(apiCaller: apiCaller, nodeTagger: nodeTagger)
)


class MessageItem {
    String value

    MessageItem(String value) {
        this.value = value.replaceAll(/\s+/, ' ').take(120)
    }

    @Override
    boolean equals(Object o) {
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
        def text = textArea.text
        comboBox.removeItemAt(selectedIndex)
        comboBox.insertItemAt(new MessageItem(text), selectedIndex)
        comboBox.selectedIndex = selectedIndex
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
    messageComboBox.selectedIndex = initialIndex

    constraints.gridy++
    constraints.weighty = 1.0 * weighty
    swingBuilder.scrollPane(constraints: constraints) {
        messageText = textArea(rows: 5 * weighty, columns: 80, tabSize: 3, lineWrap: true, wrapStyleWord: true, text: messages[initialIndex], caretPosition: 0) {}
    }
    messageComboBox.addActionListener { actionEvent ->
        if (selectedIndex != -1 && messageComboBox.selectedIndex != selectedIndex) {
            messages[selectedIndex] = messageText.text
            comboBoxModel.removeElementAt(selectedIndex)
            comboBoxModel.insertElementAt(new MessageItem(messages[selectedIndex]), selectedIndex)
        }
        selectedIndex = messageComboBox.selectedIndex
        if (messageText.text != messages[selectedIndex]) {
            messageText.text = messages[selectedIndex]
            messageText.caretPosition = 0
        }
    }
    constraints.gridy++
    constraints.weighty = 0.0
    swingBuilder.panel(layout: new FlowLayout(), constraints: constraints) {
        button(action: swingBuilder.action(name: "Reset ${title}".toString()) {
            messageText.text = messages[selectedIndex]
            messageText.caretPosition = 0
        })
        button(action: swingBuilder.action(name: "Duplicate ${title}".toString()) {
            messages.add(messageText.text)
            comboBoxModel.addElement(new MessageItem(messageText.text))
            messageText.text = messages[selectedIndex]
            messageComboBox.selectedIndex = selectedIndex = messageComboBox.itemCount - 1
        })
        button(action: swingBuilder.action(name: "Delete ${title}".toString()) {
            if (selectedIndex != -1) {
                messages.remove(selectedIndex)
                comboBoxModel.removeElementAt(selectedIndex)
                selectedIndex = -1
            }
        })
    }
    return new MessageArea(textArea: messageText, comboBox: messageComboBox)
}

def swingBuilder = new SwingBuilder()
swingBuilder.edt { // edt method makes sure the GUI is built on the Event Dispatch Thread.
    def dialog = swingBuilder.dialog(title: 'Chat GPT Communicator', owner: ui.currentFrame) {
        swingBuilder.panel(layout: new GridBagLayout()) {
            def constraints = new GridBagConstraints()
            constraints.fill = GridBagConstraints.BOTH
            constraints.weightx = 1.0
            constraints.gridx = 0
            constraints.gridy = -1  // Will be incremented to 0 in the first call to createSection

            // REPLACE deps.messageFileHandler/deps.messageLoader calls
            // Pass MessageLoader.loadDefaultMessages directly as the closure argument
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
                    // REPLACE deps.messageExpander call with MessageExpander static call
                    // Assuming c.selected is the node context
                    def expandedUserMessage = MessageExpander.expandTemplate(
                        userMessageArea.textArea.text,
                        MessageExpander.createBinding(c.selected, null, null, null, null) // Use createBinding for context
                    )

                    // Call the generateBranches closure obtained earlier
                    generateBranches(
                            String.valueOf(apiKeyField.password),
                            systemMessageArea.textArea.text, // System message text
                            expandedUserMessage,             // Expanded user message text
                            gptModelBox.selectedItem,
                            responseLengthField.value,
                            temperatureSlider.value / 100.0,
                            apiProviderBox.selectedItem
                    )
                })
                askGptButton.rootPane.defaultButton = askGptButton
                swingBuilder.button(constraints: c, action: swingBuilder.action(name: 'Save Changes') {
                    systemMessages[systemMessageArea.comboBox.selectedIndex] = systemMessageArea.textArea.text
                    userMessages[userMessageArea.comboBox.selectedIndex] = userMessageArea.textArea.text
                    // REPLACE deps.messageFileHandler call with MessageFileHandler static call
                    MessageFileHandler.saveMessagesToFile(systemMessagesFilePath, systemMessages)
                    MessageFileHandler.saveMessagesToFile(userMessagesFilePath, userMessages)
                    config.setProperty('openai.key', String.valueOf(apiKeyField.password))
                    config.setProperty('openai.gpt_model', gptModelBox.selectedItem)
                    config.setProperty('openai.max_response_length', responseLengthField.value)
                    config.setProperty('openai.temperature', temperatureSlider.value / 100.0)
                    config.setProperty('openai.system_message_index', systemMessageArea.comboBox.selectedIndex)
                    config.setProperty('openai.user_message_index', userMessageArea.comboBox.selectedIndex)
                    config.setProperty('openai.api_provider', apiProviderBox.selectedItem)
                    systemMessageArea.updateSelectedItemFromTextArea()
                    userMessageArea.updateSelectedItemFromTextArea()
                })
            }
        }
    }
    dialog.pack()
    ui.setDialogLocationRelativeTo(dialog, ui.currentFrame)
    dialog.show()
}

LogUtils.info("AskGpt script finished.") // Add logging if desired
