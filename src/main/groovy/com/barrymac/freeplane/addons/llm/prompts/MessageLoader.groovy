package com.barrymac.freeplane.addons.llm.prompts

import org.freeplane.core.util.LogUtils

/**
 * Helper class for loading and managing message templates
 */
class MessageLoader {
    /**
     * Load resource from JAR classpath
     *
     * @param path Path to the resource in the classpath
     * @return String containing the resource content
     */
    private static String getResourceContent(String path) {
        // Use Thread Context ClassLoader, more reliable in complex environments
        // Remove leading '/' as getResourceAsStream on ClassLoader expects path relative to root
        String resourcePath = path.startsWith("/") ? path.substring(1) : path
        def stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)
        if (!stream) {
            LogUtils.severe("Missing required resource: ${path} (tried path: ${resourcePath})")
            throw new Exception("Missing required resource: ${path} (tried path: ${resourcePath})")
        }
        return stream.getText("UTF-8").trim()
    }

    /**
     * Loads a single default message/resource file from the JAR classpath.
     *
     * @param resourcePath The absolute path within the JAR (e.g., "/defaultSystemMessages.txt")
     * @return The content of the resource file as a String.
     * @throws Exception if the resource cannot be found.
     */
    static String loadDefaultMessages(String resourcePath) {
        try {
            // Use the existing getResourceContent which already handles errors
            def content = getResourceContent(resourcePath)
            LogUtils.info("Loaded default message from resource: ${resourcePath}, size: ${content.length()} chars")
            return content
        } catch (Exception e) {
            LogUtils.severe("Failed to load default message from resource: ${resourcePath}: ${e.message}")
            throw e
        }
    }

    /**
     * Loads message templates for node comparison
     *
     * @param config The Freeplane config object
     * @return Map containing system and user message templates
     */
    static Map loadComparisonMessages(config) {
        try {
            LogUtils.info("Loading comparison message templates")
            return [
                    systemTemplate         : getResourceContent("/compareNodesSystem.txt"),
                    userTemplate           : getResourceContent("/compareNodesUserMessage.txt"),
                    dimensionSystemTemplate: getResourceContent("/generateComparativeDimensionSystem.txt")
            ]
        } catch (Exception e) {
            LogUtils.severe("Failed to load comparison templates: ${e.message}")
            throw new Exception("Failed to load comparison templates: ${e.message}")
        }
    }
}
