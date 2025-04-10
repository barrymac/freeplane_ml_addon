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
                    // Add new image-specific variables
                    generatedPrompt     : '',  // Will be set by caller
                    style               : 'digital art',
                    details             : 'high detail',
                    colors              : 'vibrant',
                    lighting            : 'dramatic',
                    // Existing variables
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
                details: context.details ?: 'high detail',
                colors: context.colors ?: 'vibrant',
                lighting: context.lighting ?: 'dramatic'
            ] + context
            
            // Expand the system prompt template
            def systemPrompt = new SimpleTemplateEngine()
                .createTemplate(systemPromptTemplate)
                .make(binding)
                .toString()
                
            // Extract the core prompt from the expanded template
            // For Novita API, we need to send a clean, focused prompt
            // Look for patterns that might indicate the core prompt section
            def lines = basePrompt.readLines()
            def corePrompt = lines.size() > 0 ? lines[0] : basePrompt
            
            // If the first line contains "concept:" or similar, extract just the concept part
            if (corePrompt.contains(":")) {
                corePrompt = corePrompt.split(":", 2)[1].trim()
            }
            
            LogUtils.info("Extracted core prompt for image generation: ${corePrompt}")
            return corePrompt
            
        } catch(Exception e) {
            LogUtils.severe("Failed to build image prompt: ${e.message}")
            return basePrompt // Fallback to raw input
        }
    }
}
