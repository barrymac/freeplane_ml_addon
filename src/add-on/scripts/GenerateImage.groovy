import com.barrymac.freeplane.addons.llm.api.ApiRequest
import com.barrymac.freeplane.addons.llm.prompts.Message
import com.barrymac.freeplane.addons.llm.ConfigManager
import com.barrymac.freeplane.addons.llm.services.ResourceLoaderService
import com.barrymac.freeplane.addons.llm.ui.ImageSelectionDialog
import com.barrymac.freeplane.addons.llm.ui.ImagePromptEditor
import com.barrymac.freeplane.addons.llm.prompts.MessageExpander
import org.freeplane.core.util.LogUtils

import javax.swing.*
// Removed: import java.util.concurrent.atomic.AtomicBoolean
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
    // --- ADD LOGGING ---
    LogUtils.info("Loaded savedImagePromptTemplate from config: '${savedTemplate}'")
    // --- END LOGGING ---
    // --- MODIFY LINE ---
    LogUtils.info("Saved template check - exists: ${!savedTemplate?.trim().isEmpty()}") // Use safe navigation and trim check

    // --- MODIFY CONDITION ---
    if (savedTemplate?.trim().isEmpty()) { // Check if it's null, empty, or whitespace
    // --- END MODIFY CONDITION ---
        // --- MODIFY LOG MESSAGE ---
        LogUtils.info("No valid saved template found - generating enhancement")
        // --- END MODIFY LOG MESSAGE ---
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
        // --- MODIFY LOG MESSAGE ---
        LogUtils.info("Using saved template from config, skipping LLM enhancement.") // Adjusted log
        // --- END MODIFY LOG MESSAGE ---
    }

    // --- MODIFIED PARAMETER PREPARATION ---
    // Prepare initial state for editor - Load parameters from config or use defaults
    def initialParams = [
        steps   : config.getProperty('imagegen.steps', '4').toInteger(),
        width   : config.getProperty('imagegen.width', '256').toInteger(),
        height  : config.getProperty('imagegen.height', '256').toInteger(),
        imageNum: config.getProperty('imagegen.imageNum', '4').toInteger(),
        seed    : new Random().nextInt(Integer.MAX_VALUE) // Seed is always random
    ]
    LogUtils.info("Prepared initial parameters for editor: ${initialParams}")
    // --- END MODIFIED PARAMETER PREPARATION ---

    String userPromptTemplate = ResourceLoaderService.loadTextResource('/imageUserPrompt.txt')
    // --- REPLACE LINE and ADD LOGGING ---
    boolean useSaved = savedTemplate && !savedTemplate.trim().isEmpty() // More explicit check
    LogUtils.info("Use saved template? ${useSaved}")
    def initialTemplate = useSaved ? savedTemplate : "${userPromptTemplate}\n\n${enhancedPrompt}".trim()
    LogUtils.info("Initial template for editor: '${initialTemplate}'")
    // --- END REPLACE LINE and LOGGING ---
    def binding = MessageExpander.createBinding(node, null, null, null, null) + [generatedPrompt: enhancedPrompt]

    // 4. Show Prompt Editor and Handle Result (Uses the map result now)
    LogUtils.info("Showing Image Prompt Editor...")
    // Pass initialParams, although editor now loads its own state from config
    Map editorResult = ImagePromptEditor.showPromptEditor(ui, initialTemplate, initialParams, config)

    // Handle actions based on editorResult
    switch (editorResult.action) {
        case 'Generate':
            LogUtils.info("User chose 'Generate'.")
            String finalUserTemplate = editorResult.prompt
            Map generationParams = editorResult.params // Contains validated params from editor

            // Expand the final template provided by the user
            LogUtils.info("Expanding final user template...")
            // --- USE TEMPLATE DIRECTLY ---
            String finalPrompt = MessageExpander.expandTemplate(finalUserTemplate, binding)
            LogUtils.info("Expanded final prompt: ${finalPrompt.take(100)}...")
            // --- END REVERT ---


            // 5. Build Novita Payload
            LogUtils.info("Building Novita payload...")
            def payloadMap = ApiPayloadBuilder.buildNovitaImagePayload(
                finalPrompt, // Use the directly expanded prompt
                generationParams.steps,
                generationParams.width,
                generationParams.height,
                generationParams.imageNum,
                generationParams.seed // Use the seed from the editor result
            )
            LogUtils.info("Built Novita payload: ${payloadMap}")

            // 6. Create API Caller Closure
            LogUtils.info("Creating Novita API caller closure...")
            Closure callNovitaApi = ApiCallerFactory.createApiCaller([ui: ui]).make_api_call.curry('novita', novitaApiKey)

            // --- SwingWorker Implementation ---
            LogUtils.info("Setting up SwingWorker for Novita API call...")
            SwingWorker<String, Void> worker = null // Declare worker variable here
            JDialog progressDialog = null // Declare progressDialog variable here

            // Define the cancellation action
            def cancelAction = {
                LogUtils.info("User requested cancellation.")
                if (worker != null && !worker.isDone()) {
                    LogUtils.info("Attempting to cancel SwingWorker (interrupting thread)...")
                    // --- Rely solely on worker.cancel ---
                    worker.cancel(true) // Pass true to attempt interruption
                } else {
                    LogUtils.info("Cancellation requested but worker is null or already done.")
                }
            }

            // Create the cancellable progress dialog
            progressDialog = ImageSelectionDialog.createProgressDialog(
                    ui,
                    "Generating Image",
                    "Creating images with Novita.ai...",
                    cancelAction
            )

            // Define the SwingWorker
            worker = new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    // --- LOGGING START 1 ---
                    LogUtils.info("SwingWorker: doInBackground executing on thread: " + Thread.currentThread().getName())
                    // --- LOGGING END 1 ---

                    // Check only for interruption *before* the call
                    if (Thread.currentThread().isInterrupted()) {
                        LogUtils.warn("SwingWorker: Thread interrupted BEFORE API call.") // More specific log
                        throw new InterruptedException("Operation cancelled by user before API call.")
                    }

                    LogUtils.info("SwingWorker: Calling Novita API...") // Log right before the call
                    String rawApiResponse
                    try {
                        // --- LOGGING START 2 ---
                        LogUtils.info("SwingWorker: Entering try block for callNovitaApi...")
                        // --- LOGGING END 2 ---
                        rawApiResponse = callNovitaApi(payloadMap) // Execute the blocking call
                        // --- LOGGING START 3 ---
                        LogUtils.info("SwingWorker: callNovitaApi returned successfully.")
                        // --- LOGGING END 3 ---
                    } catch (InterruptedException e) {
                        // If callNovitaApi itself throws InterruptedException
                        LogUtils.warn("SwingWorker: API call was interrupted (InterruptedException caught).", e)
                        Thread.currentThread().interrupt() // Re-interrupt the thread
                        throw e // Re-throw InterruptedException
                    } catch (Exception e) {
                        // Catch other exceptions during the call
                        LogUtils.severe("SwingWorker: Exception during API call: ${e.message}", e)
                        throw e // Re-throw other exceptions
                    }

                    // Check for interruption *after* the call returns successfully
                    if (Thread.currentThread().isInterrupted()) {
                        LogUtils.warn("SwingWorker: Thread interrupted AFTER API call.") // More specific log
                        throw new InterruptedException("Operation cancelled by user after API call.")
                    }

                    LogUtils.info("SwingWorker: doInBackground finished successfully.") // Changed log message
                    return rawApiResponse
                }

                @Override
                protected void done() {
                    LogUtils.info("SwingWorker: done() method started.")
                    // --- MODIFICATION START: Dispose dialog earlier ---
                    // Dispose the initial progress dialog *before* potentially showing another modal dialog
                    if (progressDialog != null) {
                        LogUtils.info("SwingWorker: Disposing initial progress dialog.")
                        progressDialog.dispose()
                        // progressDialog = null // Nullify to prevent double disposal in finally (removed as it's local to the outer scope)
                    }
                    // --- MODIFICATION END ---

                    try {
                        // Check worker's cancelled status first
                        if (isCancelled()) {
                            LogUtils.warn("SwingWorker: Task was cancelled by user (isCancelled() is true).")
                            // showInformationMessage(ui, "Image generation cancelled.") // Optional
                            return // Stop processing
                        }

                        // Call get() to retrieve result or propagate exceptions
                        String rawApiResponse = get() // Throws CancellationException if cancelled, ExecutionException if error in doInBackground

                        // If get() succeeded:
                        LogUtils.info("SwingWorker: API call successful. Parsing response...")
                        List<String> imageUrls = ResponseParser.parseNovitaResponse(rawApiResponse)
                        if (imageUrls.isEmpty()) {
                            LogUtils.error("SwingWorker: API response did not contain any image URLs.")
                            showErrorMessage(ui, "The API did not return any image URLs. Check logs.")
                            return
                        }
                        LogUtils.info("SwingWorker: Parsed image URLs: ${imageUrls.size()} found.")

                        // Proceed to image selection (already on EDT via done())
                        LogUtils.info("SwingWorker: Delegating image handling...")
                        // This call might show another modal dialog (image selection)
                        ImageSelectionDialog.handleImageSelection(ui, imageUrls, node, config)

                    } catch (CancellationException e) {
                        // Explicitly caught if worker was cancelled *before* get() was called or if get() throws it
                        LogUtils.warn("SwingWorker: Operation cancelled (CancellationException caught).", e)
                        // showInformationMessage(ui, "Image generation cancelled.") // Optional
                    } catch (ExecutionException e) {
                        Throwable cause = e.getCause() ?: e
                        // Check if the cause was an InterruptedException from doInBackground
                        if (cause instanceof InterruptedException) {
                            LogUtils.warn("SwingWorker: Operation interrupted during execution (InterruptedException cause).", cause)
                            Thread.currentThread().interrupt() // Preserve interrupt status
                            // showInformationMessage(ui, "Image generation cancelled.") // Optional
                        } else {
                            // Handle other execution errors
                            LogUtils.severe("SwingWorker: Error during API call execution: ${cause.message}", cause)
                            showErrorMessage(ui, "Image Generation Error: ${cause.message?.split('\n')?.head()}")
                        }
                    } catch (InterruptedException e) {
                        // If the done() thread itself is interrupted
                        LogUtils.warn("SwingWorker: done() method interrupted.", e)
                        Thread.currentThread().interrupt() // Preserve interrupt status
                        // showInformationMessage(ui, "Image generation cancelled.") // Optional
                    } catch (Exception e) {
                        LogUtils.severe("SwingWorker: Unexpected error in done() method: ${e.message}", e)
                        showErrorMessage(ui, "An unexpected error occurred: ${e.message?.split('\n')?.head()}")
                    } finally {
                        // --- MODIFICATION: Remove redundant disposal ---
                        // The progressDialog is now disposed *before* the try block
                        // LogUtils.info("SwingWorker: Disposing progress dialog.") // Already disposed above
                        // progressDialog?.dispose() // Already disposed above
                        // --- MODIFICATION END ---
                    }
                    LogUtils.info("SwingWorker: done() method finished.")
                }
            } // End SwingWorker definition

            // --- MODIFICATION START: Swap order ---
            // Start the worker FIRST
            worker.execute()
            LogUtils.info("SwingWorker started.")
            // THEN show the modal progress dialog
            progressDialog.visible = true
            LogUtils.info("Progress dialog is now visible.")
            // --- MODIFICATION END ---

            break // End of 'Generate' case

        // --- REMOVED 'Save' and 'Reset' cases ---

        case 'Cancel':
            LogUtils.info("User cancelled the Image Prompt Editor.")
            return // Stop script
            break // Keep break for consistency

        case 'Error':
            LogUtils.error("Image Prompt Editor returned an error: ${editorResult.message}")
            showErrorMessage(ui, "Could not open image editor: ${editorResult.message}")
            return // Stop script
            break // Keep break for consistency

        default:
            LogUtils.warn("Unknown action received from ImagePromptEditor: ${editorResult.action}")
            return // Stop script
            break // Keep break for consistency
    }

} catch (Exception e) { // Catch errors during setup before worker starts
    LogUtils.severe("An unexpected error occurred in GenerateImage script setup: ${e.message}", e)
    showErrorMessage(ui, "An unexpected setup error occurred: ${e.message.split('\n').head()}")
} finally {
    LogUtils.info("GenerateImage script main execution path finished (SwingWorker may still be running).")
}
