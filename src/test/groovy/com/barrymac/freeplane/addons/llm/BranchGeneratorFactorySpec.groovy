package com.barrymac.freeplane.addons.llm

import groovy.lang.Closure
import spock.lang.Specification
import spock.lang.Unroll
import javax.swing.*

class BranchGeneratorFactorySpec extends Specification {
    def mockLogger = Mock(org.slf4j.Logger)
    def mockUi = Mock(UITest)
    def mockNode = Mock(NodeProxy)
    // Replace mockDeps map with individual mocks for closures
    def mockApiClosure = Mock(Closure)
    def mockNodeTaggerClosure = Mock(Closure)

    def setup() {
        // Add static mocking for DialogHelper
        GroovyMock(DialogHelper, global: true)

        // Mock node children handling
        mockNode.children >> []
        mockNode.text >> "Test Node"
        mockNode.delegate >> mockNode // Add this line to mock delegate property

        // Add currentFrame mock to UI
        mockUi.currentFrame >> new Object() // Simple object mock for frame
        // Removed mockDeps instantiation
    }

    @Unroll
    def "generateBranches handles #scenario"() {
        given: "Configured branch generator"
        def generator = BranchGeneratorFactory.createGenerateBranches(
            [c: [selected: mockNode], ui: mockUi, logger: mockLogger, config: [:]],
            // Update the deps map passed to the factory
            [
                apiCaller: [make_api_call: mockApiClosure],
                nodeTagger: mockNodeTaggerClosure
                // dialogHelper entry removed as it's called statically
            ]
        )

        when: "Invoking generator with parameters"
        generator(apiKey, "system-msg", "user-msg", "test-model", 100, 0.7, "openai")
        // Add sleep if needed for async operations, especially for success/failure cases
        if (apiCalls > 0) Thread.sleep(100)

        then: "Verify interactions"
        // Verify progress dialog creation (static call)
        dialogCalls * DialogHelper.createProgressDialog(mockUi, "I am asking your question. Wait for the response.", "user-msg") >> {
            def mockDialog = Mock(JDialog)
            mockDialog.setVisible(_) >> { /* no-op */ }
            mockDialog.dispose() >> { /* no-op */ } // Add mocking for dispose
            return mockDialog
        }

        and: "Verify API call execution (closure call)"
        apiCalls * mockApiClosure.call("openai", apiKey, { // Changed to mockApiClosure.call
            it.model == "test-model" &&
            it.messages*.content == ["system-msg", "user-msg"] &&
            it.temperature == 0.7 &&
            it.max_tokens == 100
        }) >> apiResponse

        and: "Verify node tagging (closure call) - Takes 2 arguments now"
        tagCalls * mockNodeTaggerClosure.call(_ as NodeProxy, "test-model") // Changed to mockNodeTaggerClosure.call, removed mockLogger

        and: "Verify error handling"
        errorCalls * mockUi.errorMessage(_ as String)

        where:
        scenario                | apiKey     | apiResponse               | dialogCalls | apiCalls | tagCalls | errorCalls // Added dialogCalls
        "successful call"       | "valid-key"| '{"choices":[{"message":{"content":"response"}}]}' | 1 | 1 | 1 | 0 // tagCalls is 1
        "empty API key"         | ""         | ""                        | 0 | 0 | 0 | 1 // dialogCalls is 0, tagCalls is 0
        "API call failure"      | "valid-key"| { throw new Exception() } | 1 | 1 | 0 | 1 // dialogCalls is 1, tagCalls is 0
    }

    def "generator handles node tagging correctly"() {
        given: "Mock node initially with no children"
        def mockChild = Mock(NodeProxy)
        mockNode.children >> [] // Start with no children

        // Ensure appendTextOutlineAsBranch is stubbed to simulate adding the child
        mockNode.appendTextOutlineAsBranch(_) >> {
             mockNode.children >> [mockChild] // Simulate child being added *after* call
        }

        // Update the deps map passed here
        def generator = BranchGeneratorFactory.createGenerateBranches(
            [c: [selected: mockNode], ui: mockUi, logger: mockLogger, config: [:]],
            [
                apiCaller: [make_api_call: mockApiClosure],
                nodeTagger: mockNodeTaggerClosure
            ]
        )

        // Mock necessary preceding calls for the flow to reach tagging
        1 * DialogHelper.createProgressDialog(_, _, _) >> {
            Mock(JDialog) {
                setVisible(_) >> {}
                dispose() >> {}
            }
        }
        1 * mockApiClosure.call(_, _, _) >> '{"choices":[{"message":{"content":"response"}}]}' // Simplified successful API call

        when: "Generating branches with successful response"
        generator("valid-key", "sys", "user", "model", 100, 0.7, "openai")
        // Add a small sleep to allow the worker thread (SwingUtilities.invokeLater) to likely complete
        Thread.sleep(200) // Allow time for async operations

        then: "Verify node tagging"
        // Use mockNodeTaggerClosure.call with 2 arguments
        1 * mockNodeTaggerClosure.call(mockChild, "model")
    }

    def "generator handles OpenRouter provider specific headers"() {
        given: "Generator configured for OpenRouter and mock child"
        def mockChild = Mock(NodeProxy)
        mockNode.children >> []
        // Simulate child node creation when appending branch
        mockNode.appendTextOutlineAsBranch(_) >> { 
            mockNode.children >> [mockChild]
        }

        def generator = BranchGeneratorFactory.createGenerateBranches(
            [c: [selected: mockNode], ui: mockUi, logger: mockLogger, config: [:]],
            [
                apiCaller: [make_api_call: mockApiClosure],
                nodeTagger: mockNodeTaggerClosure
            ]
        )
        
        1 * DialogHelper.createProgressDialog(_, _, _) >> {
            Mock(JDialog) {
                setVisible(_) >> {}
                dispose() >> {}
            }
        }

        when: "Invoking generator with OpenRouter"
        generator("valid-key", "sys", "user", "model", 100, 0.7, "openrouter")
        Thread.sleep(200)  // Increase wait time for async operations

        then: "Verify API call and tagging"
        1 * mockApiClosure.call("openrouter", "valid-key", _) >> '{"choices":[{"message":{"content":"response"}}]}'
        1 * mockNodeTaggerClosure.call(mockChild, "model")
    }

    // Interface for UI mock
    interface UITest {
        void errorMessage(String message)
        void setDialogLocationRelativeTo(Object dialog, Object node)
        Object getCurrentFrame() // Add missing method
    }

    // Mock node proxy interface
    interface NodeProxy {
        List<NodeProxy> getChildren()
        String getText()
        void appendTextOutlineAsBranch(String text)
        Object getDelegate() // Add this line
    }
}
