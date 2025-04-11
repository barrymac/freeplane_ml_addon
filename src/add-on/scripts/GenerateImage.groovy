import com.barrymac.freeplane.addons.llm.api.ApiRequest
import com.barrymac.freeplane.addons.llm.prompts.Message
import com.barrymac.freeplane.addons.llm.ConfigManager
import com.barrymac.freeplane.addons.llm.services.ResourceLoaderService
import com.barrymac.freeplane.addons.llm.ui.ImageSelectionDialog
import com.barrymac.freeplane.addons.llm.ui.ImagePromptEditor
import com.barrymac.freeplane.addons.llm.prompts.MessageExpander
import org.freeplane.core.util.LogUtils

import javax.swing.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.lang.InterruptedException // Explicit import for clarity

import static com.barrymac.freeplane.addons.llm.ui.UiHelper.showErrorMessage
import static com.barrymac.freeplane.addons.llm.ui.UiHelper.showInformationMessage

import com.barrymac.freeplane.addons.llm.api.ApiPayloadBuilder
import com.barrymac.freeplane.addons.llm.api.ApiCallerFactory
import com.barrymac.freeplane.addons.llm.ResponseParser
import groovy.json.JsonSlurper

// === Script Entry Point ===
LogUtils.info("GenerateImage script started.")

try {
    // 1. Load Configuration
    LogUtils.info("Loading configuration...")

    // Check for API key FIRST before any other operations
    def novitaApiKey = config.getProperty('novita.key', '')

    // Handle missing key with dialog that persists the value
    if (!novitaApiKey) {
        LogUtils.warn("Novita.ai API key missing - prompting user")

        // Use main Freeplane frame as parent instead of node
        novitaApiKey = ui.showInputDialog(
                ui.currentFrame, // More reliable parent component
                """<html>Novita.ai API key required for image generation.<br>
            Get your key at <a href='https://novita.ai/'>novita.ai</a></html>""",
                "API Key Required",
                JOptionPane.QUESTION_MESSAGE // Use constant
        )

        // Validate input
        if (!novitaApiKey?.trim()) {
            LogUtils.info("User cancelled API key input")
            showInformationMessage(ui, "Image generation requires a valid API key")
            return // Exit script
        }

        // Persist the key properly
        try {
            config.setProperty('novita.key', novitaApiKey.trim())
            // Freeplane config auto-saves, no need to call save()
            LogUtils.info("Novita API key saved to preferences")
            // Immediately update local variable with saved value
            novitaApiKey = config.getProperty('novita.key', '')
        } catch (Exception e) {
            LogUtils.severe("Failed to save API key: ${e.message}")
            showErrorMessage(ui, "Failed to save API key: ${e.message}")
            return // Exit script
        }
    }

    // Then proceed with node selection check
    LogUtils.info("Novita API Key verified")
    // Create a simple map for now, later use ApiConfig if needed
    def apiConfig = [novitaApiKey: novitaApiKey]

    // 2. Get Selected Node & Prompt
    LogUtils.info("Getting selected node...")
    def node = c.selected  // Use the Node proxy directly
    if (node == null) {
        showInformationMessage(ui, "Please select a node first to use as the image prompt.")
        LogUtils.info("No node selected.")
        return // Exit script
    }
    String prompt = node.text?.trim() // Placeholder access
    if (!prompt) {
        showInformationMessage(ui, "The selected node has no text to use as an image prompt.")
        LogUtils.info("Selected node has no text.")
        return // Exit script
    }
    LogUtils.info("Using prompt from node ${node.id}: '${prompt.take(100)}...'")


    // 3. Generate image prompt using LLM if needed (This part remains synchronous for now)
    LogUtils.info("Preparing image prompt...")
    String enhancedPrompt = ""
    def savedTemplate = ConfigManager.getUserProperty(config, 'savedImagePromptTemplate', '')
    LogUtils.info("Saved template check - exists: ${!savedTemplate.isEmpty()}")

    if (!savedTemplate) {
        LogUtils.info("No saved template - generating enhancement")
        def llmProvider = config.getProperty('openai.api_provider', 'openai')
        def llmApiKey = config.getProperty('openai.key', '')

        if (!llmApiKey) {
            LogUtils.warn("${llmProvider} API key missing - prompting user")
            def providerUrls = [
                'openai': 'https://platform.openai.com/api-keys',
                'openrouter': 'https://openrouter.ai/keys',
                'novita': 'https://novita.ai/account'
            ]
            def helpUrl = providerUrls[llmProvider] ?: providerUrls.openai

            llmApiKey = ui.showInputDialog(
                ui.currentFrame,
                """<html>${llmProvider.toUpperCase()} API key required for prompt generation.<br>
                Get your key at <a href='${helpUrl}'>${llmProvider} website</a></html>""",
                "API Key Required",
                JOptionPane.QUESTION_MESSAGE
            )

            if (!llmApiKey?.trim()) {
                showInformationMessage(ui, "Prompt generation requires a valid API key")
                return // Exit script
            }
            config.setProperty('openai.key', llmApiKey.trim())
        }

        String systemPrompt
        try {
            systemPrompt = ResourceLoaderService.loadTextResource('/novitaSystemPrompt.txt')
            LogUtils.info("Loaded system prompt template from resources")
        } catch (Exception e) {
            LogUtils.warn("Could not load system prompt template: ${e.message}")
            systemPrompt = "You are an expert image prompt engineer. Enhance this prompt for AI image generation."
        }

        def promptRequest = new ApiRequest(
            model: "gpt-3.5-turbo", // Consider making this configurable
            messages: [
                new Message(role: 'system', content: systemPrompt),
                new Message(role: 'user', content: prompt)
            ],
            temperature: 0.7,
            maxTokens: 200
        )

        // Call LLM (Synchronous for now, could be refactored to SwingWorker too if slow)
        def (apiCaller, progressDialog, llmResponse) = [null, null, null]
        try {
            // Using a simple progress dialog here, not the cancellable one yet
            progressDialog = ImageSelectionDialog.createProgressDialog(ui, "Generating Prompt", "Creating image description...")
            progressDialog.visible = true
            apiCaller = ApiCallerFactory.createApiCaller([ui: ui]).make_api_call
            llmResponse = apiCaller(llmProvider, llmApiKey, promptRequest)
        } catch (Exception e) {
            showErrorMessage(ui, "Prompt generation failed: ${e.message.split('\n').head()}")
            return // Exit script
        } finally {
            progressDialog?.dispose()
        }

        try {
            def json = new JsonSlurper().parseText(llmResponse)
            enhancedPrompt = json.choices[0].message.content.trim()
            LogUtils.info("LLM generated prompt: ${enhancedPrompt.take(100)}...")
        } catch (Exception e) {
            LogUtils.warn("Failed to parse LLM response, using original prompt: ${e.message}")
            enhancedPrompt = prompt // Fallback
        }
    } else {
        LogUtils.info("Using saved template from config")
    }

    // Show prompt editor
    def initialParams = [
        steps: 4, width: 256, height: 256, imageNum: 4,
        seed: new Random().nextInt(Integer.MAX_VALUE)
    ]
    String userPromptTemplate = ResourceLoaderService.loadTextResource('/imageUserPrompt.txt')
    if (savedTemplate?.trim()?.isEmpty()) savedTemplate = null
    def initialTemplate = savedTemplate ?: "${userPromptTemplate}\n\n${enhancedPrompt}".trim()
    def binding = MessageExpander.createBinding(node, null, null, null, null) + [generatedPrompt: enhancedPrompt]

    def edited = ImagePromptEditor.showPromptEditor(ui, initialTemplate, initialParams, config)
    if (!edited) {
        LogUtils.info("User cancelled prompt editing")
        return // Exit script
    }

    def (modifiedPrompt, params) = edited
    LogUtils.info("User edited prompt and parameters: steps=${params.steps}, dimensions=${params.width}x${params.height}, imageNum=${params.imageNum}")

    def expandedPrompt = MessageExpander.expandTemplate(modifiedPrompt, binding)
    LogUtils.info("Expanded prompt template with node context")

    String systemPromptTemplate = ResourceLoaderService.loadTextResource('/novitaSystemPrompt.txt')
    def styleMatcher = expandedPrompt =~ ~/Style:\s*([^\n]+)/
    def detailsMatcher = expandedPrompt =~ ~/Details:\s*([^\n]+)/
    def colorsMatcher = expandedPrompt =~ ~/Colors:\s*([^\n]+)/
    def lightingMatcher = expandedPrompt =~ ~/Lighting:\s*([^\n]+)/
    def styleValue = styleMatcher.find() ? styleMatcher.group(1).trim() : 'digital art'
    def detailsValue = detailsMatcher.find() ? detailsMatcher.group(1).trim() : 'high detail'
    def colorsValue = colorsMatcher.find() ? colorsMatcher.group(1).trim() : 'vibrant'
    def lightingValue = lightingMatcher.find() ? lightingMatcher.group(1).trim() : 'dramatic'

    String finalPrompt = MessageExpander.buildImagePrompt(
        expandedPrompt, systemPromptTemplate,
        [ dimension: 'visual concept', style: styleValue, details: detailsValue, colors: colorsValue, lighting: lightingValue ]
    )
    LogUtils.info("Built final prompt with system template and extracted parameters")

    // 4. Build payload map
    def payloadMap = ApiPayloadBuilder.buildNovitaImagePayload(
        finalPrompt, params.steps, params.width, params.height, params.imageNum, params.seed
    )
    LogUtils.info("Built Novita payload: ${payloadMap}")

    // 5. Create API Caller
    LogUtils.info("Creating Novita API caller...")
    Closure callNovitaApi = ApiCallerFactory.createApiCaller([ui: ui]).make_api_call.curry('novita', novitaApiKey)

    // --- SwingWorker Implementation ---
    LogUtils.info("Setting up SwingWorker for Novita API call...")
    final AtomicBoolean cancelled = new AtomicBoolean(false)
    JDialog progressDialog = null // Declare here for access in worker and finally

    // Define the cancellation action
    def cancelAction = {
        LogUtils.info("User requested cancellation.")
        cancelled.set(true)
        // Optionally, try to interrupt the worker thread if the underlying network client supports it
        // worker.cancel(true) // This might be needed depending on ApiCaller implementation
    }

    // Create the cancellable progress dialog
    progressDialog = ImageSelectionDialog.createCancellableProgressDialog(
            ui,
            "Generating Image",
            "Creating images with Novita.ai...",
            cancelAction // Pass the closure
    )

    // Define the SwingWorker
    def worker = new SwingWorker<String, Void>() {
        @Override
        protected String doInBackground() throws Exception {
            LogUtils.info("SwingWorker: doInBackground started.")
            // Check for cancellation before starting the network call
            if (cancelled.get()) {
                LogUtils.info("SwingWorker: Detected cancellation before API call.")
                throw new CancellationException("Operation cancelled by user before API call.")
            }

            LogUtils.info("SwingWorker: Calling Novita API...")
            String rawApiResponse = callNovitaApi(payloadMap) // Execute the blocking call
            LogUtils.info("SwingWorker: Novita API call finished.")

            // Check for cancellation immediately after the network call returns
            if (cancelled.get()) {
                LogUtils.info("SwingWorker: Detected cancellation after API call.")
                throw new CancellationException("Operation cancelled by user after API call.")
            }

            LogUtils.info("SwingWorker: doInBackground finished successfully.")
            return rawApiResponse
        }

        @Override
        protected void done() {
            LogUtils.info("SwingWorker: done() method started.")
            try {
                // Check cancellation flag first (more reliable than isCancelled() if only flag is set)
                if (cancelled.get()) {
                    LogUtils.info("SwingWorker: Task was cancelled by user (checked in done()).")
                    // No further action needed, dialog will be disposed in finally
                    return
                }

                // Call get() to retrieve result or exception
                String rawApiResponse = get() // This will throw exceptions if doInBackground failed or was cancelled

                // If get() succeeded without exception:
                LogUtils.info("SwingWorker: API call successful. Parsing response...")
                List<String> imageUrls = ResponseParser.parseNovitaResponse(rawApiResponse)
                if (imageUrls.isEmpty()) {
                    LogUtils.error("SwingWorker: API response did not contain any image URLs.")
                    showErrorMessage(ui, "The API did not return any image URLs. Check the logs for details.")
                    return
                }
                LogUtils.info("SwingWorker: Parsed image URLs: ${imageUrls}")

                // Proceed to image selection
                LogUtils.info("SwingWorker: Delegating image handling to ImageSelectionDialog...")
                // Ensure UI updates happen on EDT (handleImageSelection likely does this internally, but good practice)
                SwingUtilities.invokeLater {
                    ImageSelectionDialog.handleImageSelection(ui, imageUrls, node, config)
                }

            } catch (CancellationException e) {
                LogUtils.warn("SwingWorker: Operation cancelled by user.", e)
                // Optionally show a message, but often just closing the dialog is enough
                // showInformationMessage(ui, "Image generation cancelled.")
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() ?: e
                LogUtils.severe("SwingWorker: Error during Novita API call execution: ${cause.message}", cause)
                showErrorMessage(ui, "Image Generation Error: ${cause.message?.split('\n')?.head()}")
            } catch (InterruptedException e) {
                LogUtils.warn("SwingWorker: Task interrupted.", e)
                Thread.currentThread().interrupt() // Preserve interrupt status
                showErrorMessage(ui, "Image generation was interrupted.")
            } catch (Exception e) { // Catch any other unexpected errors during done() processing
                LogUtils.severe("SwingWorker: Unexpected error in done() method: ${e.message}", e)
                showErrorMessage(ui, "An unexpected error occurred: ${e.message?.split('\n')?.head()}")
            } finally {
                LogUtils.info("SwingWorker: Disposing progress dialog.")
                progressDialog?.dispose() // Ensure dialog is closed
            }
            LogUtils.info("SwingWorker: done() method finished.")
        }
    }

    // Show the progress dialog *before* starting the worker
    progressDialog.visible = true
    // Start the worker
    worker.execute()
    LogUtils.info("SwingWorker started.")

} catch (Exception e) { // Catch errors during setup before worker starts
    LogUtils.severe("An unexpected error occurred in GenerateImage script setup: ${e.message}", e)
    showErrorMessage(ui, "An unexpected setup error occurred: ${e.message.split('\n').head()}")
} finally {
    // This finally block executes *before* the SwingWorker finishes
    LogUtils.info("GenerateImage script main execution path finished (SwingWorker may still be running).")
}
