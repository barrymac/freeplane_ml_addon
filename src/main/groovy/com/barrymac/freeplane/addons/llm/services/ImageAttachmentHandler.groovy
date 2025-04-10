package com.barrymac.freeplane.addons.llm.services

import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException
import org.freeplane.api.Node
import org.freeplane.core.util.LogUtils

import java.awt.MediaTracker
import java.awt.Panel
import java.awt.Toolkit

class ImageAttachmentHandler {
    static void attachImageToNode(Node node, byte[] imageBytes, String baseName, String extension) {
        try {
            LogUtils.info("Starting image attachment to node: ${node.text}")

            // 1. Generate unique filename
            String fileName = generateFileName(baseName, extension)
            LogUtils.info("Generated filename: ${fileName}")

            // 2. Save image to map directory
            File imageFile = saveImageToMap(node, imageBytes, fileName)

            // 3. Attach to node
            attachImageUri(node, imageFile)

            LogUtils.info("Successfully attached image: ${fileName}")
        } catch (Exception e) {
            String errorMsg = "Image attachment failed"
            LogUtils.severe("${errorMsg}: ${e.message}")
            throw new LlmAddonException(errorMsg, e)
        }
    }

    /**
     * Sanitizes a base name for use in filenames by removing invalid characters
     * @param input The original string to sanitize
     * @return A sanitized string suitable for filenames
     */
    static String sanitizeBaseName(String input) {
        if (!input?.trim()) return "image"
        return input.replaceAll(/[^\w\-]/, '_').take(30)
    }

    /**
     * Extracts and validates file extension from a URL or filename
     * @param url The URL or filename to extract extension from
     * @return A valid image extension (png, jpg, jpeg, gif) or "png" as default
     */
    static String getFileExtension(String url) {
        if (!url?.trim()) return "png"
        def ext = url.substring(url.lastIndexOf('.') + 1).toLowerCase()
        return ['png', 'jpg', 'jpeg', 'gif'].contains(ext) ? ext : 'png'
    }

    private static String generateFileName(String baseName, String extension) {
        String safeName = baseName.replaceAll(/[^\w\-]/, '_').take(30)
        String timestamp = new Date().format('yyyyMMddHHmmss')
        "${safeName}_${timestamp}.${extension}"
    }

    private static File saveImageToMap(Node node, byte[] bytes, String fileName) {
        def mapFile = node.map.file
        if (!mapFile) {
            throw new LlmAddonException("Map must be saved before adding images")
        }

        File imageFile = new File(mapFile.parentFile, fileName)
        imageFile.bytes = bytes
        LogUtils.info("Saved image to: ${imageFile.absolutePath}")
        
        // Add image validation
        validateImageDimensions(imageFile)
        
        return imageFile
    }
    
    private static void validateImageDimensions(File imageFile) {
        try {
            def image = Toolkit.defaultToolkit.createImage(imageFile.bytes)
            def tracker = new MediaTracker(new Panel()) // Dummy component
            tracker.addImage(image, 0)
            tracker.waitForAll()
            
            // Use getWidth()/getHeight() with ImageObserver instead of property access
            int width = image.getWidth(null)
            int height = image.getHeight(null)
            
            if (width <= 0 || height <= 0) {
                throw new LlmAddonException("Invalid image dimensions: ${width}x${height}")
            }
            LogUtils.info("Validated image dimensions: ${width}x${height}")
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt()
            throw new LlmAddonException("Image validation interrupted", e)
        } catch (Exception e) {
            LogUtils.severe("Error validating image: ${e.message}")
            throw new LlmAddonException("Failed to validate image: ${e.message}", e)
        }
    }

    private static void attachImageUri(Node node, File imageFile) {
        try {
            def uri = imageFile.toURI()
            node.externalObject.uri = uri.toString()
            LogUtils.info("Attached image URI: ${uri}")
        } catch (Exception e) {
            throw new LlmAddonException("Failed to set image URI: ${e.message}", e)
        }
    }
}
