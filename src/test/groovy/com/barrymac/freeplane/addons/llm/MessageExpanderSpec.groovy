package com.barrymac.freeplane.addons.llm

import org.freeplane.api.NodeRO // Import NodeRO for mocking
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

/**
 * Spock test specification for the MessageExpander class.
 */
class MessageExpanderSpec extends Specification {

    // Use @Subject to indicate the class under test
    @Subject
    def messageExpander = new MessageExpander() // Instance needed if methods weren't static, but good practice

    @Unroll // Report each iteration separately
    def "getBindingMap should correctly extract context for #description"() {
        given: "A mock node representing the scenario"
        // NodeRO is an interface, easy to mock
        def mockNode = Mock(NodeRO)
        def mockParent = Mock(NodeRO)
        def mockRoot = Mock(NodeRO)
        def mockSibling1 = Mock(NodeRO)
        def mockSibling2 = Mock(NodeRO)
        def mockGrandParent = Mock(NodeRO) // For ancestor path

        // --- Configure Mock Interactions based on scenario ---
        // Common setup
        mockNode.getPlainText() >> nodeText
        mockNode.getMindMap().getRoot() >> mockRoot
        mockRoot.getPlainText() >> rootText

        // Scenario-specific setups
        if (isRoot) {
            mockNode.isRoot() >> true
            mockNode.getPathToRoot() >> [mockNode] // Path includes the node itself
        } else {
            mockNode.isRoot() >> false
            mockNode.getParent() >> mockParent
            mockParent.getChildren() >> parentChildren // Siblings + node itself
            mockNode.getPathToRoot() >> pathToRootNodes // Path from node up to (but not including) root
        }

        // Setup plain text for ancestors and siblings
        // The path returned by getPathToRoot includes the node itself, but the logic in getBindingMap excludes it.
        // So, we need to simulate the path *without* the node itself for ancestor text extraction.
        def effectivePathForAncestors = pathToRootNodes.drop(1) // Drop the mockNode itself
        effectivePathForAncestors*.getPlainText() >> ancestorTexts

        // Sibling text extraction
        def mockSiblings = parentChildren.findAll { it != mockNode }
        mockSiblings*.getPlainText() >> siblingTexts


        when: "getBindingMap is called"
        // Access static method via class name
        def bindingMap = MessageExpander.getBindingMap(mockNode)

        then: "The binding map should contain the expected context"
        bindingMap == expectedMap

        where:
        description          | isRoot | nodeText      | rootText      | parentChildren                     | pathToRootNodes                                     | ancestorTexts                      | siblingTexts           | expectedMap
        "a root node"        | true   | "Root Node"   | "Root Node"   | []                                 | [mockNode]                                          | []                                 | []                     | [rootText: "Root Node", nodeContent: "Root Node", ancestorContents: "", siblingContents: ""] // No ancestors/siblings for root
        "a child node"       | false  | "Child 1"     | "The Root"    | [mockNode]                         | [mockNode, mockRoot]                                | ["The Root"]                       | []                     | [rootText: "The Root", nodeContent: "Child 1", ancestorContents: "The Root", siblingContents: ""] // Root is ancestor, no siblings
        "node with siblings" | false  | "Node B"      | "Root"        | [mockSibling1, mockNode, mockSibling2] | [mockNode, mockParent, mockRoot]                    | ["Parent A", "Root"]               | ["Sibling 1", "Sibling 2"] | [rootText: "Root", nodeContent: "Node B", ancestorContents: "Parent A\nRoot", siblingContents: "Sibling 1\nSibling 2"]
        "deeply nested node" | false  | "Leaf C"      | "Main Root"   | [mockNode]                         | [mockNode, mockParent, mockGrandParent, mockRoot] | ["Parent B", "GrandParent A", "Main Root"] | []                     | [rootText: "Main Root", nodeContent: "Leaf C", ancestorContents: "Parent B\nGrandParent A\nMain Root", siblingContents: ""]
        "node with no text"  | false  | ""            | "Root"        | [mockNode]                         | [mockNode, mockParent, mockRoot]                    | ["Parent", "Root"]                 | []                     | [rootText: "Root", nodeContent: "", ancestorContents: "Parent\nRoot", siblingContents: ""]
        // --- Mock setup for sibling/ancestor text extraction ---
        // Need to ensure the mocks respond correctly to getPlainText()
        mockParent.getPlainText() >> "Parent A" // For "node with siblings" ancestor
        mockSibling1.getPlainText() >> "Sibling 1"
        mockSibling2.getPlainText() >> "Sibling 2"
        mockGrandParent.getPlainText() >> "GrandParent A" // For "deeply nested node" ancestor
        // Reusing mockParent for "deeply nested node" and "node with no text"
        mockParent.getPlainText() >>> ["Parent A", "Parent B", "Parent"] // Stubbing multiple calls if needed, or specific setup per 'where' block if clearer
        // Reusing mockRoot for different scenarios
        mockRoot.getPlainText() >>> ["Root Node", "The Root", "Root", "Main Root", "Root"]
    }
}
