package com.barrymac.freeplane.addons.llm


import org.freeplane.core.util.LogUtils
import spock.lang.Specification

// Use interfaces from other tests or define simple ones

class DependencyLoaderSpec extends Specification {

    def mockConfig = Mock(ConfigTest)
    def mockLogger = Mock(LoggerTest)
    def mockUi = Mock(UITest)

    // setupSpec removed

    // Add setup() method with the GroovyMock lines
    def setup() {
        GroovyMock(LogUtils, global: true)
        // Mock the ApiCallerFactory to return a predictable object
        GroovyMock(ApiCallerFactory, global: true)
        // Re-apply the stubbing for the factory here as well
        ApiCallerFactory.createApiCaller(_) >> [make_api_call: { /* mock closure */ }]
    }

    def "loadDependencies wires up all dependencies correctly"() {
        when: "Loading dependencies"
        def deps = DependencyLoader.loadDependencies(mockConfig, mockLogger, mockUi)

        then: "Log message is generated"
        1 * LogUtils.info("Loading dependencies for LLM add-on")

        and: "Dependencies object is returned and populated"
        deps != null
        deps instanceof Dependencies

        // Verify ApiCaller (check if it's the map returned by the mocked factory)
        deps.apiCaller != null
        deps.apiCaller.containsKey('make_api_call')

        // Verify BranchGeneratorFactory (check if it's a closure referencing the correct method)
        deps.branchGeneratorFactory instanceof Closure
        // More specific check: verify it points to the intended method (can be tricky)
        // This checks if the closure's owner is the class containing the static method
        deps.branchGeneratorFactory.delegate == BranchGeneratorFactory // Check delegate for static method ref

        // Verify MessageExpander (check map structure and method references)
        deps.messageExpander instanceof Map
        deps.messageExpander.size() == 2
        deps.messageExpander.expandMessage instanceof Closure
        deps.messageExpander.expandMessage.delegate == MessageExpander
        deps.messageExpander.getBindingMap instanceof Closure
        deps.messageExpander.getBindingMap.delegate == MessageExpander

        // Verify MessageFileHandler (check map structure and method references)
        deps.messageFileHandler instanceof Map
        deps.messageFileHandler.size() == 2
        deps.messageFileHandler.loadMessagesFromFile instanceof Closure
        deps.messageFileHandler.loadMessagesFromFile.delegate == MessageFileHandler
        deps.messageFileHandler.saveMessagesToFile instanceof Closure
        deps.messageFileHandler.saveMessagesToFile.delegate == MessageFileHandler

        // Verify NodeTagger (check method reference)
        deps.nodeTagger instanceof Closure
        deps.nodeTagger.delegate == NodeTagger

        // Verify ResponseParser (check class reference)
        deps.responseParser == ResponseParser

        // Verify DialogHelper (check class reference)
        deps.dialogHelper == DialogHelper

        // Verify NodeHelper (check class reference)
        deps.nodeHelperUtils == NodeHelper

        // Verify ConfigManager (check class reference)
        deps.configManager == ConfigManager

        // Verify MessageLoader (check map structure, class ref, method refs)
        deps.messageLoader instanceof Map
        deps.messageLoader.size() == 3
        deps.messageLoader.MessageLoaderClass == MessageLoader
        deps.messageLoader.loadDefaultMessages instanceof Closure
        deps.messageLoader.loadDefaultMessages.delegate == MessageLoader
        deps.messageLoader.loadComparisonMessages instanceof Closure
        deps.messageLoader.loadComparisonMessages.delegate == MessageLoader
    }
}
