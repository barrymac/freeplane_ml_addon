// --- Imports ---
// Core LLM Addon Classes
import com.barrymac.freeplane.addons.llm.ConfigManager
import com.barrymac.freeplane.addons.llm.ResponseProcessor
import com.barrymac.freeplane.addons.llm.ApiConfig // Needed for type hint

// API related classes
import com.barrymac.freeplane.addons.llm.api.ApiCallerFactory

// Map related classes
import com.barrymac.freeplane.addons.llm.maps.NodeTagger
import com.barrymac.freeplane.addons.llm.maps.NodeHelper
import com.barrymac.freeplane.addons.llm.maps.MapUpdater

// Prompt related classes
import com.barrymac.freeplane.addons.llm.prompts.MessageLoader
import com.barrymac.freeplane.addons.llm.prompts.DimensionGenerator
import com.barrymac.freeplane.addons.llm.prompts.PromptBuilder
import com.barrymac.freeplane.addons.llm.ui.CompareDialogueHelper

// UI related classes
import com.barrymac.freeplane.addons.llm.ui.DialogHelper
import com.barrymac.freeplane.addons.llm.ui.UiHelper

// Freeplane specific classes

// Ensure LogUtils is imported

// Standard Java/Swing classes

// Explicitly needed by UiHelper methods used here

// Ensure LogUtils is imported
import org.freeplane.core.util.LogUtils

import static com.barrymac.freeplane.addons.llm.ui.CompareDialogueHelper.*

// --- Initialize Core Components ---
// Load configuration FIRST
ApiConfig apiConfig = ConfigManager.loadBaseConfig(config)

// Create instances of required classes using the loaded config
// Pass only 'ui' to createApiCaller, as logger is no longer needed by the factory
def apiCallerClosures = ApiCallerFactory.createApiCaller([ui: ui])
if (!apiCallerClosures) {
    throw new Exception("Failed to create API caller closures.")
}
NodeTagger nodeTagger = new NodeTagger()

// Get method references for commonly used functions
// Get the specific make_api_call closure from the returned map
Closure make_api_call = apiCallerClosures.make_api_call
Closure addModelTagRecursively = nodeTagger.&tagWithModel

// Load messages
def messages = MessageLoader.loadComparisonMessages(config)
def systemMessageTemplate = messages.systemTemplate
def compareNodesUserMessageTemplate = messages.userTemplate

// --- Main Script Logic ---

// Wrap the entire script in a try-catch block for better error handling
try {

    // Check if templates are loaded
    if (systemMessageTemplate.isEmpty() || compareNodesUserMessageTemplate.isEmpty()) {
        throw new Exception("System message template or the dedicated compareNodesUserMessage.txt is missing or empty. Please check configuration or files.")
    }

    // 2. Get Selected Nodes and Validate (Use NodeHelper class from deps)
    def selectedNodes = c.selecteds
    // Use the static method directly via the class obtained from deps
    def nodes = NodeHelper.validateSelectedNodes(selectedNodes) // This might throw ValidationException
    def sourceNode = nodes[0]
    def targetNode = nodes[1]

    LogUtils.info("Selected nodes for comparison: ${sourceNode.text} and ${targetNode.text}")

    // 3. Get Comparison Type from User
    def dialogMessage = "Comparing selected nodes:\n'${sourceNode.text}'\nand\n'${targetNode.text}'\nEnter comparison type:"
    def defaultComparisonTypes = ["Pros and Cons", "Compare and Contrast", "Strengths vs Weaknesses", "Advantages and Disadvantages"]
    def comparisonTypesConfigKey = "promptLlmAddOn.comparisonTypes"

    String comparisonType = showComparisonDialog(
        ui, 
        config, 
        sourceNode.delegate, 
        dialogMessage,
        defaultComparisonTypes,
        comparisonTypesConfigKey
    )

    if (comparisonType == null || comparisonType.trim().isEmpty()) {
        LogUtils.info("User cancelled comparison input.")
        return
    }
    comparisonType = comparisonType.trim()

    // 4. Show Progress Dialog
    def progressMessage = "Generating '${comparisonType}' analysis framework..."
    def dialog = DialogHelper.createProgressDialog(ui, "Analyzing Nodes with LLM...", progressMessage)
    dialog.setVisible(true)

    // 6. Run API Calls in Background Thread
    def workerThread = new Thread({
        String errorMessage = null

        try {
            // --- Generate Comparative Dimension with Validation ---
            def dimensionData = DimensionGenerator.generateDimension(
                make_api_call.curry(apiConfig.provider, apiConfig.apiKey),
                apiConfig.model,
                messages.dimensionSystemTemplate, 
                comparisonType
            )
            def (pole1, pole2) = [dimensionData.pole1, dimensionData.pole2]
            def comparativeDimension = "${pole1} vs ${pole2}"
            LogUtils.info("Generated comparative dimension: ${comparativeDimension}")
            
            // --- Prepare Prompts with Generated Dimension ---
            LogUtils.info("CompareNodes: Final userMessageTemplate for expansion:\n---\n${compareNodesUserMessageTemplate}\n---")
            
            def sourceUserPrompt = PromptBuilder.buildComparisonPrompt(
                sourceNode, targetNode, 
                compareNodesUserMessageTemplate,
                comparativeDimension, pole1, pole2
            )
            LogUtils.info("CompareNodes: Source User Prompt:\n${sourceUserPrompt}")
            
            def targetUserPrompt = PromptBuilder.buildComparisonPrompt(
                targetNode, sourceNode,
                compareNodesUserMessageTemplate,
                comparativeDimension, pole1, pole2 
            )
            LogUtils.info("CompareNodes: Target User Prompt:\n${targetUserPrompt}")
            
            // Update progress dialog
            UiHelper.updateDialogMessage(dialog, "Analyzing '${sourceNode.text}' and '${targetNode.text}' using '${comparativeDimension}' framework...")

            // --- Prepare System Prompts ---
            LogUtils.info("CompareNodes: Final systemMessageTemplate for expansion:\n---\n${systemMessageTemplate}\n---")

            def sourceSystemPrompt = PromptBuilder.buildSystemPrompt(
                sourceNode, targetNode,
                systemMessageTemplate,
                comparativeDimension, pole1, pole2
            )
            LogUtils.info("CompareNodes: Source System Prompt:\n${sourceSystemPrompt}")

            def targetSystemPrompt = PromptBuilder.buildSystemPrompt(
                targetNode, sourceNode,
                systemMessageTemplate,
                comparativeDimension, pole1, pole2
            )
            LogUtils.info("CompareNodes: Target System Prompt:\n${targetSystemPrompt}")

            // --- Define Helper Closure for API Call with Retry ---
            def callApiAndParseWithRetry = { String nodeName, Map initialPayload ->
                Map analysisResult = [error: "Initial error state"] // Default error state
                String lastRawApiResponse = "" // Store last raw response for retry message
                final int MAX_RETRIES = 2 // Allow up to 2 retries (3 attempts total)

                for (int attempt = 1; attempt <= MAX_RETRIES + 1; attempt++) {
                    LogUtils.info("Attempt ${attempt}/${MAX_RETRIES + 1} for node '${nodeName}'")
                    def currentPayload = initialPayload // Use initial payload for first attempt

                    // --- Prepare Payload for Retry Attempts ---
                    if (attempt > 1) {
                        // Update dialog for retry
                        UiHelper.updateDialogMessageThreadSafe(dialog, "Attempt ${attempt}: LLM response format incorrect for '${nodeName}'. Retrying...")

                        // Construct retry message specifically asking for ONLY JSON
                        def retryUserMessage = """
                        Your previous response was not valid JSON or contained extra text.
                        Please strictly adhere to the requested JSON format.
                        DO NOT include any text before or after the JSON structure (like 'Here is the JSON...' or explanations).
                        Provide ONLY the JSON object starting with '{' and ending with '}'.

                        Previous invalid response snippet (first 200 chars):
                        ${lastRawApiResponse.take(200)}...

                        Please try again, providing only the valid JSON for the original request:
                        '$comparativeDimension' ($pole1 vs $pole2) for '$nodeContent' and '$otherNodeContent'.
                        """.stripIndent().trim() // Use trim()

                        // Modify the payload for retry: Add previous assistant response and new user instruction
                        def retryMessages = new ArrayList<>(initialPayload.messages)
                        // Add the *entire* previous raw response as the assistant's turn
                        retryMessages.add([role: 'assistant', content: lastRawApiResponse])
                        retryMessages.add([role: 'user', content: retryUserMessage])

                        // Create a new payload map for the retry call
                        currentPayload = new HashMap<>(initialPayload) // Create a copy
                        currentPayload.messages = retryMessages
                        // Ensure response_format is still set if applicable
                        currentPayload['response_format'] = initialPayload['response_format']
                        currentPayload = currentPayload.findAll { key, value -> value != null } // Clean nulls again

                        LogUtils.info("Retry attempt ${attempt} for '${nodeName}'. Sending correction request.")
                    }

                    // --- Make API Call ---
                    def apiResponse = make_api_call(apiConfig.provider, apiConfig.apiKey, currentPayload)
                    if (apiResponse == null || apiResponse.isEmpty()) {
                        analysisResult = [error: "Received empty or null response (Attempt ${attempt})"]
                        lastRawApiResponse = "" // Reset raw response
                        if (attempt > MAX_RETRIES) break // Exit loop if max retries reached
                        Thread.sleep(500) // Small delay before retry
                        continue // Go to next retry attempt
                    }
                    lastRawApiResponse = apiResponse // Store for potential next retry

                    // --- Process Response ---
                    try {
                        // Try parsing the response - ResponseProcessor calls ResponseParser internally
                        analysisResult = ResponseProcessor.parseApiResponse(apiResponse, pole1, pole2)
                        // Check if the *parsed* result contains an error key (set by ResponseParser on failure)
                        if (!analysisResult.error) {
                            LogUtils.info("Successfully parsed response for '${nodeName}' on attempt ${attempt}")
                            return analysisResult // Success! Exit the retry loop and return result.
                        } else {
                            // Parsing failed, error message is in analysisResult.error
                            LogUtils.warn("Parsing failed on attempt ${attempt} for '${nodeName}': ${analysisResult.error}")
                            // Loop will continue if retries remain
                        }
                    } catch (Exception parseEx) {
                        // Catch unexpected errors during parsing itself
                        LogUtils.warn("Unexpected processing/parsing exception on attempt ${attempt} for '${nodeName}': ${parseEx.message}")
                        analysisResult = [error: "Unexpected processing error: ${parseEx.message} (Attempt ${attempt})"]
                        // Loop will continue if retries remain
                    }

                    // If we reach here and it's the last attempt, the loop will terminate
                    if (attempt > MAX_RETRIES) {
                         LogUtils.warn("Max retries reached for '${nodeName}'. Final error: ${analysisResult.error ?: 'Unknown parsing failure'}")
                    }
                    Thread.sleep(500) // Small delay before retry
                }
                // If loop finishes without success, return the last error state
                return analysisResult
            }

            // --- Prepare Initial Payloads ---
            // Use response_format for OpenAI JSON mode where possible
            def responseFormat = (apiConfig.provider == 'openai' && apiConfig.model.contains("gpt")) ? [type: "json_object"] : null

            Map<String, Object> sourcePayloadMap = [
                'model': apiConfig.model,
                'messages': [
                    [role: 'system', content: sourceSystemPrompt],
                    [role: 'user', content: sourceUserPrompt]
                ],
                'temperature': apiConfig.temperature,
                'max_tokens': apiConfig.maxTokens,
                'response_format': responseFormat
            ].findAll { key, value -> value != null }

            Map<String, Object> targetPayloadMap = [
                'model': apiConfig.model,
                'messages': [
                    [role: 'system', content: targetSystemPrompt],
                    [role: 'user', content: targetUserPrompt]
                ],
                'temperature': apiConfig.temperature,
                'max_tokens': apiConfig.maxTokens,
                'response_format': responseFormat
            ].findAll { key, value -> value != null }

            // --- Call APIs using the Retry Helper ---
            UiHelper.updateDialogMessageThreadSafe(dialog, "Requesting analysis for '${sourceNode.text}'...")
            def sourceAnalysis = callApiAndParseWithRetry(sourceNode.text, sourcePayloadMap)
            if (sourceAnalysis.error) {
                throw new Exception("Failed to get valid analysis for '${sourceNode.text}' after multiple attempts: ${sourceAnalysis.error}")
            }
            LogUtils.info("Source Node Analysis received and parsed successfully.")

            UiHelper.updateDialogMessageThreadSafe(dialog, "Requesting analysis for '${targetNode.text}'...")
            def targetAnalysis = callApiAndParseWithRetry(targetNode.text, targetPayloadMap)
            if (targetAnalysis.error) {
                throw new Exception("Failed to get valid analysis for '${targetNode.text}' after multiple attempts: ${targetAnalysis.error}")
            }
            LogUtils.info("Target Node Analysis received and parsed successfully.")

            // Add validation for pole consistency (using the poles from the parsed results)
            // Check if dimension exists before accessing poles
            if (!sourceAnalysis?.dimension?.pole1 || !sourceAnalysis?.dimension?.pole2 ||
                !targetAnalysis?.dimension?.pole1 || !targetAnalysis?.dimension?.pole2 ||
                sourceAnalysis.dimension.pole1 != targetAnalysis.dimension.pole1 ||
                sourceAnalysis.dimension.pole2 != targetAnalysis.dimension.pole2) {
                 // Log the actual dimensions found for debugging
                 LogUtils.warn("Source dimension: ${sourceAnalysis?.dimension}")
                 LogUtils.warn("Target dimension: ${targetAnalysis?.dimension}")
                 throw new Exception("Mismatched or missing comparison dimensions between concepts. Check LLM response format.")
            }

            // --- Update Map on EDT ---
            UiHelper.disposeDialog(dialog) // Close progress dialog first
            if (sourceAnalysis.isEmpty() && targetAnalysis.isEmpty()) {
                UiHelper.showInformationMessage(ui, "The LLM analysis did not yield structured results for either node.")
            } else {
                try {
                    MapUpdater.createComparisonStructure(
                        sourceNode,
                        targetNode,
                        sourceAnalysis,
                        targetAnalysis,
                        comparativeDimension,
                        pole1,
                        pole2,
                        apiConfig.model,
                        addModelTagRecursively
                    )

                    UiHelper.showInformationMessage(ui, "Central comparison node using '${comparativeDimension}' created.")

                } catch (Exception e) {
                    LogUtils.warn("Error creating central comparison node structure on EDT: ${e.message}", e)
                    UiHelper.showErrorMessage(ui, "Failed to add central comparison node to the map. Check logs. Error: ${e.message}")
                }
            }

        } catch (Exception e) {
            LogUtils.warn("LLM Comparison failed: ${e.message}", e)
            errorMessage = "Comparison Error: ${e.message.split('\n').head()}"
            // Ensure dialog is closed and error shown on EDT
            UiHelper.disposeDialog(dialog)
            UiHelper.showErrorMessage(ui, errorMessage)
        }
    })
    workerThread.start()

} catch (Exception e) {
    // Handle all errors with a simple message
    UiHelper.showErrorMessage(ui, e.message)
    LogUtils.warn("Error in CompareConnectedNodes: ${e.message}", e)
}

LogUtils.info("CompareConnectedNodes script finished.")

