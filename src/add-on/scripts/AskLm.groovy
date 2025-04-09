// Core Freeplane imports
import org.freeplane.core.util.LogUtils

// Standard Java/Swing imports - No longer needed for main dialog UI elements here

// Core LLM Add-on imports
import com.barrymac.freeplane.addons.llm.api.ApiCallerFactory
import com.barrymac.freeplane.addons.llm.ApiConfig
import com.barrymac.freeplane.addons.llm.ConfigManager
import com.barrymac.freeplane.addons.llm.prompts.MessageExpander // Needed for expanding template
import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException // Needed for API error handling
import com.barrymac.freeplane.addons.llm.utils.JsonUtils // Needed for response parsing
import com.barrymac.freeplane.addons.llm.utils.UiHelper // Still needed for error/info messages
import com.barrymac.freeplane.addons.llm.maps.NodeOperations // Needed for adding branch
import com.barrymac.freeplane.addons.llm.maps.NodeTagger // Still needed for tagging
import com.barrymac.freeplane.addons.llm.prompts.MessageFileHandler // Needed for loading/saving messages
import com.barrymac.freeplane.addons.llm.prompts.MessageLoader // Needed for loading default messages

// Model imports (MessageItem, MessageArea are handled by DialogHelper now)

// UI Helper import
import com.barrymac.freeplane.addons.llm.ui.DialogHelper // Import the refactored helper

// --- Initialize Core Components ---
LogUtils.info("AskLm script started.")
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

    // Load messages using MessageFileHandler and MessageLoader
    // These lists might be modified by the dialog (duplicate/delete actions)
    // and the modified versions will be returned in the result map for saving.
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

    // --- Check for Parent Frame before showing dialog ---
    if (ui.currentFrame == null) {
        LogUtils.severe("AskLm Script: Cannot show dialog because ui.currentFrame is null.")
        UiHelper.showErrorMessage(ui, "Cannot show dialog: Parent window not found.")
        return // Exit script
    }

    // --- Show the Dialog using DialogHelper ---
    // Pass only necessary parameters. DialogHelper returns a Map.
    Map dialogResult = DialogHelper.showAskLmDialog(
            ui,                     // Pass Freeplane UI object (needed for owner/placement)
            apiConfig,              // Pass loaded API configuration (for initial values)
            systemMessages,         // Pass list (can be modified by dialog UI actions)
            userMessages,           // Pass list (can be modified by dialog UI actions)
            selectedSystemMessageIndex, // Pass initial index
            selectedUserMessageIndex    // Pass initial index
            // Removed: config, c, file paths, closures
    )

    // --- Process Dialog Result ---
    if (dialogResult == null || dialogResult.action == 'Close') {
        LogUtils.info("Dialog closed or cancelled.")
        // No action needed

    } else if (dialogResult.action == 'Error') {
         LogUtils.severe("DialogHelper reported an error: ${dialogResult.message}")
         UiHelper.showErrorMessage(ui, "Dialog Error: ${dialogResult.message ?: 'Unknown error during dialog operation'}")

    } else if (dialogResult.action == 'Prompt') {
        LogUtils.info("Processing 'Prompt' action from dialog.")
        try {
            // 1. Get selected node (using 'c' from script context)
            def node = c.selected
            if (node == null) {
                UiHelper.showInformationMessage(ui, "Please select a node first.")
                // Optionally, could re-show the dialog or just exit
                return // Exit script gracefully
            }

            // 2. Expand user message template from result map
            def expandedUserMessage = MessageExpander.expandTemplate(
                    dialogResult.userMessage, // Use template returned from dialog
                    MessageExpander.createBinding(node, null, null, null, null)
            )

            // 3. Prepare API Payload Map using data from result map
            def messagesList = [
                    [role: 'system', content: dialogResult.systemMessage],
                    [role: 'user', content: expandedUserMessage]
            ]
            Map<String, Object> payload = [
                    'model'      : dialogResult.model,
                    'messages'   : messagesList,
                    'temperature': dialogResult.temperature,
                    'max_tokens' : dialogResult.maxTokens,
                    // Add response_format based on provider/model from result map
                    'response_format': (dialogResult.provider == 'openai' && dialogResult.model.toString().contains("gpt")) ? [type: "text"] : null
            ].findAll { key, value -> value != null } // Filter out null values

            LogUtils.info("AskLm Script: Sending payload: ${payload}")

            // 4. Show Progress & Call API (using closure defined earlier)
            // --- Updated Progress Dialog Message ---
            def truncatedPrompt = expandedUserMessage.take(300) + (expandedUserMessage.length() > 300 ? "..." : "")
            def progressMessage = "Sending prompt to ${dialogResult.model}:\n\n${truncatedPrompt}"
            def progressDialog = DialogHelper.createProgressDialog(ui, "LLM Request", progressMessage)
            progressDialog.visible = true
            def rawApiResponse
            try {
                // Use make_api_call closure defined earlier in the script
                // Pass provider and apiKey from the result map
                rawApiResponse = make_api_call(dialogResult.provider, dialogResult.apiKey, payload)
            } finally {
                progressDialog.dispose() // Ensure dialog closes
            }

            if (rawApiResponse == null || rawApiResponse.isEmpty()) {
                throw new LlmAddonException("Received empty or null response from API.")
            }

            // 5. Process Response
            def responseContent = JsonUtils.extractLlmContent(rawApiResponse)
            LogUtils.info("AskLm Script: Received response content:\n${responseContent}")

            // 6. Update Map (using NodeOperations and tagWithModel closure)
            NodeOperations.addAnalysisBranch(
                    node,                   // Parent node (from script context)
                    null,                   // No analysis map
                    responseContent,        // The raw text content
                    dialogResult.model,     // Model used (from result map)
                    tagWithModel,           // Tagger function (closure defined earlier)
                    "LLM Prompt Result"     // Optional type string
            )

            // --- Removed Success Confirmation Dialog ---
            // UiHelper.showInformationMessage(ui, "Response added as a new branch.")

        } catch (Exception ex) {
            LogUtils.severe("Error during 'Prompt LLM' action in script: ${ex.message}", ex)
            UiHelper.showErrorMessage(ui, "Prompt LLM Error: ${ex.message.split('\n').head()}")
        }

    } else if (dialogResult.action == 'Save') {
        LogUtils.info("Processing 'Save' action from dialog.")
        try {
            // 1. Save potentially updated messages using lists from result map
            // Use file paths defined earlier in the script
            MessageFileHandler.saveMessagesToFile(systemMessagesFilePath, dialogResult.updatedSystemMessages)
            MessageFileHandler.saveMessagesToFile(userMessagesFilePath, dialogResult.updatedUserMessages)

            // 2. Save configuration properties using data from result map
            // Use 'config' from script context
            config.setProperty('openai.key', dialogResult.apiKey)
            config.setProperty('openai.gpt_model', dialogResult.model)
            config.setProperty('openai.max_response_length', dialogResult.maxTokens)
            config.setProperty('openai.temperature', dialogResult.temperature)
            config.setProperty('openai.system_message_index', dialogResult.systemMessageIndex)
            config.setProperty('openai.user_message_index', dialogResult.userMessageIndex)
            config.setProperty('openai.api_provider', dialogResult.provider)

            UiHelper.showInformationMessage(ui, "Changes saved.")

        } catch (Exception ex) {
            LogUtils.severe("Error during 'Save Changes' action in script: ${ex.message}", ex)
            UiHelper.showErrorMessage(ui, "Save Error: ${ex.message.split('\n').head()}")
        }
    }

} catch (Exception e) {
    LogUtils.severe("Error initializing AskLm script or processing dialog result: ${e.message}", e)
    // Use UiHelper for user-facing errors
    UiHelper.showErrorMessage(ui, "Script Error: ${e.message.split('\n').head()}")
} finally {
    LogUtils.info("AskLm script finished.")
}
