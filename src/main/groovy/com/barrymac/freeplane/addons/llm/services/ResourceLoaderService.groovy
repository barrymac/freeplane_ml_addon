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
     * @param path The absolute path within the JAR (e.g., "/images/my_icon.png")
     * @return byte[] The resource bytes
     * @throws FileNotFoundException if the resource cannot be found
     * @throws IOException if there's an error reading the resource stream
     */
    static byte[] loadBundledResourceBytes(String path) throws FileNotFoundException, IOException {
        LogUtils.info("ResourceLoaderService: Attempting to load resource '${path}'")
        if (!path?.startsWith("/")) {
            // Enforce the contract: paths must be absolute from JAR root
            LogUtils.error("ResourceLoaderService: Path must start with '/'. Provided: '${path}'")
            throw new IllegalArgumentException("Bundled resource path must start with '/'")
        }

        // Use the classloader of this service class itself, which is part of the add-on JAR
        InputStream stream = ResourceLoaderService.class.getResourceAsStream(path)

        if (stream == null) {
            LogUtils.severe("ResourceLoaderService: Resource not found at path: ${path} using ${ResourceLoaderService.class.name} classloader.")
            // You could try other classloaders here as a fallback if needed, but start with the most reliable one.
            throw new FileNotFoundException("Bundled resource not found: ${path}")
        }

        LogUtils.info("ResourceLoaderService: Found resource stream for path: ${path}")
        try {
            byte[] bytes = stream.bytes
            LogUtils.info("ResourceLoaderService: Successfully loaded ${bytes.length} bytes from resource: ${path}")
            return bytes
        } catch (IOException e) {
            LogUtils.severe("ResourceLoaderService: IOException reading resource stream for path: ${path}", e)
            throw e // Re-throw
        } finally {
            try {
                stream.close()
            } catch (IOException ignored) {
                // Ignore close exception
            }
        }
    }
}
