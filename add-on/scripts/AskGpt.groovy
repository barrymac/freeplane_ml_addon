import com.barrymac.freeplane.addons.llm.DependencyLoader
import groovy.swing.SwingBuilder

import javax.swing.*
import java.awt.*

// Import the compiled DependencyLoader

// Load all dependencies
// Call static method directly
def deps = DependencyLoader.loadDependencies(config, logger, ui)

// Extract needed functions/classes from deps
def ConfigManager = deps.configManager
def expandMessageFunction = deps.messageExpander.expandMessage // Get static method reference
def loadMessagesFromFile = deps.messageFileHandler.loadMessagesFromFile
def saveMessagesToFile = deps.messageFileHandler.saveMessagesToFile
def loadDefaultMessages = deps.messageLoader.loadDefaultMessages // Get the new loader function
def createBranchGenerator = deps.branchGeneratorFactory // Get factory method

// Load configuration using ConfigManager
def configMap = ConfigManager.loadBaseConfig(config)
def apiKey = configMap.apiKey
def gptModel = configMap.model
def maxResponseLength = configMap.maxTokens
def temperature = configMap.temperature
def apiProvider = configMap.provider
def selectedSystemMessageIndex = config.getProperty('openai.system_message_index', 0) as int
def selectedUserMessageIndex = config.getProperty('openai.user_message_index', 0) as int

String systemMessagesFilePath = "${config.freeplaneUserDirectory}/chatGptSystemMessages.txt"
String userMessagesFilePath = "${config.freeplaneUserDirectory}/chatGptUserMessages.txt"

// Initialize the branch generator with necessary dependencies
def generateBranches = createBranchGenerator([ // Call the factory method
        c      : c,
        ui     : ui,
        logger : logger,
        config : config
], deps) // Pass the loaded dependencies map


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
            // Load messages, using defaults from JAR via MessageLoader if user file doesn't exist
            def systemMessages = loadMessagesFromFile(systemMessagesFilePath, "/defaultSystemMessages.txt", loadDefaultMessages)
            def userMessages = loadMessagesFromFile(userMessagesFilePath, "/defaultUserMessages.txt", loadDefaultMessages)
            MessageArea systemMessageArea = createMessageSection(swingBuilder, systemMessages, "System Message", selectedSystemMessageIndex, constraints, 4)
            MessageArea userMessageArea = createMessageSection(swingBuilder, userMessages, "User Message", selectedUserMessageIndex, constraints, 1)
            constraints.gridy++
            def apiKeyField
            def responseLengthField
            def gptModelBox
            def temperatureSlider
            swingBuilder.panel(constraints: constraints, layout: new GridBagLayout()) {
                def c = new GridBagConstraints()
                c.fill = GridBagConstraints.BOTH
                c.weightx = 1.0
                c.weighty = 1.0
                c.gridx = 0
                c.gridy = 0
                swingBuilder.panel(constraints: c, layout: new BorderLayout(), border: BorderFactory.createTitledBorder('API Key')) {
                    apiKeyField = passwordField(columns: 10, text: apiKey)
                }
                c.gridx++
                swingBuilder.panel(constraints: c, layout: new BorderLayout(), border: BorderFactory.createTitledBorder('Max Response Length'), toolTipText: 'Maximum Response Length') {
                    responseLengthField = formattedTextField(columns: 5, value: maxResponseLength)
                }
                c.gridx++
                swingBuilder.panel(constraints: c, layout: new BorderLayout(), border: BorderFactory.createTitledBorder('Language Model')) {
                    gptModelBox = comboBox(
                        items: configMap.availableModels,
                        selectedItem: gptModel,
                        prototypeDisplayValue: configMap.availableModels.max { it.length() }
                    )
                }
                c.gridx++
                swingBuilder.panel(constraints: c, layout: new BorderLayout(), border: BorderFactory.createTitledBorder('API Provider')) {
                    apiProviderBox = comboBox(items: ['openai', 'openrouter'], selectedItem: apiProvider)
                }
                c.gridx++
                swingBuilder.panel(constraints: c, layout: new BorderLayout(), border: BorderFactory.createTitledBorder('Randomness')) {
                    temperatureSlider = slider(minimum: 0, maximum: 100, minorTickSpacing: 5, majorTickSpacing: 50, snapToTicks: true,
                            paintTicks: true, paintLabels: true, value: (int) (temperature * 100.0 + 0.5))
                }
            }
            constraints.gridy++
            swingBuilder.panel(constraints: constraints) {
                def askGptButton = swingBuilder.button(constraints: c, action: swingBuilder.action(name: 'Prompt LLM') {
                    generateBranches(String.valueOf(apiKeyField.password),
                            systemMessageArea.textArea.text, // Call the extracted function
                            expandMessageFunction(userMessageArea.textArea.text, c.selected),
                            gptModelBox.selectedItem,
                            responseLengthField.value,
                            temperatureSlider.value / 100.0,
                            apiProviderBox.selectedItem)
                })
                askGptButton.rootPane.defaultButton = askGptButton
                swingBuilder.button(constraints: c, action: swingBuilder.action(name: 'Save Changes') {
                    systemMessages[systemMessageArea.comboBox.selectedIndex] = systemMessageArea.textArea.text
                    userMessages[userMessageArea.comboBox.selectedIndex] = userMessageArea.textArea.text
                    saveMessagesToFile(systemMessagesFilePath, systemMessages)
                    saveMessagesToFile(userMessagesFilePath, userMessages)
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
