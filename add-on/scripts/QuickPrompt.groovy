// --- Imports ---
import com.barrymac.freeplane.addons.llm.exceptions.*
import groovy.swing.SwingBuilder
import javax.swing.*
import java.awt.*

// Core LLM Addon Classes
import com.barrymac.freeplane.addons.llm.ApiCallerFactory
import com.barrymac.freeplane.addons.llm.ConfigManager
import com.barrymac.freeplane.addons.llm.MessageExpander
import com.barrymac.freeplane.addons.llm.MessageFileHandler
import com.barrymac.freeplane.addons.llm.MessageLoader
import com.barrymac.freeplane.addons.llm.maps.NodeTagger
import com.barrymac.freeplane.addons.llm.ApiConfig
import com.barrymac.freeplane.addons.llm.utils.JsonUtils
import com.barrymac.freeplane.addons.llm.maps.NodeOperations
import com.barrymac.freeplane.addons.llm.utils.UiHelper
import org.freeplane.core.util.LogUtils


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
    // Load messages using MessageFileHandler and MessageLoader
    def systemMessages = MessageFileHandler.loadMessagesFromFile(
            systemMessagesFilePath,
            "/defaultSystemMessages.txt",
            MessageLoader.&loadDefaultMessages
    )
    def userMessages = MessageFileHandler.loadMessagesFromFile(
            userMessagesFilePath,
            "/defaultUserMessages.txt",
            MessageLoader.&loadDefaultMessages
    )

    // Get the specific messages based on saved index
    String systemMessageText = systemMessages[selectedSystemMessageIndex]
    String userMessageTemplate = userMessages[selectedUserMessageIndex]

    // Get selected node
    def node = c.selected
    if (node == null) {
        ui.informationMessage("Please select a node first.")
        return
    }

    // Expand user message template
    def expandedUserMessage = MessageExpander.expandTemplate(
        userMessageTemplate,
        MessageExpander.createBinding(node, null, null, null, null)
    )

    LogUtils.info("QuickPrompt: Using System Message:\n${systemMessageText}")
    LogUtils.info("QuickPrompt: Using Expanded User Message:\n${expandedUserMessage}")

    // --- Prepare API Payload ---
    // Construct messages list directly as maps
    def messagesList = [
        [role: 'system', content: systemMessageText],
        [role: 'user', content: expandedUserMessage]
    ]

    // Construct payload map directly
    Map<String, Object> payload = [
        'model'      : apiConfig.model,
        'messages'   : messagesList,
        'temperature': apiConfig.temperature,
        'max_tokens' : apiConfig.maxTokens,
        // Add response_format for OpenAI models if applicable
        'response_format': (apiConfig.provider == 'openai' && apiConfig.model.contains("gpt")) ? [type: "text"] : null
    ].findAll { key, value -> value != null }

    LogUtils.info("QuickPrompt: Sending payload: ${payload}")

    // --- Call API ---
    // Show progress indicator (optional, simple message)
    ui.informationMessage("Sending prompt to ${apiConfig.model}...")

    def rawApiResponse = make_api_call(apiConfig.provider, apiConfig.apiKey, payload)

    if (rawApiResponse == null || rawApiResponse.isEmpty()) {
        throw new Exception("Received empty or null response from API.")
    }

    // --- Process Response ---
    // Extract content using JsonUtils (assuming standard OpenAI/compatible structure)
    def responseContent = JsonUtils.extractLlmContent(rawApiResponse)

    LogUtils.info("QuickPrompt: Received response content:\n${responseContent}")

    // --- Update Map ---
    // Add response as a new branch using NodeOperations
    NodeOperations.addAnalysisBranch(
            node,                   // Parent node
            null,                   // No analysis map for QuickPrompt
            responseContent,        // The raw text content
            apiConfig.model,        // Model used
            tagWithModel,           // Tagger function
            "Quick Prompt Result"   // Optional type string
    )

    ui.informationMessage("Response added as a new branch.")
    LogUtils.info("QuickPrompt script finished successfully.")

} catch (Exception e) {
    LogUtils.severe("Error in QuickPrompt script: ${e.message}", e)
    UiHelper.showErrorMessage(ui, "QuickPrompt Error: ${e.message.split('\n').head()}")
}
