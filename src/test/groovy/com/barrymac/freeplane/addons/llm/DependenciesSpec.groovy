package com.barrymac.freeplane.addons.llm

import spock.lang.Specification

class DependenciesSpec extends Specification {

    def "Dependencies class can be instantiated"() {
        given: "Some mock dependency objects"
        def mockApiCaller = [:]
        def mockFactory = {}
        def mockExpander = [:]
        def mockHandler = [:]
        def mockTagger = {}
        def mockParser = Object // Class reference
        def mockDialog = Object // Class reference
        def mockNodeHelper = Object // Class reference
        // def mockNodeOps = Object // Removed - not in Dependencies class
        def mockConfigMgr = Object // Class reference
        def mockMsgLoader = [:]

        when: "Creating a Dependencies instance"
        def deps = new Dependencies(
                apiCaller: mockApiCaller,
                branchGeneratorFactory: mockFactory,
                messageExpander: mockExpander,
                messageFileHandler: mockHandler,
                nodeTagger: mockTagger,
                responseParser: mockParser,
                dialogHelper: mockDialog,
                nodeHelperUtils: mockNodeHelper,
                // nodeOperations: mockNodeOps, // Removed
                configManager: mockConfigMgr,
                messageLoader: mockMsgLoader
        )

        then: "The instance is created successfully and fields are set"
        deps != null
        deps.apiCaller == mockApiCaller
        deps.branchGeneratorFactory == mockFactory
        deps.messageExpander == mockExpander
        deps.messageFileHandler == mockHandler
        deps.nodeTagger == mockTagger
        deps.responseParser == mockParser
        deps.dialogHelper == mockDialog
        deps.nodeHelperUtils == mockNodeHelper
        // deps.nodeOperations == mockNodeOps // Removed
        deps.configManager == mockConfigMgr
        deps.messageLoader == mockMsgLoader

        // Also test @Canonical generated methods if needed (equals, hashCode, toString)
        // For example:
        def deps2 = new Dependencies(
                apiCaller: mockApiCaller, branchGeneratorFactory: mockFactory, messageExpander: mockExpander,
                messageFileHandler: mockHandler, nodeTagger: mockTagger, responseParser: mockParser,
                dialogHelper: mockDialog, nodeHelperUtils: mockNodeHelper, /* nodeOperations: mockNodeOps, */ // Removed
                configManager: mockConfigMgr, messageLoader: mockMsgLoader
        )
        deps == deps2 // Test equals
    }
}
