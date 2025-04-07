package com.barrymac.freeplane.addons.llm

import spock.lang.Specification
import spock.lang.Unroll
import javax.swing.*

class BranchGeneratorFactorySpec extends Specification {
    def mockLogger = Mock(org.slf4j.Logger)
    def mockUi = Mock(UITest)
    def mockNode = Mock(NodeProxy)
    def mockDeps = [
        apiCaller: [make_api_call: Mock(Closure)],
        dialogHelper: Mock(DialogHelper),
        nodeTagger: Mock(Closure)
    ]
    
    def setup() {
        // Mock node children handling
        mockNode.children >> []
        mockNode.text >> "Test Node"
        mockNode.delegate >> mockNode // Add this line to mock delegate property
        
        // Add currentFrame mock to UI
        mockUi.currentFrame >> new Object() // Simple object mock for frame
    }

    @Unroll
    def "generateBranches handles #scenario"() {
        given: "Configured branch generator"
        def generator = BranchGeneratorFactory.createGenerateBranches(
            [c: [selected: mockNode], ui: mockUi, logger: mockLogger, config: [:]],
            mockDeps
        )

        when: "Invoking generator with parameters"
        generator(apiKey, "system-msg", "user-msg", "test-model", 100, 0.7, "openai")

        then: "Verify progress dialog creation"
        1 * mockDeps.dialogHelper.createProgressDialog(mockUi, "I am asking your question. Wait for the response.", "user-msg") >> {
            def mockDialog = Mock(JDialog)
            mockDialog.setVisible(_) >> { /* no-op */ }
            return mockDialog
        }
        
        and: "Verify API call execution"
        apiCalls * mockDeps.apiCaller.make_api_call("openai", apiKey, {
            it.model == "test-model" &&
            it.messages*.content == ["system-msg", "user-msg"] &&
            it.temperature == 0.7 &&
            it.max_tokens == 100
        }) >> apiResponse
        
        and: "Verify node tagging"
        tagCalls * mockDeps.nodeTagger.call(_ as NodeProxy, "test-model", mockLogger)
        
        and: "Verify error handling"
        errorCalls * mockUi.errorMessage(_ as String)

        where:
        scenario                | apiKey     | apiResponse               | apiCalls | tagCalls | errorCalls
        "successful call"       | "valid-key"| '{"choices":[{"message":{"content":"response"}}]}' | 1 | 1 | 0
        "empty API key"         | ""         | ""                        | 0 | 0 | 1
        "API call failure"      | "valid-key"| { throw new Exception() } | 1 | 0 | 1
    }

    def "generator handles node tagging correctly"() {
        given: "Mock node with children"
        def mockChild = Mock(NodeProxy)
        mockNode.children >> [mockChild]
        
        def generator = BranchGeneratorFactory.createGenerateBranches(
            [c: [selected: mockNode], ui: mockUi, logger: mockLogger, config: [:]],
            mockDeps
        )

        when: "Generating branches with successful response"
        generator("valid-key", "sys", "user", "model", 100, 0.7, "openai")

        then: "Verify node tagging"
        1 * mockDeps.nodeTagger(mockChild, "model", mockLogger)
    }

    def "generator handles OpenRouter provider specific headers"() {
        given:
        def generator = BranchGeneratorFactory.createGenerateBranches(
            [c: [selected: mockNode], ui: mockUi, logger: mockLogger, config: [:]],
            mockDeps
        )

        when:
        generator("valid-key", "sys", "user", "model", 100, 0.7, "openrouter")

        then:
        1 * mockDeps.apiCaller.make_api_call("openrouter", "valid-key", _) >> '{"choices":[{}]}'
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
