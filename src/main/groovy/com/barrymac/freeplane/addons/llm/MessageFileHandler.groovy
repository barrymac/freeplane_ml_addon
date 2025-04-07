package com.barrymac.freeplane.addons.llm

import org.freeplane.core.util.LogUtils

class MessageFileHandler {
    /**
     * Loads messages from a user-specific file, falling back to a default resource from the JAR.
     * If the user file doesn't exist, it's created with the default content.
     *
     * @param filePath Path to the user-specific message file (e.g., ".../chatGptSystemMessages.txt").
     * @param defaultResourcePath Path to the default resource within the JAR (e.g., "/defaultSystemMessages.txt").
     * @param defaultLoaderFunc A function (like MessageLoader.loadDefaultMessages) to load the default resource.
     * @return A list of messages split by '======'.
     */
    static def loadMessagesFromFile(String filePath, String defaultResourcePath, Closure defaultLoaderFunc) {
        def messages
        def fileContent
        try {
            fileContent = new File(filePath).text
            LogUtils.info("Loaded message file from: ${filePath}")
        } catch (Exception e) {
            LogUtils.info("User message file not found at: ${filePath}. Loading default from resource: ${defaultResourcePath}")
            fileContent = defaultLoaderFunc(defaultResourcePath) // Load default from JAR
            try {
                new File(filePath).write(fileContent) // Write default content to user file
                LogUtils.info("Created new message file at: ${filePath}")
            } catch (Exception writeEx) {
                LogUtils.warn("Failed to write default content to: ${filePath}: ${writeEx.message}")
            }
        }
        messages = fileContent.split(/======+\n/)*.trim()
        LogUtils.info("Loaded ${messages.size()} messages from file")
        return messages
    }

    static def saveMessagesToFile(String filePath, List messages) {
        try {
            def fileContent = messages.join("\n======\n")
            new File(filePath).write(fileContent)
            LogUtils.info("Saved ${messages.size()} messages to file: ${filePath}")
        } catch (Exception e) {
            LogUtils.severe("Failed to save messages to file: ${filePath}: ${e.message}")
        }
    }
}
