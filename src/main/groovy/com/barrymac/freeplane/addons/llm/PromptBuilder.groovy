package com.barrymac.freeplane.addons.llm

import groovy.text.SimpleTemplateEngine
import org.freeplane.core.util.LogUtils
import org.freeplane.plugin.script.proxy.NodeProxy

class PromptBuilder {
    static String buildComparisonPrompt(
            NodeProxy node,
            NodeProxy otherNode,
            String template,
            String dimension,
            String pole1,
            String pole2) {
        try {
            def binding = MessageExpander.getBindingMap(node, otherNode) + [
                comparativeDimension: dimension,
                pole1: pole1,
                pole2: pole2
            ]
            
            LogUtils.info("Building comparison prompt for node: ${node.text}")
            LogUtils.info("Binding map contains dimension? ${binding.containsKey('comparativeDimension')}")
            
            def prompt = new SimpleTemplateEngine()
                .createTemplate(template)
                .make(binding)
                .toString()
                
            LogUtils.info("Generated prompt for ${node.text}:\n${prompt}")
            return prompt
            
        } catch (Exception e) {
            LogUtils.severe("Failed to build comparison prompt: ${e.message}")
            throw e
        }
    }
}
