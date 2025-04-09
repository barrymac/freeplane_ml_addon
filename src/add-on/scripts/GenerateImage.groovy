// Freeplane & Core Java/Groovy
import org.freeplane.core.util.LogUtils
import org.freeplane.features.map.NodeModel

import javax.swing.*  // Imports all Swing classes including JDialog and JOptionPane

// LLM Add-on specific

import com.barrymac.freeplane.addons.llm.ui.UiHelper
import com.barrymac.freeplane.addons.llm.ui.DialogHelper
// import com.barrymac.freeplane.addons.llm.api.ApiPayloadBuilder // TODO: Uncomment when implemented
// import com.barrymac.freeplane.addons.llm.api.ApiCallerFactory // TODO: Uncomment when implemented
// import com.barrymac.freeplane.addons.llm.ResponseParser // TODO: Uncomment when implemented
// import com.barrymac.freeplane.addons.llm.maps.NodeOperations // TODO: Uncomment when implemented
// import com.barrymac.freeplane.addons.llm.utils.ImageDownloader // TODO: Uncomment when implemented

// === Script Entry Point ===
LogUtils.info("GenerateImage script started.")

try {
    // 1. Load Configuration
    LogUtils.info("Loading configuration...")
    // Load base config (though not strictly needed for Novita key yet)
    // ApiConfig baseApiConfig = ConfigManager.loadBaseConfig(config)
    def novitaApiKey = config.getProperty('novita.key', '')
    if (!novitaApiKey) {
        // Show input dialog instead of error message
        novitaApiKey = ui.showInputDialog(
            c.selected?.delegate, // Parent component
            "Please enter your Novita.ai API key:", // Message
            "API Key Required", // Title 
            3 // QUESTION_MESSAGE = 3, WARNING_MESSAGE = 2, PLAIN_MESSAGE = 1
        )
        
        if (!novitaApiKey?.trim()) {
            LogUtils.warn("User cancelled API key input")
            UiHelper.showInformationMessage(ui, "API key is required for image generation")
            return
        }
        
        // Save the key to preferences
        config.setProperty('novita.key', novitaApiKey)
        LogUtils.info("Novita API key saved to preferences")
    }
    LogUtils.info("Novita API Key loaded.")
    // Create a simple map for now, later use ApiConfig if needed
    def apiConfig = [novitaApiKey: novitaApiKey]

    // 2. Get Selected Node & Prompt
    LogUtils.info("Getting selected node...")
    def node = c.selected?.delegate  // Get the actual NodeModel from the proxy
    if (node == null) {
        UiHelper.showInformationMessage(ui, "Please select a node first to use as the image prompt.")
        LogUtils.info("No node selected.")
        return
    }
    String prompt = node.text?.trim() // Placeholder access
    if (!prompt) {
        UiHelper.showInformationMessage(ui, "The selected node has no text to use as an image prompt.")
        LogUtils.info("Selected node has no text.")
        return
    }
    LogUtils.info("Using prompt from node ${node.id}: '${prompt.take(100)}...'")

    LogUtils.info(">>> Skipping image generation steps (feature not fully implemented yet).")
    UiHelper.showInformationMessage(ui, "Image generation feature is under development.") // Optional user feedback
    return // Stop script execution here for now

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
    // Example placeholder: Define a dummy closure
    Closure callNovitaApi = { String payload ->
        LogUtils.info("Placeholder: Calling Novita API with payload: ${payload}")
        // Return a dummy JSON response structure
        return """
        {
          "images": [
            { "image_url": "https://example.com/placeholder1.png", "image_type": "png" },
            { "image_url": "https://example.com/placeholder2.png", "image_type": "png" },
            { "image_url": "https://example.com/placeholder3.png", "image_type": "png" },
            { "image_url": "https://example.com/placeholder4.png", "image_type": "png" }
          ],
          "task": { "task_id": "dummy-task-id" }
        }
        """
    }
    LogUtils.info("Created Novita API caller (placeholder).")

    // 5. Call API (with progress indication)
    LogUtils.info("Showing progress dialog...")
    JDialog progressDialog = DialogHelper.createProgressDialog(ui, "Generating Image", "Contacting Novita.ai API...")
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
        "https://example.com/placeholder1.png",
        "https://example.com/placeholder2.png",
        "https://example.com/placeholder3.png",
        "https://example.com/placeholder4.png"
    ]
    if (imageUrls.isEmpty()) {
        UiHelper.showErrorMessage(ui, "The API did not return any image URLs. Check the logs for details.")
        LogUtils.error("API response did not contain any image URLs.")
        return
    }
    LogUtils.info("Parsed image URLs (placeholder): ${imageUrls}")

    // 7. Show Selection Dialog
    LogUtils.info("Showing image selection dialog...")
    // Placeholder downloader function - replace with actual implementation later
    // TODO: Replace placeholder with:
    // Closure downloader = ImageDownloader.&downloadImageBytes // Or similar
    Closure downloader = { String url ->
        LogUtils.info("Placeholder: Simulating download of image from ${url}")
        // Simulate delay
        try { Thread.sleep(500) } catch (InterruptedException ignored) {}
        return "dummy image data for ${url}".bytes // Return dummy bytes
    }
    // TODO: Replace placeholder selection simulation with actual dialog call:
    // String selectedUrl = DialogHelper.showImageSelectionDialog(ui, imageUrls, downloader)
    // Simulate user selection (e.g., selects the second image)
    String selectedUrl = imageUrls.size() > 1 ? imageUrls[1] : null
    LogUtils.info("User selected URL (placeholder): ${selectedUrl}")


    // 8. Process Selection
    if (selectedUrl) {
        LogUtils.info("Showing download progress dialog...")
        JDialog downloadProgress = DialogHelper.createProgressDialog(ui, "Downloading Image", "Downloading selected image...")
        try {
            downloadProgress?.visible = true
            LogUtils.info("Download progress dialog shown.")

            LogUtils.info("Downloading image bytes...")
            // TODO: Replace placeholder downloader call with actual download:
            // byte[] selectedImageBytes = ImageDownloader.downloadImageBytes(selectedUrl)
            byte[] selectedImageBytes = downloader(selectedUrl) // Use placeholder downloader
            LogUtils.info("Downloaded ${selectedImageBytes.length} bytes for selected image (placeholder).")

            LogUtils.info("Determining filename and extension...")
            String baseName = node.text.replaceAll("[^a-zA-Z0-9_\\-]", "_").take(30) ?: "image"
            String extension = selectedUrl.substring(selectedUrl.lastIndexOf('.') + 1).toLowerCase() ?: "png"
            if (!['png', 'jpg', 'jpeg', 'gif'].contains(extension)) extension = 'png'

            LogUtils.info("Attaching image to node ${node.id} (baseName: ${baseName}, ext: ${extension})...")
            // TODO: Replace placeholder log with actual attachment call:
            // NodeOperations.attachImageToNode(node, selectedImageBytes, baseName, extension)
            LogUtils.info("Placeholder: Attaching image to node ${node.id} (baseName: ${baseName}, ext: ${extension})") // Keep placeholder log for now

        } catch (IOException e) {
           LogUtils.severe("Failed to download or attach image: ${e.message}", e)
           UiHelper.showErrorMessage(ui, "Failed to download or attach image: ${e.message}")
        } finally {
            downloadProgress?.dispose()
            LogUtils.info("Download progress dialog disposed.")
        }
    } else {
        LogUtils.info("User cancelled image selection or no URL was selected.")
    }

} catch (Exception e) { // Catching generic Exception for now
    LogUtils.severe("An unexpected error occurred in GenerateImage script: ${e.message}", e)
    UiHelper.showErrorMessage(ui, "An unexpected error occurred: ${e.message.split('\n').head()}")
} finally {
    LogUtils.info("GenerateImage script finished.")
}
