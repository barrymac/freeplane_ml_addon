import com.barrymac.freeplane.addons.llm.services.ImageAttachmentHandler
import com.barrymac.freeplane.addons.llm.ui.ImageDialogueHelper
import org.freeplane.core.util.LogUtils

import javax.swing.*

import static com.barrymac.freeplane.addons.llm.ui.DialogHelper.createProgressDialog
import static com.barrymac.freeplane.addons.llm.ui.UiHelper.showErrorMessage

// import com.barrymac.freeplane.addons.llm.api.ApiPayloadBuilder // TODO: Uncomment when implemented
// import com.barrymac.freeplane.addons.llm.api.ApiCallerFactory // TODO: Uncomment when implemented
// import com.barrymac.freeplane.addons.llm.ResponseParser // TODO: Uncomment when implemented

import static com.barrymac.freeplane.addons.llm.ui.UiHelper.showInformationMessage

// import com.barrymac.freeplane.addons.llm.utils.ImageDownloader // TODO: Uncomment when implemented

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


    // 3. Build Payload
    LogUtils.info("Building Novita payload...")
    // TODO: Replace placeholder with:
    // String jsonPayload = ApiPayloadBuilder.buildNovitaImagePayload(prompt)
    // Example placeholder:
    String jsonPayload = """{"prompt": "${prompt}", "image_num": 4, "width": 512, "height": 512, "steps": 4}"""
    LogUtils.info("Built Novita payload (placeholder): ${jsonPayload}")

    // 4. Create API Caller
    LogUtils.info("Creating Novita API caller...")
    // TODO: Replace placeholder with:
    // Closure callNovitaApi = ApiCallerFactory.createNovitaImageCaller(apiConfig.novitaApiKey)
    // Replace the dummy closure with local image path
    Closure callNovitaApi = { String payload ->
        LogUtils.info("Placeholder: Simulating Novita API call")
        // Return paths to bundled image
        return """
        {
          "images": [
            { "image_url": "images/placeholder1.png", "image_type": "png" },
            { "image_url": "images/placeholder2.png", "image_type": "png" },
            { "image_url": "images/placeholder3.png", "image_type": "png" },
            { "image_url": "images/placeholder4.png", "image_type": "png" }
          ],
          "task": { "task_id": "dummy-task-id" }
        }
        """
    }
    LogUtils.info("Created Novita API caller (placeholder).")

    // 5. Call API (with progress indication)
    LogUtils.info("Showing progress dialog...")
    JDialog progressDialog = createProgressDialog(ui, "Generating Image", "Contacting Novita.ai API...")
    String rawApiResponse // Declare here to be accessible in finally block if needed
    try {
        progressDialog?.visible = true
        LogUtils.info("Progress dialog shown.")
        rawApiResponse = callNovitaApi(jsonPayload) // Placeholder call
        LogUtils.info("Received raw API response (placeholder).")
    } finally {
        progressDialog?.dispose()
        LogUtils.info("Progress dialog disposed.")
    }

    // 6. Parse Response
    LogUtils.info("Parsing API response...")
    // TODO: Replace placeholder with:
    // List<String> imageUrls = ResponseParser.parseNovitaImageResponse(rawApiResponse)
    // Example placeholder:
    List<String> imageUrls = [
            "/images/placeholder1.png",
            "/images/placeholder2.png",
            "/images/placeholder3.png",
            "/images/placeholder4.png"
    ]
    if (imageUrls.isEmpty()) {
        showErrorMessage(ui, "The API did not return any image URLs. Check the logs for details.")
        LogUtils.error("API response did not contain any image URLs.")
        return
    }
    LogUtils.info("Parsed image URLs (placeholder): ${imageUrls}")

    // 7. Show Selection Dialog
    LogUtils.info("Showing image selection dialog...")
    // Enhanced downloader function that handles both resources and URLs
    Closure downloader = { String url ->
        try {
            LogUtils.info("Loading image from: ${url}")
            // Handle bundled resources
            if (url.startsWith("/")) {
                // Use the controller's classloader and the original path with leading slash
                def classLoader = c.class.classLoader
                LogUtils.info("Attempting to load resource '${url}' using classloader: ${classLoader}")
                def imageStream = classLoader.getResourceAsStream(url) // Use original URL with '/'
                if (!imageStream) {
                    // Try without leading slash as a fallback for ClassLoader access
                    def resourcePathNoSlash = url.startsWith('/') ? url.substring(1) : url
                    LogUtils.warn("Resource '${url}' not found with leading slash, trying '${resourcePathNoSlash}'")
                    imageStream = classLoader.getResourceAsStream(resourcePathNoSlash)
                }

                if (!imageStream) {
                     // If still not found, try relative to ImageDialogueHelper class again, but with leading slash
                     LogUtils.warn("Resource still not found with controller classloader, trying ImageDialogueHelper.class.getResourceAsStream('${url}')")
                     imageStream = ImageDialogueHelper.class.getResourceAsStream(url) // Requires leading slash
                }

                if (!imageStream) {
                    // Final attempt: Use the script's own class directly
                    LogUtils.warn("Resource still not found, trying getClass().getResourceAsStream('${url}')")
                    imageStream = getClass().getResourceAsStream(url) // Requires leading slash
                }

                if (!imageStream) {
                    // Give up if none worked
                    throw new FileNotFoundException("Bundled image not found using multiple classloaders/paths for: ${url}")
                }
                def bytes = imageStream.bytes
                LogUtils.info("Successfully loaded ${bytes.length} bytes from resource: ${url}")
                return bytes
            }
            // Handle real URLs (for future API integration)
            else {
                def connection = new URL(url).openConnection()
                connection.connectTimeout = 10000 // 10 seconds
                connection.readTimeout = 30000    // 30 seconds
                def bytes = connection.inputStream.bytes
                LogUtils.info("Successfully downloaded ${bytes.length} bytes from URL: ${url}")
                return bytes
            }
        } catch (Exception e) {
            LogUtils.severe("Error loading image: ${e.message}")
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


    // 8. Process Selection
    if (selectedUrl) {
        LogUtils.info("Showing download progress dialog...")
        JDialog downloadProgress = createProgressDialog(ui, "Downloading Image", "Downloading selected image...")
        try {
            downloadProgress?.visible = true
            LogUtils.info("Download progress dialog shown.")

            LogUtils.info("Downloading image bytes...")
            // TODO: Replace placeholder downloader call with actual download:
            // byte[] selectedImageBytes = ImageDownloader.downloadImageBytes(selectedUrl)
            byte[] selectedImageBytes = downloader(selectedUrl) // Use placeholder downloader
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
