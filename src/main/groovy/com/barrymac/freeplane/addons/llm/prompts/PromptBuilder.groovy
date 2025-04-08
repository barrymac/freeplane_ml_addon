package com.barrymac.freeplane.addons.llm.prompts


import org.freeplane.core.util.LogUtils
import org.freeplane.plugin.script.proxy.NodeProxy

class PromptBuilder {
    static String buildComparisonPrompt(
            NodeProxy sourceNode,
            NodeProxy targetNode,
            String templateText,
            String comparativeDimension,
            String pole1,
            String pole2) {

        def binding = [
                nodeContent         : sourceNode.text,
                otherNodeContent    : targetNode.text,
                comparativeDimension: comparativeDimension,
                pole1               : pole1,
                pole2               : pole2
        ]

        LogUtils.info("Building comparison prompt with binding: ${binding}")

        try {
            def result = MessageExpander.expandTemplate(templateText, binding)
            LogUtils.info("Generated prompt:\n${result}")
            return result
        } catch (Exception e) {
            LogUtils.severe("Failed to build comparison prompt: ${e.message}")
            throw new Exception("Failed to build comparison prompt: ${e.message}")
        }
    }
}
