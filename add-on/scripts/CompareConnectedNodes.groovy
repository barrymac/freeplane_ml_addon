import com.barrymac.freeplane.addons.llm.*
import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException
import org.freeplane.plugin.script.proxy.NodeProxy

import javax.swing.*
import java.awt.*


// --- Initialize Core Components ---
// Create instances of required classes
def provider = apiConfig.provider
def apiKey = apiConfig.apiKey
def apiCaller = ApiCallerFactory.createApiCaller(provider, apiKey)
if (!apiCaller) {
    throw new Exception("Failed to create API caller for provider: ${provider}")
}
NodeTagger nodeTagger = new NodeTagger()

// Get method references for commonly used functions
Closure make_api_call = apiCaller.&make_api_call
Closure getBindingMap = MessageExpander.&getBindingMap
Closure parseAnalysis = ResponseParser.&parseAnalysis
Closure addModelTagRecursively = nodeTagger.&addModelTagRecursively

// Load configuration and messages
Map<String, Object> apiConfig = ConfigManager.loadBaseConfig(config)
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
    List<NodeProxy> selectedNodes = (List<NodeProxy>) c.selecteds
    // Use the static method directly via the class obtained from deps
    NodeProxy[] nodes = NodeHelper.validateSelectedNodes(selectedNodes) // This might throw ValidationException
    NodeProxy sourceNode = nodes[0]
    NodeProxy targetNode = nodes[1]

    logger.info("Selected nodes for comparison: ${sourceNode.text} and ${targetNode.text}")

    // 3. Get Comparison Type from User
    def dialogMessage = "Comparing selected nodes:\n'${sourceNode.text}'\nand\n'${targetNode.text}'\nEnter comparison type:"
    def defaultComparisonTypes = ["Pros and Cons", "Compare and Contrast", "Strengths vs Weaknesses", "Advantages and Disadvantages"]
    def comparisonTypesConfigKey = "promptLlmAddOn.comparisonTypes"

    String comparisonType = DialogHelper.showComparisonDialog(
        ui, 
        config, 
        sourceNode.delegate, 
        dialogMessage,
        defaultComparisonTypes,
        comparisonTypesConfigKey
    )

    if (comparisonType == null || comparisonType.trim().isEmpty()) {
        logger.info("User cancelled comparison input.")
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
            // Get provider from config
            def provider = apiConfig.provider
            
            // --- Generate Comparative Dimension with Validation ---
            def dimensionData = DimensionGenerator.generateDimension(
                make_api_call.curry(apiConfig.provider, apiKey),
                apiConfig.model,
                messages.dimensionSystemTemplate, 
                comparisonType
            )
            def (pole1, pole2) = [dimensionData.pole1, dimensionData.pole2]
            def comparativeDimension = "${pole1} vs ${pole2}"
            logger.info("Generated comparative dimension: ${comparativeDimension}")
            
            // --- Prepare Prompts with Generated Dimension ---
            logger.info("CompareNodes: Final userMessageTemplate for expansion:\n---\n${compareNodesUserMessageTemplate}\n---")
            
            def sourceUserPrompt = PromptBuilder.buildComparisonPrompt(
                sourceNode, targetNode, 
                compareNodesUserMessageTemplate,
                comparativeDimension, pole1, pole2
            )
            logger.info("CompareNodes: Source User Prompt:\n${sourceUserPrompt}")
            
            def targetUserPrompt = PromptBuilder.buildComparisonPrompt(
                targetNode, sourceNode,
                compareNodesUserMessageTemplate,
                comparativeDimension, pole1, pole2 
            )
            logger.info("CompareNodes: Target User Prompt:\n${targetUserPrompt}")
            
            // Update progress dialog
            SwingUtilities.invokeLater {
                dialog.setMessage("Analyzing '${sourceNode.text}' and '${targetNode.text}' using '${comparativeDimension}' framework...")
            }

            // --- Call API for Source Node ---
            Map<String, Object> sourcePayloadMap = [
                'model': apiConfig.model as String,
                'messages': [
                    [role: 'system' as String, content: systemMessageTemplate as String],
                    [role: 'user' as String, content: sourceUserPrompt as String]
                ] as List<Map<String,String>>,
                'temperature': apiConfig.temperature as Number,
                'max_tokens': apiConfig.maxTokens as Integer
            ]
            logger.info("Requesting analysis for source node: ${sourceNode.text}")
            // Use the unified API call function from deps
            def sourceApiResponse = make_api_call(sourcePayloadMap)

            if (sourceApiResponse == null || sourceApiResponse.isEmpty()) {
                throw new Exception("Received empty or null response for source node.")
            }

            // --- Call API for Target Node ---
            def targetPayloadMap = [
                'model': apiConfig.model,
                'messages': [
                    [role: 'system', content: systemMessageTemplate],
                    [role: 'user', content: targetUserPrompt]
                ],
                'temperature': apiConfig.temperature,
                'max_tokens': apiConfig.maxTokens
            ]
            logger.info("Requesting analysis for target node: ${targetNode.text}")
            // Use the unified API call function from deps
            def targetApiResponse = make_api_call(targetPayloadMap)

            if (targetApiResponse == null || targetApiResponse.isEmpty()) {
                throw new Exception("Received empty or null response for target node.")
            }

            // --- Process Responses ---
            def jsonSlurper = new JsonSlurper()

            def sourceAnalysis = ResponseProcessor.parseApiResponse(sourceApiResponse, pole1, pole2)
            logger.info("Source Node Analysis received and parsed")
            
            def targetAnalysis = ResponseProcessor.parseApiResponse(targetApiResponse, pole1, pole2)
            logger.info("Target Node Analysis received and parsed")

            // Add validation for pole consistency
            if (sourceAnalysis.dimension.pole1 != targetAnalysis.dimension.pole1 ||
                sourceAnalysis.dimension.pole2 != targetAnalysis.dimension.pole2) {
                throw new Exception("Mismatched comparison dimensions between concepts")
            }

            // --- Update Map on EDT ---
            SwingUtilities.invokeLater {
                dialog.dispose() // Close progress dialog first
                if (sourceAnalysis.isEmpty() && targetAnalysis.isEmpty()) {
                    ui.informationMessage("The LLM analysis did not yield structured results for either node.")
                } else {
                    try {
                        MapUpdater.createComparisonStructure(
                            sourceNode,
                            targetNode,
                            sourceAnalysis,
                            targetAnalysis,
                            comparativeDimension,
                            apiConfig.model,
                            addModelTagRecursively
                        )

                        ui.informationMessage("Central comparison node using '${comparativeDimension}' created.")

                        // --- END NEW LOGIC ---

                    } catch (Exception e) {
                        logger.warn("Error creating central comparison node structure on EDT", e)
                        ui.errorMessage("Failed to add central comparison node to the map. Check logs. Error: ${e.message}")
                    }
                }
            }

        } catch (Exception e) {
            logger.warn("LLM Comparison failed", e)
            errorMessage = "Comparison Error: ${e.message.split('\n').head()}"
            // Ensure dialog is closed and error shown on EDT
            SwingUtilities.invokeLater {
                dialog.dispose()
                ui.errorMessage(errorMessage)
            }
        }
    })
    workerThread.start()

} catch (Exception e) {
    // Handle all errors with a simple message
    ui.errorMessage(e.message)
    // Use SLF4J logging
    logger.warn("Error in CompareConnectedNodes", e)
}

