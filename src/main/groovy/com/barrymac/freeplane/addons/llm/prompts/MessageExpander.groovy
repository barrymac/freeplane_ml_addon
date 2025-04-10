package com.barrymac.freeplane.addons.llm.prompts

import groovy.text.SimpleTemplateEngine
import org.freeplane.core.util.LogUtils

class MessageExpander {
    static Map createBinding(def node, def otherNode,
                             String dimension, String pole1, String pole2) {
        try {
            def pathToRoot = node.pathToRoot
            def rootText = node.mindMap.root.text
            pathToRoot = pathToRoot.take(pathToRoot.size() - 1)

            [
                    rootText            : rootText,
                    nodeContent         : node.plainText,
                    otherNodeContent    : otherNode?.plainText ?: "",
                    ancestorContents    : pathToRoot*.plainText.join('\n'),
                    siblingContents     : node.isRoot() ? '' :
                            node.parent.children.findAll { it != node }*.plainText.join('\n'),
                    comparativeDimension: dimension,
                    pole1               : pole1,
                    pole2               : pole2
            ]
        } catch (Exception e) {
            LogUtils.severe("Error creating binding map: ${e.message}")
            return [
                    nodeContent         : node?.plainText ?: "",
                    otherNodeContent    : otherNode?.plainText ?: "",
                    comparativeDimension: dimension,
                    pole1               : pole1,
                    pole2               : pole2
            ]
        }
    }

    static String expandTemplate(String template, Map binding) {
        try {
            new SimpleTemplateEngine()
                    .createTemplate(template)
                    .make(binding)
                    .toString()
        } catch (Exception e) {
            LogUtils.severe("Error expanding template: ${e.message}")
            return template
        }
    }
    
    /**
     * Builds an enhanced image prompt by combining user input with a system prompt template
     * 
     * @param basePrompt The original user prompt text
     * @param systemPromptTemplate Template for the system prompt with placeholders
     * @param context Additional context parameters to include in the template binding
     * @return A structured prompt optimized for image generation
     */
    static String buildImagePrompt(String basePrompt, String systemPromptTemplate, Map context=[:]) {
        try {
            // Create binding with base prompt and any additional context
            def binding = [
                basePrompt: basePrompt,
                dimension: context.dimension ?: 'visual concept',
                style: context.style ?: 'digital art',
                details: context.details ?: 'high detail'
            ] + context
            
            // Expand the system prompt template
            def systemPrompt = new SimpleTemplateEngine()
                .createTemplate(systemPromptTemplate)
                .make(binding)
                .toString()
                
            // For Novita API, we just return the expanded prompt directly
            // This is the prompt that will be sent to the image generation API
            return basePrompt
            
        } catch(Exception e) {
            LogUtils.severe("Failed to build image prompt: ${e.message}")
            return basePrompt // Fallback to raw input
        }
    }
}
