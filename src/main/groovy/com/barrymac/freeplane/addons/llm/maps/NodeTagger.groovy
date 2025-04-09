package com.barrymac.freeplane.addons.llm.maps

import org.freeplane.core.util.LogUtils

class NodeTagger {
    static void tagWithModel(def node, String modelName) {
        if (!node || !modelName?.trim()) return

        def tagName = "LLM:${modelName.replace('/', '_')}"

        try {
            node.tags.add(tagName)
            node.children.each { child ->
                tagWithModel(child, modelName)
            }
        } catch (Exception e) {
            LogUtils.warn("Failed to add tag '${tagName}' to node ${node.text}: ${e.message}")
        }
    }
}
