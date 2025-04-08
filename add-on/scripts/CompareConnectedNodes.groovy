import groovy.json.JsonSlurper
import groovy.text.SimpleTemplateEngine
import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException
import com.barrymac.freeplane.addons.llm.DependencyLoader

import javax.swing.*
import java.awt.*

// Helper function to parse generated dimension from LLM response
def parseGeneratedDimension(String response) {
    // More flexible regex pattern
    def pattern = ~/(?i)(Pole\s*1:\s*([^;]+?)\s*;\s*Pole\s*2:\s*([^\n]+?))\s*$/
    def matcher = pattern.matcher(response)
    
    if (matcher.find()) {
        def pole1 = matcher[0][2].trim().replaceAll(/["']/, '')
        def pole2 = matcher[0][3].trim().replaceAll(/["']/, '')
        return [pole1, pole2]
    }
    
    // Try alternative patterns if initial fails
    def altPatterns = [
        ~/([A-Z][\w\s]+?)\s*\/\/\s*([A-Z][\w\s]+)/,
        ~/(.+)\s+vs\s+(.+)/,
        ~/^([^;]+);([^;]+)$/
    ]
    
    for (p in altPatterns) {
        matcher = p.matcher(response)
        if (matcher.find() && matcher.groupCount() >= 2) {
            return [matcher[0][1].trim(), matcher[0][2].trim()]
        }
    }
    
    throw new LlmAddonException("""Invalid dimension format. Received: '$response'
        Expected format: 'Pole 1: [concept]; Pole 2: [concept]'""")
}

// --- Load Core Dependencies ---
// Import the compiled DependencyLoader

// Load all dependencies
// Call static method directly
def deps = DependencyLoader.loadDependencies(config, null, ui)

// Extract needed functions/classes from deps
def ConfigManager = deps.configManager
def make_api_call = deps.apiCaller.make_api_call
def getBindingMap = deps.messageExpander.getBindingMap
def parseAnalysis = deps.responseParser.&parseAnalysis
def DialogHelper = deps.dialogHelper
def NodeHelper = deps.nodeHelperUtils // Get the NodeHelperClass directly
def addAnalysisToNodeAsBranch = NodeHelper.&addAnalysisToNodeAsBranch // Get method reference from the class
def MessageLoader = deps.messageLoader
def addModelTagRecursively = deps.nodeTagger

// Load configuration using ConfigManager
def apiConfig = ConfigManager.loadBaseConfig(config)

// Load comparison messages using MessageLoader from deps
def messages = MessageLoader.loadComparisonMessages(config)
def systemMessageTemplate = messages.systemTemplate
def compareNodesUserMessageTemplate = messages.userTemplate

// --- Main Script Logic ---

// Wrap the entire script in a try-catch block for better error handling
try {
    // 1. Check API Key
    if (apiConfig.apiKey.isEmpty()) {
        if (provider == 'openrouter') {
            Desktop.desktop.browse(new URI("https://openrouter.ai/keys"))
        } else {
            Desktop.desktop.browse(new URI("https://platform.openai.com/account/api-keys"))
        }
        throw new Exception("API key is missing. Please configure it first via the LLM menu.")
    }

    // Check if templates are loaded
    if (systemMessageTemplate.isEmpty() || compareNodesUserMessageTemplate.isEmpty()) {
        throw new Exception("System message template or the dedicated compareNodesUserMessage.txt is missing or empty. Please check configuration or files.")
    }

    // 2. Get Selected Nodes and Validate (Use NodeHelper class from deps)
    def selectedNodes = c.selecteds
    // Use the static method directly via the class obtained from deps
    def (sourceNode, targetNode) = NodeHelper.validateSelectedNodes(selectedNodes) // This might throw ValidationException

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
            def dimensionPayload = [
                'model': apiConfig.model,
                'messages': [
                    [role: 'system', content: messages.dimensionSystemTemplate],
                    [role: 'user', content: "Create a focused comparative dimension for analyzing: ${comparisonType}"]
                ],
                'temperature': 0.2,
                'max_tokens': 100
            ]
            
            logger.info("Generating comparative dimension for: ${comparisonType}")
            
            def maxRetries = 2
            def attempts = 0
            def comparativeDimension = null
            def dimensionContent = null
            def pole1 = null
            def pole2 = null
            
            while (attempts <= maxRetries) {
                try {
                    def dimensionResponse = make_api_call(provider, apiConfig.apiKey, dimensionPayload)
                    dimensionContent = new JsonSlurper().parseText(dimensionResponse)?.choices[0]?.message?.content
                    (pole1, pole2) = parseGeneratedDimension(dimensionContent)
                    comparativeDimension = "${pole1} vs ${pole2}"
                    logger.info("Generated comparative dimension: ${comparativeDimension}")
                    break
                } catch (Exception e) {
                    attempts++
                    if (attempts > maxRetries) throw e
                    
                    // Add correction attempt
                    dimensionPayload.messages.add([role: 'assistant', content: dimensionContent])
                    dimensionPayload.messages.add([role: 'user', content: """
                        Format was incorrect. Please STRICTLY follow:
                        Pole 1: [2-3 words]; Pole 2: [2-3 words]
                        No other text. Just the poles in this format.
                    """])
                }
            }
            
            // --- Prepare Prompts with Generated Dimension ---
            logger.info("CompareNodes: Final userMessageTemplate for expansion:\n---\n${compareNodesUserMessageTemplate}\n---")
            
            // --- Prepare source node prompt ---
            def sourceBinding = getBindingMap(sourceNode, targetNode) // Pass both nodes
            // Remove incorrect assignment - comparisonType should be user input, not the generated dimension
            sourceBinding['comparativeDimension'] = comparativeDimension
            // Use existing poles from dimension generation
            sourceBinding['pole1'] = pole1
            sourceBinding['pole2'] = pole2
            logger.info("CompareNodes: Source Binding Map: ${sourceBinding}")
            logger.info("CompareNodes: Source Binding Map contains comparativeDimension? ${sourceBinding.containsKey('comparativeDimension')}")
            def sourceEngine = new SimpleTemplateEngine()
            def sourceUserPrompt = sourceEngine.createTemplate(compareNodesUserMessageTemplate).make(sourceBinding).toString()
            logger.info("CompareNodes: Source User Prompt:\n${sourceUserPrompt}")
            
            // --- Prepare target node prompt ---
            def targetBinding = getBindingMap(targetNode, sourceNode) // Pass both nodes
            // Use existing poles from dimension generation
            targetBinding['comparativeDimension'] = comparativeDimension
            targetBinding['pole1'] = pole1
            targetBinding['pole2'] = pole2
            logger.info("CompareNodes: Target Binding Map: ${targetBinding}")
            logger.info("CompareNodes: Target Binding Map contains comparativeDimension? ${targetBinding.containsKey('comparativeDimension')}")
            def targetEngine = new SimpleTemplateEngine()
            def targetUserPrompt = targetEngine.createTemplate(compareNodesUserMessageTemplate).make(targetBinding).toString()
            logger.info("CompareNodes: Target User Prompt:\n${targetUserPrompt}")
            
            // Update progress dialog
            SwingUtilities.invokeLater {
                dialog.setMessage("Analyzing '${sourceNode.text}' and '${targetNode.text}' using '${comparativeDimension}' framework...")
            }

            // --- Call API for Source Node ---
            def sourcePayloadMap = [
                'model': apiConfig.model,
                'messages': [
                    [role: 'system', content: systemMessageTemplate],
                    [role: 'user', content: sourceUserPrompt]
                ],
                'temperature': apiConfig.temperature,
                'max_tokens': apiConfig.maxTokens
            ]
            logger.info("Requesting analysis for source node: ${sourceNode.text}")
            // Use the unified API call function from deps
            def sourceApiResponse = make_api_call(provider, apiConfig.apiKey, sourcePayloadMap)

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
            def targetApiResponse = make_api_call(provider, apiConfig.apiKey, targetPayloadMap)

            if (targetApiResponse == null || targetApiResponse.isEmpty()) {
                throw new Exception("Received empty or null response for target node.")
            }

            // --- Process Responses ---
            def jsonSlurper = new JsonSlurper()

            def sourceJsonResponse = jsonSlurper.parseText(sourceApiResponse)
            def sourceResponseContent = sourceJsonResponse?.choices[0]?.message?.content
            // Add logging for raw source response
            logger.info("CompareConnectedNodes: Raw Source Response Content:\n---\n${sourceResponseContent}\n---")
            if (!sourceResponseContent?.trim()) throw new Exception("Empty content in source response. Model may have hit token limit.")

            def targetJsonResponse = jsonSlurper.parseText(targetApiResponse)
            def targetResponseContent = targetJsonResponse?.choices[0]?.message?.content
            // Add logging for raw target response
            logger.info("CompareConnectedNodes: Raw Target Response Content:\n---\n${targetResponseContent}\n---")
            if (!targetResponseContent?.trim()) throw new Exception("Empty content in target response. Model may have hit token limit.")

            logger.info("Source Node Analysis received, length: ${sourceResponseContent?.length() ?: 0}")
            logger.info("Target Node Analysis received, length: ${targetResponseContent?.length() ?: 0}")

            // Parse responses
            logger.info("CompareConnectedNodes: Parsing source response...")
            (pole1, pole2) = comparativeDimension.split(' vs ') // Remove 'def' keyword
            def sourceAnalysis = ResponseParser.parseJsonAnalysis(sourceResponseContent, pole1, pole2)
            if (sourceAnalysis.error) {
                throw new Exception("Source analysis error: ${sourceAnalysis.error}")
            }
            logger.info("CompareConnectedNodes: Parsed Source Analysis Map: ${sourceAnalysis}")

            logger.info("CompareConnectedNodes: Parsing target response...")
            def targetAnalysis = ResponseParser.parseJsonAnalysis(targetResponseContent, pole1, pole2)
            if (targetAnalysis.error) {
                throw new Exception("Target analysis error: ${targetAnalysis.error}")
            }
            logger.info("CompareConnectedNodes: Parsed Target Analysis Map: ${targetAnalysis}")

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
                        // --- NEW LOGIC: Create Central Comparison Node ---

                        // 1. Create the central node (position it logically, e.g., near the source node)
                        //    The map structure doesn't guarantee positioning, but creating it as a child
                        //    of the mind map root or near one of the nodes is common.
                        //    Let's create it as a sibling of the source node for simplicity.
                        def parentNode = sourceNode.parent // Or c.root if sourceNode is root
                        def centralNode = parentNode.createChild()
                        centralNode.text = "Comparison: ${comparativeDimension}" // Set concise title
                        centralNode.style.backgroundColorCode = '#E8E8FF' // Optional: Style central node

                        // 2. Create child nodes for each original idea under the central node
                        def centralSourceChild = centralNode.createChild(sourceNode.text)
                        def centralTargetChild = centralNode.createChild(targetNode.text)

                        // 3. Add the parsed analysis under the corresponding child using the new helper
                        if (!sourceAnalysis.isEmpty()) {
                            // Use the new helper function from NodeHelper class
                            NodeHelper.addJsonComparison(centralSourceChild, sourceAnalysis, 'concept_a')
                        } else {
                            centralSourceChild.createChild("(No analysis generated)")
                        }

                        if (!targetAnalysis.isEmpty()) {
                            // Use the new helper function from NodeHelper class
                            NodeHelper.addJsonComparison(centralTargetChild, targetAnalysis, 'concept_b')
                        } else {
                            centralTargetChild.createChild("(No analysis generated)")
                        }

                        // 4. Create visual links from central node to original ideas
                        centralNode.addConnectorTo(sourceNode)
                        centralNode.addConnectorTo(targetNode)
                        logger.info("Created connectors from comparison node to original ideas")

                        // 5. Apply LLM tag to the central node
                        if (addModelTagRecursively != null) {
                             try {
                                 // Tag the central node
                                 addModelTagRecursively(centralNode, apiConfig.model)
                                 logger.info("CompareNodes: Tag 'LLM:${apiConfig.model.replace('/', '_')}' applied to central comparison node: ${centralNode.text}")
                             } catch (Exception e) {
                                 logger.warn("Failed to apply node tagger function to central node: ${e.message}")
                             }
                        }

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
    // Use the classloader of a known compiled class from the JAR
    workerThread.setContextClassLoader(DependencyLoader.class.classLoader)
    workerThread.start()

} catch (Exception e) {
    // Handle all errors with a simple message
    ui.errorMessage(e.message)
    // Use SLF4J logging
    logger.warn("Error in CompareConnectedNodes", e)
}
