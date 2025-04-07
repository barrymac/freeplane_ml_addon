package com.barrymac.freeplane.addons.llm

// Helper class to centralize dependency loading

class DependencyLoader {
    static Map loadDependencies(config, logger, ui) {

        // Load all dependencies with a consistent approach
        return [
                // Instantiate ApiCaller using its factory
                apiCaller             : ApiCallerFactory.createApiCaller([logger: logger, ui: ui]),

                // Provide BranchGenerator factory method reference
                branchGeneratorFactory: BranchGeneratorFactory.&createGenerateBranches,

                // Provide MessageExpander static method references
                messageExpander       : [expandMessage: MessageExpander.&expandMessage, getBindingMap: MessageExpander.&getBindingMap],

                // Provide MessageFileHandler static method references
                messageFileHandler    : [loadMessagesFromFile: MessageFileHandler.&loadMessagesFromFile,
                                         saveMessagesToFile  : MessageFileHandler.&saveMessagesToFile],

                // Provide NodeTagger static method reference
                nodeTagger            : NodeTagger.&addModelTagRecursively,

                // Provide ResponseParserClass directly
                responseParser        : ResponseParser,

                // Add new ones
                dialogHelper          : DialogHelper,

                // Provide NodeHelperClass directly
                nodeHelperUtils       : NodeHelper,

                configManager         : ConfigManager,

                // Provide messageLoader map directly using imported class/methods
                messageLoader         : [
                        MessageLoaderClass    : MessageLoader,
                        loadDefaultMessages   : MessageLoader.&loadDefaultMessages,
                        loadComparisonMessages: MessageLoader.&loadComparisonMessages
                ]
        ]
    }
}
