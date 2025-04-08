import groovy.swing.SwingBuilder
import javax.swing.JOptionPane

// Import the compiled classes directly
import com.barrymac.freeplane.addons.llm.exceptions.*
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
import com.barrymac.freeplane.addons.llm.ApiRequest // Ensure ApiRequest is imported
import org.freeplane.core.util.LogUtils // Ensure LogUtils is imported


// REPLACE deps.configManager calls with ConfigManager static calls
def apiConfig = ConfigManager.loadBaseConfig(config)
def systemMessageIndex = ConfigManager.getSystemMessageIndex(config)
def userMessageIndex = ConfigManager.getUserMessageIndex(config)

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

// Load message templates directly
def systemMessagesFilePath = "${config.freeplaneUserDirectory}/chatGptSystemMessages.txt"
def userMessagesFilePath = "${config.freeplaneUserDirectory}/chatGptUserMessages.txt"

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

// Validate and fallback to defaults if needed (using indices loaded via ConfigManager)
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
    // REPLACE deps.messageExpander call with MessageExpander static call
    def expandedUserMessage = MessageExpander.expandTemplate(
        userMessageTemplate,
        MessageExpander.createBinding(c.selected, null, null, null, null) // Use createBinding for context
    )

    // Create Message and ApiRequest objects (this part remains the same)
    def systemMsg = new Message(role: 'system', content: systemMessage)
    def userMsg = new Message(role: 'user', content: expandedUserMessage)
    def request = new ApiRequest(
        model: apiConfig.model,
        messages: [systemMsg, userMsg],
        temperature: apiConfig.temperature,
        maxTokens: apiConfig.maxTokens
    )

    // Call the generateBranches closure obtained earlier
    // The arguments are already passed individually, so this call structure is correct
    generateBranches(
            apiConfig.apiKey,
            systemMessage, // Pass the selected system message content
            expandedUserMessage, // Pass the expanded user message content
            apiConfig.model,
            apiConfig.maxTokens,
            apiConfig.temperature,
            apiConfig.provider
    )
} catch (LlmAddonException e) {
    // Use logger if available, otherwise LogUtils
    def log = binding.variables.containsKey('logger') ? logger : LogUtils
    log.warn("Quick prompt failed: ${e.message}")
    ui.errorMessage(e.message)
} catch (Exception e) {
    def log = binding.variables.containsKey('logger') ? logger : LogUtils
    log.warn("Quick prompt failed", e) // Log the exception object too
    ui.errorMessage("Quick prompt error: ${e.message}")
}

LogUtils.info("QuickPrompt script finished.") // Add logging if desired
