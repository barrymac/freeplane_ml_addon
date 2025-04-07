package com.barrymac.freeplane.addons.llm

import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException
import org.freeplane.core.util.LogUtils

/**
 * Handles node-related operations with proper error handling and logging
 */
//@CompileStatic
class NodeOperations {
    /**
     * Adds analysis content as a branch to a node with proper tagging
     *
     * @param parentNode The node to add the branch to
     * @param content The content for the new branch
     * @param model The LLM model used for generation
     * @param tagger Function to handle node tagging
     * @throws LlmAddonException if the operation fails
     */
    static void addAnalysisBranch(def parentNode, String content,
                                  String model, Closure tagger) {
        try {
            LogUtils.info("Adding analysis branch to node: ${parentNode.text}")

            // Track existing children before adding
            def childrenBefore = parentNode.children.toSet()

            // Add the content as a new branch
            parentNode.appendTextOutlineAsBranch(content)

            // Find newly added nodes
            def newNodes = parentNode.children.toSet() - childrenBefore

            if (newNodes.isEmpty()) {
                LogUtils.warn("No new nodes created when adding branch to: ${parentNode.text}")
                return
            }

            // Tag all new nodes recursively
            newNodes.each { node ->
                try {
                    tagger.call(node, model)
                    LogUtils.info("Tagged node: ${node.text}")
                } catch (Exception taggingError) {
                    LogUtils.warn("Failed to tag node: ${node.text}: ${taggingError.message}")
                }
            }

        } catch (Exception e) {
            String errorMsg = "Failed to add branch to node: ${parentNode.text}"
            LogUtils.severe("${errorMsg}: ${e.message}")
            throw new LlmAddonException(errorMsg, e)
        }
    }

    /**
     * Validates node connection structure between two nodes
     *
     * @param node1 First selected node
     * @param node2 Second selected node
     * @return List containing validated nodes in order [source, target]
     * @throws LlmAddonException if validation fails
     */
    List validateConnection(def node1, def node2) {
        try {
            LogUtils.info("Validating connection between ${node1.text} and ${node2.text}")

            def connectors = node1.connectorsOut.findAll { it.target == node2 } +
                    node1.connectorsIn.findAll { it.source == node2 }

            if (connectors.size() != 1) {
                String msg = connectors.isEmpty() ?
                        "No connection found between nodes" :
                        "Multiple connections between nodes"
                throw new LlmAddonException(msg)
            }

            // Return nodes in connection order
            def connector = connectors.first()
            return [connector.source, connector.target]

        } catch (LlmAddonException e) {
            throw e // Re-throw validation errors
        } catch (Exception e) {
            String errorMsg = "Connection validation failed"
            LogUtils.severe("${errorMsg}: ${e.message}")
            throw new LlmAddonException(errorMsg, e)
        }
    }

    /**
     * Formats analysis results into a structured string
     *
     * @param analysisMap Map of analysis categories and points
     * @param comparisonType Type of comparison being made
     * @return Formatted string for node insertion
     */
    static String formatAnalysis(Map<String, List<String>> analysisMap,
                                 String comparisonType) {
        try {
            LogUtils.info("Formatting analysis for ${comparisonType}")

            def builder = new StringBuilder().with {
                append("Comparison (${comparisonType})\n")
                analysisMap.each { category, points ->
                    append("    ${category}\n")
                    points.each { point ->
                        append("        ${point}\n")
                    }
                }
                return it
            }

            return builder.toString().trim()

        } catch (Exception e) {
            LogUtils.warn("Analysis formatting failed: ${e.message}")
            return "Analysis formatting error: ${e.message}"
        }
    }
}
