package com.barrymac.freeplane.addons.llm

import com.barrymac.freeplane.addons.llm.prompts.MessageExpander
import org.freeplane.api.NodeRO
import spock.lang.Ignore

// Import NodeRO for mocking
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

/**
 * Spock test specification for the MessageExpander class.
 */
@Ignore
class MessageExpanderSpec extends Specification {

    // --- MOCK DEFINITIONS MOVED TO CLASS LEVEL ---
    def mockNode = Mock(NodeRO)
    def mockParent = Mock(NodeRO)
    def mockRoot = Mock(NodeRO)
    def mockSibling1 = Mock(NodeRO)
    def mockSibling2 = Mock(NodeRO)
    def mockGrandParent = Mock(NodeRO) // For ancestor path
    // --- END MOCK DEFINITIONS ---

    // Use @Subject to indicate the class under test
    @Subject
    def messageExpander = new MessageExpander() // Instance needed if methods weren't static, but good practice

    @Unroll // Report each iteration separately
    def "getBindingMap should correctly extract context for #description"() {
        given: "Configuring mock interactions based on where: block data"
        // Basic setup
        mockNode.getPlainText() >> nodeText
        mockNode.getMindMap().getRoot() >> mockRoot
        mockRoot.getPlainText() >> rootText

        if (isRoot) {
            mockNode.isRoot() >> true
            mockNode.getPathToRoot() >> [mockNode] // Path is just itself
        } else {
            mockNode.isRoot() >> false
            mockNode.getParent() >> mockParent

            // Configure siblings based on siblingTexts
            def siblings = []
            if (siblingTexts.size() >= 1) {
                mockSibling1.getPlainText() >> siblingTexts[0]
                siblings << mockSibling1
            }
            if (siblingTexts.size() >= 2) {
                mockSibling2.getPlainText() >> siblingTexts[1]
                siblings << mockSibling2
            }
            mockParent.getChildren() >> ([mockNode] + siblings)

            // Configure ancestor path based on ancestorTexts
            def pathToRoot = [mockNode]
            def currentAncestor = mockParent
            ancestorTexts.eachWithIndex { text, index ->
                currentAncestor.getPlainText() >> text
                pathToRoot << currentAncestor
                // If there's a next ancestor, link to it
                if (index < ancestorTexts.size() - 1) {
                    def nextAncestor = index == 0 ? mockGrandParent : Mock(NodeRO)
                    currentAncestor.getParent() >> nextAncestor
                    currentAncestor = nextAncestor
                } else {
                    currentAncestor.getParent() >> mockRoot
                }
            }
            pathToRoot << mockRoot // Ensure root is at the end
            mockNode.getPathToRoot() >> pathToRoot
        }

        when: "getBindingMap is called"
        def bindingMap = MessageExpander.getBindingMap(mockNode)

        then: "The binding map should contain the expected context"
        bindingMap == expectedMap

        where:
        description          | isRoot | nodeText      | rootText      | ancestorTexts                      | siblingTexts           | expectedMap
        "a root node"        | true   | "Root Node"   | "Root Node"   | []                                 | []                     | [rootText: "Root Node", nodeContent: "Root Node", ancestorContents: "", siblingContents: ""]
        "a child node"       | false  | "Child 1"     | "The Root"    | ["The Root"]                       | []                     | [rootText: "The Root", nodeContent: "Child 1", ancestorContents: "The Root", siblingContents: ""]
        "node with siblings" | false  | "Node B"      | "Root"        | ["Parent A", "Root"]               | ["Sibling 1", "Sibling 2"] | [rootText: "Root", nodeContent: "Node B", ancestorContents: "Parent A\nRoot", siblingContents: "Sibling 1\nSibling 2"]
        "deeply nested node" | false  | "Leaf C"      | "Main Root"   | ["Parent B", "GrandParent A", "Main Root"] | []                     | [rootText: "Main Root", nodeContent: "Leaf C", ancestorContents: "Parent B\nGrandParent A\nMain Root", siblingContents: ""]
        "node with no text"  | false  | ""            | "Root"        | ["Parent", "Root"]                 | []                     | [rootText: "Root", nodeContent: "", ancestorContents: "Parent\nRoot", siblingContents: ""]
    }
}
