package com.barrymac.freeplane.addons.llm

import groovy.util.logging.Slf4j

@Slf4j
class NodeTagger {
    // Helper function to recursively add a dynamic tag (e.g., "LLM:model-name") to a node and its children
    static def addModelTagRecursively(node, modelName) {
        if (node == null || modelName == null || modelName.trim().isEmpty()) return

        // Sanitize model name slightly for tag (replace slashes, common in OpenRouter models)
        def sanitizedModelName = modelName.replace('/', '_')
        def tagName = "LLM:${sanitizedModelName}"

        try {
            node.tags.add(tagName)
            node.children.each { child -> addModelTagRecursively(child, modelName) }
            // Pass original modelName recursively
        } catch (Exception e) {
            // Log error if tagging fails for any reason
            log.warn("Failed to add tag '${tagName}' to node ${node.text}", e)
        }
    }
}
