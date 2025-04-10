package com.barrymac.freeplane.addons.llm.services

import org.freeplane.core.util.LogUtils
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * Handles loading of resources bundled within the add-on JAR.
 */
class ResourceLoaderService {

    /**
     * Loads bytes for a resource bundled within the add-on JAR.
     * Assumes the path starts with '/' and is relative to the JAR root.
     *
     * @param path The absolute path within the JAR (e.g., "/my_icon.png")
     * @return byte[] The resource bytes
     * @throws FileNotFoundException if the resource cannot be found
     * @throws IOException if there's an error reading the resource stream
     */
    static byte[] loadBundledResourceBytes(String path) throws FileNotFoundException, IOException {
        LogUtils.info("ResourceLoaderService: Attempting to load resource '${path}'")
        if (!path) {
            LogUtils.error("ResourceLoaderService: Path cannot be null")
            throw new IllegalArgumentException("Bundled resource path cannot be null")
        }
        
        // Remove leading slash if present to use classpath-relative path
        String adjustedPath = path.startsWith("/") ? path.substring(1) : path
        LogUtils.info("ResourceLoaderService: Using adjusted path '${adjustedPath}'")
        
        // Use the classloader instead of class-based resource access
        ClassLoader classLoader = ResourceLoaderService.class.getClassLoader()
        InputStream stream = classLoader.getResourceAsStream(adjustedPath)

        if (stream == null) {
            // Try to list available resources for better debugging
            LogUtils.severe("ResourceLoaderService: Resource not found at path: ${adjustedPath} using ${ResourceLoaderService.class.name} classloader.")
            
            // Add diagnostic information about what resources are available
            try {
                LogUtils.info("ResourceLoaderService: Checking for resources in 'images/' directory...")
                def resources = classLoader.getResources("images")
                if (resources.hasMoreElements()) {
                    LogUtils.info("ResourceLoaderService: 'images/' directory exists in classpath")
                } else {
                    LogUtils.info("ResourceLoaderService: No 'images/' directory found in classpath")
                }
            } catch (Exception e) {
                LogUtils.info("ResourceLoaderService: Error checking resources: ${e.message}")
            }
            
            throw new FileNotFoundException("Bundled resource not found: ${adjustedPath}")
        }

        LogUtils.info("ResourceLoaderService: Found resource stream for path: ${adjustedPath}")
        try {
            byte[] bytes = stream.bytes
            LogUtils.info("ResourceLoaderService: Successfully loaded ${bytes.length} bytes from resource: ${adjustedPath}")
            return bytes
        } catch (IOException e) {
            LogUtils.severe("ResourceLoaderService: IOException reading resource stream for path: ${adjustedPath}", e)
            throw e // Re-throw
        } finally {
            try {
                stream.close()
            } catch (IOException ignored) {
                // Ignore close exception
            }
        }
    }
    
    /**
     * Loads a text resource from the add-on JAR and returns its content as a String.
     *
     * @param path The path to the text resource (e.g., "/templates/prompt.txt")
     * @return String The content of the text resource
     * @throws FileNotFoundException if the resource cannot be found
     * @throws IOException if there's an error reading the resource
     */
    static String loadTextResource(String path) throws FileNotFoundException, IOException {
        LogUtils.info("ResourceLoaderService: Loading text resource '${path}'")
        try {
            byte[] bytes = loadBundledResourceBytes(path)
            String content = new String(bytes, "UTF-8")
            LogUtils.info("ResourceLoaderService: Successfully loaded text resource (${content.length()} chars)")
            return content
        } catch (Exception e) {
            LogUtils.severe("ResourceLoaderService: Failed to load text resource '${path}': ${e.message}", e)
            throw e
        }
    }
}
