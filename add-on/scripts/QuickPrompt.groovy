import groovy.swing.SwingBuilder
import javax.swing.JOptionPane

// Import the compiled DependencyLoader and other classes
import com.barrymac.freeplane.addons.llm.*
import com.barrymac.freeplane.addons.llm.exceptions.*

// Load all dependencies
// Call static method directly
def deps = DependencyLoader.loadDependencies(config, null, ui)

// Extract needed functions/classes from deps
def ConfigManager = deps.configManager
def expandMessage = deps.messageExpander.expandMessage // Get static method reference
def loadMessagesFromFile = deps.messageFileHandler.loadMessagesFromFile
def loadDefaultMessages = deps.messageLoader.loadDefaultMessages // Get the new loader function
def createBranchGenerator = deps.branchGeneratorFactory // Get factory method

// Load configuration using ConfigManager
def apiConfig = ConfigManager.loadBaseConfig(config)
def systemMessageIndex = ConfigManager.getSystemMessageIndex(config)
def userMessageIndex = ConfigManager.getUserMessageIndex(config)

// Initialize the branch generator with necessary dependencies
def generateBranches = createBranchGenerator([ // Call the factory method
        c      : c,
        ui     : ui,
        logger : logger,
        config : config
], deps) // Pass the loaded dependencies map

// Load message templates
def systemMessagesFilePath = "${config.freeplaneUserDirectory}/chatGptSystemMessages.txt"
def userMessagesFilePath = "${config.freeplaneUserDirectory}/chatGptUserMessages.txt"

// Load messages, using defaults from JAR via MessageLoader if user file doesn't exist
def systemMessages = loadMessagesFromFile(systemMessagesFilePath, "/defaultSystemMessages.txt", loadDefaultMessages)
def userMessages = loadMessagesFromFile(userMessagesFilePath, "/defaultUserMessages.txt", loadDefaultMessages)

// Validate and fallback to defaults if needed
def systemMessage = systemMessageIndex < systemMessages.size() ? systemMessages[systemMessageIndex] : systemMessages[0]
def userMessageTemplate = userMessageIndex < userMessages.size() ? userMessages[userMessageIndex] : userMessages[0]

if (!apiConfig.apiKey) {
    JOptionPane.showMessageDialog(ui.currentFrame, 
        "Please configure API settings first via the LLM menu",
        "Configuration Required",
        JOptionPane.WARNING_MESSAGE)
    return
}

try {
    def expandedUserMessage = expandMessage(userMessageTemplate, c.selected)
    
    // Create a proper Message object for system and user messages
    def systemMsg = new Message(role: 'system', content: systemMessage)
    def userMsg = new Message(role: 'user', content: expandedUserMessage)
    
    // Create an ApiRequest object
    def request = new ApiRequest(
        model: apiConfig.model,
        messages: [systemMsg, userMsg],
        temperature: apiConfig.temperature,
        maxTokens: apiConfig.maxTokens
    )
    
    // Call the API with the structured request
    generateBranches(apiConfig.apiKey, systemMessage, expandedUserMessage, 
                    apiConfig.model, apiConfig.maxTokens, apiConfig.temperature, apiConfig.provider)
} catch (LlmAddonException e) {
    logger.warn("Quick prompt failed: ${e.message}")
    ui.errorMessage(e.message)
} catch (Exception e) {
    logger.warn("Quick prompt failed", e)
    ui.errorMessage("Quick prompt error: ${e.message}")
}
