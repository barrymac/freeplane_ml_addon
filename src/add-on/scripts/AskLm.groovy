// Core Freeplane imports
import org.freeplane.core.util.LogUtils

// Standard Java/Swing imports
// SwingBuilder, J*, etc. are no longer needed here for the main dialog

// Core LLM Add-on imports
import com.barrymac.freeplane.addons.llm.api.ApiCallerFactory
import com.barrymac.freeplane.addons.llm.ApiConfig
import com.barrymac.freeplane.addons.llm.ConfigManager

// Still needed for binding

// Still needed for potential errors

// Utility imports

// Still needed for response parsing
import com.barrymac.freeplane.addons.llm.utils.UiHelper // Still needed for error/info messages

// Map operation imports

// Still needed for adding branch
import com.barrymac.freeplane.addons.llm.maps.NodeTagger // Still needed for tagging

// Message handling imports
import com.barrymac.freeplane.addons.llm.prompts.MessageFileHandler
import com.barrymac.freeplane.addons.llm.prompts.MessageLoader

// Model imports (MessageItem, MessageArea are handled by DialogHelper now)

// UI Helper import
import com.barrymac.freeplane.addons.llm.ui.DialogHelper // Import the refactored helper

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

    // --- UI Building and Interaction is now handled by DialogHelper ---

    // Show the main dialog using DialogHelper
    DialogHelper.showAskGptDialog(
            ui,                     // Freeplane UI
            config,                 // Freeplane Config
            c,                      // Controller (for c.selected)
            apiConfig,              // Loaded API configuration
            systemMessages,         // List of system messages (passed by reference, can be modified by dialog)
            userMessages,           // List of user messages (passed by reference, can be modified by dialog)
            selectedSystemMessageIndex, // Initial system index
            selectedUserMessageIndex,   // Initial user index
            systemMessagesFilePath, // Path to save system messages
            userMessagesFilePath,   // Path to save user messages
            make_api_call,          // API call closure
            tagWithModel            // Node tagging closure
    )

} catch (Exception e) {
    LogUtils.severe("Error initializing AskGpt script: ${e.message}", e)
    // Use UiHelper for user-facing errors
    UiHelper.showErrorMessage(ui, "Initialization Error: ${e.message.split('\n').head()}")
} finally {
    LogUtils.info("AskGpt script finished.")
}
