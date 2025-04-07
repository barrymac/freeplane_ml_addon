package com.barrymac.freeplane.addons.llm

import groovy.text.SimpleTemplateEngine
import org.freeplane.core.util.LogUtils

class MessageExpander {
    // Function to expand message templates with node context
    static def expandMessage(String message, def node) {
        try {
            def pathToRoot = node.pathToRoot
            def rootText = node.mindMap.root.text
            pathToRoot = pathToRoot.take(pathToRoot.size() - 1)
            String ancestorContents = pathToRoot*.plainText.join('\n')
            String siblingContents = node.isRoot() ? '' : node.parent.children.findAll { it != node }*.plainText.join('\n')

            def binding = [
                    rootText        : rootText,
                    nodeContent     : node.plainText,
                    ancestorContents: ancestorContents,
                    siblingContents : siblingContents
            ]

            def engine = new SimpleTemplateEngine()
            def template = engine.createTemplate(message).make(binding)
            return template.toString()
        } catch (Exception e) {
            LogUtils.severe("Error expanding message template: ${e.message}")
            return message // Return original message on error
        }
    }

    // Add this function inside the file, alongside the existing expandMessage function
    static def getBindingMap(def node, def otherNode = null) {
        try {
            def pathToRoot = node.pathToRoot
            def rootText = node.mindMap.root.text
            pathToRoot = pathToRoot.take(pathToRoot.size() - 1) // Exclude the node itself
            String ancestorContents = pathToRoot*.plainText.join('\n')
            String siblingContents = node.isRoot() ? '' : node.parent.children.findAll { it != node }*.plainText.join('\n')

            def result = [
                    rootText        : rootText,
                    nodeContent     : node.plainText,
                    ancestorContents: ancestorContents,
                    siblingContents : siblingContents
            ]
            
            // Add other node content if provided
            if (otherNode) {
                result.otherNodeContent = otherNode.plainText
            }
            
            return result
        } catch (Exception e) {
            LogUtils.severe("Error creating binding map: ${e.message}")
            return [
                nodeContent: node?.plainText ?: "", 
                otherNodeContent: otherNode?.plainText ?: ""
            ] // Return minimal binding on error
        }
    }
}
