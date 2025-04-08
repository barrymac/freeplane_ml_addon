// Core Freeplane imports
import org.freeplane.core.util.LogUtils

// Standard Java/Swing imports
import groovy.swing.SwingBuilder
import javax.swing.*
import java.awt.*

// Core LLM Add-on imports
import com.barrymac.freeplane.addons.llm.api.ApiCallerFactory
import com.barrymac.freeplane.addons.llm.ApiConfig
import com.barrymac.freeplane.addons.llm.ConfigManager
import com.barrymac.freeplane.addons.llm.prompts.MessageExpander
import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException

// Utility imports
import com.barrymac.freeplane.addons.llm.utils.JsonUtils
import com.barrymac.freeplane.addons.llm.utils.UiHelper

// Map operation imports
import com.barrymac.freeplane.addons.llm.maps.NodeOperations
import com.barrymac.freeplane.addons.llm.maps.NodeTagger

// Message handling imports
import com.barrymac.freeplane.addons.llm.prompts.MessageFileHandler
import com.barrymac.freeplane.addons.llm.prompts.MessageLoader

// --- Initialize Core Components ---
LogUtils.info("QuickPrompt script started.")
try {
    // Load configuration FIRST
    ApiConfig apiConfig = ConfigManager.loadBaseConfig(config)
    def selectedSystemMessageIndex = ConfigManager.getSystemMessageIndex(config)
    def selectedUserMessageIndex = ConfigManager.getUserMessageIndex(config)

    // Define file paths
    String systemMessagesFilePath = "${config.freeplaneUserDirectory}/chatGptSystemMessages.txt"
    String userMessagesFilePath = "${config.freeplaneUserDirectory}/chatGptUserMessages.txt"

    // Instantiate ApiCaller and get NodeTagger reference
    def apiCallerClosures = ApiCallerFactory.createApiCaller([ui: ui])
    if (!apiCallerClosures) {
        throw new Exception("Failed to create API caller closures.")
    }
    Closure make_api_call = apiCallerClosures.make_api_call
    Closure nodeTagger = NodeTagger.&tagWithModel

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
        UiHelper.showInformationMessage(ui, "Please select a node first.")
    } else {
        // Expand user message template
        def expandedUserMessage = MessageExpander.expandTemplate(
            userMessageTemplate,
            MessageExpander.createBinding(node, null, null, null, null)
        )

        LogUtils.info("QuickPrompt: Using System Message:\n${systemMessageText}")
        LogUtils.info("QuickPrompt: Using Expanded User Message:\n${expandedUserMessage}")

        // --- Prepare API Payload ---
        def messagesList = [
            [role: 'system', content: systemMessageText],
            [role: 'user', content: expandedUserMessage]
        ]

        Map<String, Object> payload = [
            'model'      : apiConfig.model,
            'messages'   : messagesList,
            'temperature': apiConfig.temperature,
            'max_tokens' : apiConfig.maxTokens,
            'response_format': (apiConfig.provider == 'openai' && apiConfig.model.contains("gpt")) ? [type: "text"] : null
        ].findAll { key, value -> value != null }

        LogUtils.info("QuickPrompt: Sending payload: ${payload}")

        // Show progress indicator
        UiHelper.showInformationMessage(ui, "Sending prompt to ${apiConfig.model}...")

        def rawApiResponse = make_api_call(apiConfig.provider, apiConfig.apiKey, payload)

        if (rawApiResponse == null || rawApiResponse.isEmpty()) {
            throw new Exception("Received empty or null response from API.")
        }

        def responseContent = JsonUtils.extractLlmContent(rawApiResponse)
        LogUtils.info("QuickPrompt: Received response content:\n${responseContent}")

        // Update Map
        NodeOperations.addAnalysisBranch(
                node,                   // Parent node
                null,                   // No analysis map for QuickPrompt
                responseContent,        // The raw text content
                apiConfig.model,        // Model used
                nodeTagger,            // Use correct variable name
                "Quick Prompt Result"   // Optional type string
        )

        UiHelper.showInformationMessage(ui, "Response added as a new branch.")
        LogUtils.info("QuickPrompt script finished successfully.")
    }

} catch (Exception e) {
    LogUtils.severe("Error in QuickPrompt script: ${e.message}", e)
    UiHelper.showErrorMessage(ui, "QuickPrompt Error: ${e.message.split('\n').head()}")
}

LogUtils.info("QuickPrompt script finished execution path.")
