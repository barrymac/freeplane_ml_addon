package com.barrymac.freeplane.addons.llm

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Helper class to centralize dependency loading
 */
@CompileStatic
@Slf4j
class DependencyLoader {
    /**
     * Loads all dependencies needed by the add-on
     *
     * @param config The Freeplane config object
     * @param ui The UI
     * @return Dependencies object containing all dependencies
     */
    static Dependencies loadDependencies(def config, def logger, def ui) {
        log.debug("Loading dependencies for LLM add-on")
        return new Dependencies(
            // Instantiate ApiCaller using its factory
            apiCaller: ApiCallerFactory.createApiCaller([ui: ui]),

            // Provide BranchGenerator factory method reference
            branchGeneratorFactory: BranchGeneratorFactory.&createGenerateBranches,

            // Provide MessageExpander static method references
            messageExpander: [expandMessage: MessageExpander.&expandMessage, getBindingMap: MessageExpander.&getBindingMap],

            // Provide MessageFileHandler static method references
            messageFileHandler: [loadMessagesFromFile: MessageFileHandler.&loadMessagesFromFile,
                                 saveMessagesToFile: MessageFileHandler.&saveMessagesToFile],

            // Provide NodeTagger static method reference
            nodeTagger: NodeTagger.&addModelTagRecursively,

            // Provide ResponseParserClass directly
            responseParser: ResponseParser,

            // Add new ones
            dialogHelper: DialogHelper,

            // Provide NodeHelperClass directly
            nodeHelperUtils: NodeHelper,

            configManager: ConfigManager,

            // Provide messageLoader map directly using imported class/methods
            messageLoader: [
                MessageLoaderClass: MessageLoader,
                loadDefaultMessages: MessageLoader.&loadDefaultMessages,
                loadComparisonMessages: MessageLoader.&loadComparisonMessages
            ]
        )
    }
}
