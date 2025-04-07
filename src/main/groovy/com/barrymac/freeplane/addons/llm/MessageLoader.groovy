package com.barrymac.freeplane.addons.llm

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
        def stream = MessageLoader.class.getResourceAsStream(path)
        if (!stream) {
            throw new Exception("Missing required resource: ${path}")
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
        // Use the existing getResourceContent which already handles errors
        return getResourceContent(resourcePath)
    }

    /**
     * Loads message templates for node comparison
     *
     * @param config The Freeplane config object
     * @return Map containing system and user message templates
     */
    static Map loadComparisonMessages(config) {
        try {
            return [
                    systemTemplate         : getResourceContent("/compareNodesSystem.txt"),
                    userTemplate           : getResourceContent("/compareNodesUserMessage.txt"),
                    dimensionSystemTemplate: getResourceContent("/generateComparativeDimensionSystem.txt")
            ]
        } catch (Exception e) {
            throw new Exception("Failed to load comparison templates: ${e.message}")
        }
    }
}
