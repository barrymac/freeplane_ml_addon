import com.barrymac.freeplane.addons.llm.api.ApiRequest
import com.barrymac.freeplane.addons.llm.prompts.Message
import com.barrymac.freeplane.addons.llm.ConfigManager
import com.barrymac.freeplane.addons.llm.services.ImageAttachmentHandler
import com.barrymac.freeplane.addons.llm.services.ResourceLoaderService
import com.barrymac.freeplane.addons.llm.ui.ImageDialogueHelper
import com.barrymac.freeplane.addons.llm.ui.PromptEditor
import com.barrymac.freeplane.addons.llm.prompts.MessageExpander
import org.freeplane.core.util.LogUtils

import javax.swing.*

import static com.barrymac.freeplane.addons.llm.ui.DialogHelper.createProgressDialog
import static com.barrymac.freeplane.addons.llm.ui.UiHelper.showErrorMessage
import static com.barrymac.freeplane.addons.llm.ui.UiHelper.showInformationMessage

import com.barrymac.freeplane.addons.llm.api.ApiPayloadBuilder
import com.barrymac.freeplane.addons.llm.api.ApiCallerFactory
import com.barrymac.freeplane.addons.llm.ResponseParser
import groovy.json.JsonBuilder
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
                3 // QUESTION_MESSAGE
        )

        // Validate input
        if (!novitaApiKey?.trim()) {
            LogUtils.info("User cancelled API key input")
            showInformationMessage(ui, "Image generation requires a valid API key")
            return
        }

        // Persist the key properly
        try {
            config.setProperty('novita.key', novitaApiKey.trim())
            config.save() // Persist changes to disk
            LogUtils.info("Novita API key saved to preferences")
            // Immediately update local variable with saved value
            novitaApiKey = config.getProperty('novita.key', '')
        } catch (Exception e) {
            LogUtils.severe("Failed to save API key: ${e.message}")
            showErrorMessage(ui, "Failed to save API key: ${e.message}")
            return
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
        return
    }
    String prompt = node.text?.trim() // Placeholder access
    if (!prompt) {
        showInformationMessage(ui, "The selected node has no text to use as an image prompt.")
        LogUtils.info("Selected node has no text.")
        return
    }
    LogUtils.info("Using prompt from node ${node.id}: '${prompt.take(100)}...'")


    // 3. Generate image prompt using LLM
    LogUtils.info("Generating image prompt via LLM...")
    
    // Get configured provider (default to 'openai' if not set)
    def llmProvider = config.getProperty('openai.api_provider', 'openai')
    def llmApiKey = config.getProperty('openai.key', '')
    
    // Handle missing key with provider-specific messaging
    if (!llmApiKey) {
        LogUtils.warn("${llmProvider} API key missing - prompting user")
        
        // Provider-specific help URLs
        def providerUrls = [
            'openai': 'https://platform.openai.com/api-keys',
            'openrouter': 'https://openrouter.ai/keys',
            'novita': 'https://novita.ai/account' // Though novita is only for images
        ]
        def helpUrl = providerUrls[llmProvider] ?: providerUrls.openai
    
        llmApiKey = ui.showInputDialog(
            ui.currentFrame,
            """<html>${llmProvider.toUpperCase()} API key required for prompt generation.<br>
            Get your key at <a href='${helpUrl}'>${llmProvider} website</a></html>""",
            "API Key Required",
            3
        )
        
        if (!llmApiKey?.trim()) {
            showInformationMessage(ui, "Prompt generation requires a valid API key")
            return
        }
        config.setProperty('openai.key', llmApiKey.trim())
        config.save() // Persist changes to disk
    }

    // Load system prompt from resources
    String systemPrompt
    try {
        systemPrompt = ResourceLoaderService.loadTextResource('/novitaSystemPrompt.txt')
        LogUtils.info("Loaded system prompt template from resources")
    } catch (Exception e) {
        LogUtils.warn("Could not load system prompt template: ${e.message}")
        systemPrompt = "You are an expert image prompt engineer. Enhance this prompt for AI image generation."
    }

    // Build LLM request
    def promptRequest = new ApiRequest(
        model: "gpt-3.5-turbo",
        messages: [
            new Message(role: 'system', content: systemPrompt),
            new Message(role: 'user', content: prompt)
        ],
        temperature: 0.7,
        maxTokens: 200
    )

    // Call LLM
    def (apiCaller, progressDialog, llmResponse) = [null, null, null]
    try {
        progressDialog = createProgressDialog(ui, "Generating Prompt", "Creating image description...")
        progressDialog.visible = true
        apiCaller = ApiCallerFactory.createApiCaller([ui: ui]).make_api_call
        llmResponse = apiCaller(llmProvider, llmApiKey, promptRequest)
    } catch (Exception e) {
        showErrorMessage(ui, "Prompt generation failed: ${e.message.split('\n').head()}")
        return
    } finally {
        progressDialog?.dispose()
    }

    // Parse LLM response
    String enhancedPrompt
    try {
        def json = new JsonSlurper().parseText(llmResponse)
        enhancedPrompt = json.choices[0].message.content.trim()
        LogUtils.info("LLM generated prompt: ${enhancedPrompt.take(100)}...")
    } catch (Exception e) {
        LogUtils.warn("Failed to parse LLM response, using original prompt")
        enhancedPrompt = prompt
    }
    
    // Show prompt editor with default params
    def initialParams = [
        steps: 4,
        width: 512,
        height: 512,
        imageNum: 4,
        seed: new Random().nextInt(Integer.MAX_VALUE) // Generate valid 32-bit seed
    ]
    
    // Load image user prompt template
    String userPromptTemplate = ResourceLoaderService.loadTextResource('/imageUserPrompt.txt')
    
    // Check for saved template
    def savedTemplate = ConfigManager.getUserProperty(config, 'savedImagePromptTemplate', '')
    LogUtils.info("Saved template check - exists: ${!savedTemplate.isEmpty()}")
    if (savedTemplate) {
        LogUtils.info("Loaded saved template:\n${savedTemplate.take(100)}...")
        userPromptTemplate = savedTemplate
    } else {
        LogUtils.info("Using default image prompt template")
    }
    
    // Create binding with generated prompt and all node context variables
    def binding = MessageExpander.createBinding(node, null, null, null, null) + [
        generatedPrompt: enhancedPrompt
    ]
    
    // Expand template with generated prompt and full context
    def initialTemplate = MessageExpander.expandTemplate(userPromptTemplate, binding)
    
    def edited = PromptEditor.showPromptEditor(ui, initialTemplate, initialParams, config)
    if (!edited) {
        LogUtils.info("User cancelled prompt editing")
        return
    }
    
    def (modifiedPrompt, params) = edited
    LogUtils.info("User edited prompt and parameters: steps=${params.steps}, dimensions=${params.width}x${params.height}, imageNum=${params.imageNum}")
    
    // Reuse existing binding from earlier in the script
    def expandedPrompt = MessageExpander.expandTemplate(modifiedPrompt, binding)
    LogUtils.info("Expanded prompt template with node context")
    
    // Build final prompt with system template
    String systemPromptTemplate = ResourceLoaderService.loadTextResource('/novitaSystemPrompt.txt')
    
    // Extract style parameters from the expanded prompt
    def stylePattern = ~/Style:\s*([^\n]+)/
    def detailsPattern = ~/Details:\s*([^\n]+)/
    def colorsPattern = ~/Colors:\s*([^\n]+)/
    def lightingPattern = ~/Lighting:\s*([^\n]+)/
    
    def styleMatcher = expandedPrompt =~ stylePattern
    def detailsMatcher = expandedPrompt =~ detailsPattern
    def colorsMatcher = expandedPrompt =~ colorsPattern
    def lightingMatcher = expandedPrompt =~ lightingPattern
    
    def styleValue = styleMatcher.find() ? styleMatcher.group(1).trim() : 'digital art'
    def detailsValue = detailsMatcher.find() ? detailsMatcher.group(1).trim() : 'high detail'
    def colorsValue = colorsMatcher.find() ? colorsMatcher.group(1).trim() : 'vibrant'
    def lightingValue = lightingMatcher.find() ? lightingMatcher.group(1).trim() : 'dramatic'
    
    String finalPrompt = MessageExpander.buildImagePrompt(
        expandedPrompt, 
        systemPromptTemplate,
        [
            dimension: 'visual concept',
            style: styleValue,
            details: detailsValue,
            colors: colorsValue,
            lighting: lightingValue
        ]
    )
    LogUtils.info("Built final prompt with system template and extracted parameters")
    
    // 4. Build payload map with user-edited values
    def payloadMap = ApiPayloadBuilder.buildNovitaImagePayload(
        finalPrompt, 
        params.steps,
        params.width,
        params.height,
        params.imageNum,
        params.seed
    )
    LogUtils.info("Built Novita payload: ${payloadMap}")

    // 5. Create API Caller 
    LogUtils.info("Creating Novita API caller...")
    Closure callNovitaApi = ApiCallerFactory.createApiCaller([ui: ui]).make_api_call.curry('novita', novitaApiKey)

    // 6. Call API (with progress indication)
    LogUtils.info("Calling Novita API...")
    JDialog novitaProgressDialog = createProgressDialog(ui, "Generating Image", "Creating images with Novita.ai...")
    String rawApiResponse // Declare here to be accessible in finally block if needed
    try {
        novitaProgressDialog.visible = true
        rawApiResponse = callNovitaApi(payloadMap) // Pass the map directly
        LogUtils.info("Novita API response received")
    } finally {
        novitaProgressDialog?.dispose()
        LogUtils.info("Progress dialog disposed.")
    }

    // 7. Parse Response
    LogUtils.info("Parsing API response...")
    List<String> imageUrls = ResponseParser.parseNovitaResponse(rawApiResponse)
    if (imageUrls.isEmpty()) {
        showErrorMessage(ui, "The API did not return any image URLs. Check the logs for details.")
        LogUtils.error("API response did not contain any image URLs.")
        return
    }
    LogUtils.info("Parsed image URLs (placeholder): ${imageUrls}")

    // 8. Show Selection Dialog
    LogUtils.info("Showing image selection dialog...")
    // Enhanced downloader function that handles both resources and URLs
    Closure downloader = { String url ->
        try {
            LogUtils.info("Downloader: Loading image from: ${url}")
            // Handle bundled resources via ResourceLoaderService
            if (url.startsWith("/")) {
                // Delegate loading to the dedicated service class within the add-on JAR
                return ResourceLoaderService.loadBundledResourceBytes(url)
            }
            // Handle real URLs (for future API integration)
            else {
                // Keep the existing URL download logic
                def connection = new URL(url).openConnection()
                connection.connectTimeout = 10000 // 10 seconds
                connection.readTimeout = 30000    // 30 seconds
                def bytes = connection.inputStream.bytes
                LogUtils.info("Downloader: Successfully downloaded ${bytes.length} bytes from URL: ${url}")
                return bytes
            }
        } catch (Exception e) {
            // Log the error more specifically from the downloader context
            // Avoid full stack trace for FileNotFound, as it's expected if the service fails
            LogUtils.severe("Downloader: Error loading image for URL '${url}': ${e.message}",
                    (e instanceof FileNotFoundException || e instanceof IllegalArgumentException) ? null : e)
            return null // Return null to allow dialog to show error
        }
    }

    // Show the image selection dialog
    String selectedUrl = ImageDialogueHelper.showImageSelectionDialog(ui, imageUrls, downloader)

    if (!selectedUrl) {
        LogUtils.info("User cancelled image selection or selection failed")
        showInformationMessage(ui, "Image selection cancelled")
        return
    }
    LogUtils.info("User selected image URL: ${selectedUrl}")


    // 9. Process Selection
    if (selectedUrl) {
        LogUtils.info("Showing download progress dialog...")
        JDialog downloadProgress = createProgressDialog(ui, "Downloading Image", "Downloading selected image...")
        try {
            downloadProgress?.visible = true
            LogUtils.info("Download progress dialog shown.")

            LogUtils.info("Downloading image bytes...")
            // Download the selected image
            byte[] selectedImageBytes = downloader(selectedUrl)
            if (!selectedImageBytes) {
                LogUtils.warn("No image bytes received for ${selectedUrl}")
                showErrorMessage(ui, "Failed to load selected image")
                return
            }
            // Only proceed if bytes are valid
            LogUtils.info("Downloaded ${selectedImageBytes.length} bytes for selected image (placeholder).")

            LogUtils.info("Determining filename and extension...")
            String baseName = ImageAttachmentHandler.sanitizeBaseName(node.text)
            String extension = ImageAttachmentHandler.getFileExtension(selectedUrl)

            LogUtils.info("Attaching image to node ${node.id} (baseName: ${baseName}, ext: ${extension})...")

            try {
                // Use the dedicated ImageAttachmentHandler to attach the image
                // Import needed at the top of the file
                ImageAttachmentHandler.attachImageToNode(node, selectedImageBytes, baseName, extension)

                LogUtils.info("Image successfully attached to node")
            } catch (Exception e) {
                LogUtils.severe("Failed to attach image: ${e.message}", e)
                throw e // Re-throw to be caught by outer catch block
            }

            LogUtils.info("Successfully attached image to node ${node.id}")
            showInformationMessage(ui, "Image added to node!")

        } catch (IOException e) {
            LogUtils.severe("Failed to download or attach image: ${e.message}", e)
            showErrorMessage(ui, "Failed to download or attach image: ${e.message}")
        } finally {
            downloadProgress?.dispose()
            LogUtils.info("Download progress dialog disposed.")
        }
    } else {
        LogUtils.info("User cancelled image selection or no URL was selected.")
    }

} catch (Exception e) { // Catching generic Exception for now
    LogUtils.severe("An unexpected error occurred in GenerateImage script: ${e.message}", e)
    showErrorMessage(ui, "An unexpected error occurred: ${e.message.split('\n').head()}")
} finally {
    LogUtils.info("GenerateImage script finished.")
}
