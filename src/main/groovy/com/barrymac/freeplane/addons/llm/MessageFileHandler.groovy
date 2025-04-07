package com.barrymac.freeplane.addons.llm

import groovy.util.logging.Slf4j

@Slf4j
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
            log.debug("Loaded message file from: {}", filePath)
        } catch (Exception e) {
            log.info("User message file not found at: {}. Loading default from resource: {}", filePath, defaultResourcePath)
            fileContent = defaultLoaderFunc(defaultResourcePath) // Load default from JAR
            try {
                new File(filePath).write(fileContent) // Write default content to user file
                log.info("Created new message file at: {}", filePath)
            } catch (Exception writeEx) {
                log.warn("Failed to write default content to: {}", filePath, writeEx)
            }
        }
        messages = fileContent.split(/======+\n/)*.trim()
        log.debug("Loaded {} messages from file", messages.size())
        return messages
    }

    static def saveMessagesToFile(String filePath, List messages) {
        try {
            def fileContent = messages.join("\n======\n")
            new File(filePath).write(fileContent)
            log.info("Saved {} messages to file: {}", messages.size(), filePath)
        } catch (Exception e) {
            log.error("Failed to save messages to file: {}", filePath, e)
        }
    }
}
