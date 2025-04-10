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
                    // Image-specific variables (generatedPrompt first for clarity)
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
            // Add fallback for missing variables
            def safeBinding = binding.withDefault { "" }
            return new SimpleTemplateEngine()
                    .createTemplate(template)
                    .make(safeBinding)
                    .toString()
        } catch (Exception e) {
            LogUtils.severe("Error expanding template: ${e.message}")
            return template
        }
    }
    
    /**
     * Builds an enhanced image prompt by combining user input with a system prompt template
     * 
     * @param userTemplate The user template with variables
     * @param systemTemplate Template for the system prompt with placeholders
     * @param context Additional context parameters to include in the template binding
     * @return A structured prompt optimized for image generation
     */
    static String buildImagePrompt(String userTemplate, String systemTemplate, Map context=[:]) {
        try {
            // First expand user template with node context
            def userExpanded = expandTemplate(userTemplate, context)
            
            // Then combine with system template
            return expandTemplate(systemTemplate, [
                user_prompt: userExpanded,
                style: context.style ?: 'digital art',
                details: context.details ?: 'high detail',
                colors: context.colors ?: 'vibrant',
                lighting: context.lighting ?: 'dramatic'
            ])
        } catch(Exception e) {
            LogUtils.severe("Failed to build image prompt: ${e.message}")
            return userTemplate // Fallback to raw input
        }
    }
}
