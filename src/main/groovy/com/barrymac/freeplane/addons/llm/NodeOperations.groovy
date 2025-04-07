package com.barrymac.freeplane.addons.llm

import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException
import groovy.transform.CompileStatic
import org.slf4j.Logger

/**
 * Utility class for node operations
 */
@CompileStatic
class NodeOperations {
    /**
     * Adds analysis as a branch to a node
     *
     * @param node The node to add the branch to
     * @param content The content to add
     * @param model The model used for tagging
     * @param tagger The node tagger function
     * @param logger The logger
     * @throws LlmAddonException if adding the branch fails
     */
    static void addAnalysisBranch(def node, String content, String model, 
                                def tagger, Logger logger) {
        try {
            def childrenBefore = node.children.toSet()
            node.appendTextOutlineAsBranch(content)
            def newNodes = node.children.toSet() - childrenBefore
            
            newNodes.each { newNode -> 
                tagger.call(newNode, model, logger) 
            }
            
            logger.info("Added analysis branch to node: ${node.text}")
        } catch (Exception e) {
            throw new LlmAddonException("Failed to add branch to node: ${e.message}", e)
        }
    }
    
    /**
     * Formats analysis map into an indented string
     *
     * @param analysisMap The map of analysis data
     * @param comparisonType The type of comparison
     * @return Formatted string
     */
    static String formatAnalysisMap(Map analysisMap, String comparisonType) {
        def builder = new StringBuilder()
        builder.append("Comparison (${comparisonType})\n")
        
        analysisMap.each { category, points ->
            builder.append("    ${category}\n")
            points.each { point ->
                builder.append("        ${point}\n")
            }
        }
        
        return builder.toString().trim()
    }
}
