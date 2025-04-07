package com.barrymac.freeplane.addons.llm

import groovy.util.logging.Slf4j

/**
 * Helper class for node operations
 */
@Slf4j
class NodeHelper {
    /**
     * Validates that exactly two connected nodes are selected
     *
     * @param selectedNodes The list of selected nodes
     * @return A tuple containing [sourceNode, targetNode] if valid
     * @throws Exception if validation fails
     */
    static def validateAndGetConnectedNodes(selectedNodes) {
        try {
            if (selectedNodes.size() != 2) {
                throw new Exception("Please select exactly two nodes to compare.")
            }

            def node1 = selectedNodes[0]
            def node2 = selectedNodes[1]

            // Find connectors between node1 and node2 (in either direction)
            def connectorsOut = node1.connectorsOut.findAll { it.target == node2 }
            def connectorsIn = node1.connectorsIn.findAll { it.source == node2 }
            def allConnectorsBetween = connectorsOut + connectorsIn

            if (allConnectorsBetween.size() == 0) {
                throw new Exception("The two selected nodes are not connected. Please add a connector between them.")
            }

            if (allConnectorsBetween.size() > 1) {
                throw new Exception("There are multiple connectors between the selected nodes. Please ensure there is only one.")
            }

            log.debug("Found valid connection between nodes: '{}' and '{}'", node1.text, node2.text)
            // Return the nodes in selection order
            return [node1, node2]
        } catch (Exception e) {
            log.error("Node validation failed", e)
            throw e
        }
    }

    /**
     * Formats the analysis map into an indented string and adds it as a branch
     *
     * @param nodeProxy The node to add the branch to
     * @param analysisMap The map of analysis data
     * @param comparisonType The type of comparison performed
     * @param model The LLM model used
     * @param addModelTagRecursivelyFunc Optional function to tag nodes with model info
     */
    static void addAnalysisToNodeAsBranch(nodeProxy, Map analysisMap, String comparisonType, String model, addModelTagRecursivelyFunc = null) {
        log.info("Attempting to add analysis to node: {}", nodeProxy.text)
        if (analysisMap.isEmpty()) {
            log.warn("No analysis data to add for node: {}", nodeProxy.text)
            return
        }

        // Format the map into an indented string
        def builder = new StringBuilder()
        builder.append("Comparison (${comparisonType})\n") // Root of the new branch
        analysisMap.each { category, points ->
            builder.append("    ${category}\n") // Indent level 1 for category
            points.each { point ->
                builder.append("        ${point}\n") // Indent level 2 for points
            }
        }
        def formattedAnalysis = builder.toString().trim()
        log.debug("Formatted analysis string for node {}:\n---\n{}\n---", nodeProxy.text, formattedAnalysis)

        // Add the formatted string as a new branch
        try {
            // Get the set of children *before* adding
            def childrenBeforeSet = nodeProxy.children.toSet()
            nodeProxy.appendTextOutlineAsBranch(formattedAnalysis) // Call method on the NodeProxy
            // Get the set of children *after* adding
            def childrenAfterSet = nodeProxy.children.toSet()
            // Find the newly added root node (difference between the sets)
            def addedBranchRoot = (childrenAfterSet - childrenBeforeSet).find { true } // Get the single added node

            log.info("Successfully called appendTextOutlineAsBranch for node: {}", nodeProxy.text)

            // Use the passed-in tagging function
            if (addedBranchRoot && addModelTagRecursivelyFunc != null) {
                try {
                    // Use the passed function reference
                    addModelTagRecursivelyFunc(addedBranchRoot, model)
                    log.info("CompareNodes: Tag 'LLM:{}' applied to comparison branch starting with node: {}",
                            model.replace('/', '_'), addedBranchRoot.text)
                } catch (Exception e) {
                    log.warn("Failed to apply node tagger function", e)
                }
            } else if (addModelTagRecursivelyFunc == null) {
                log.warn("CompareNodes: Node tagging function was not provided for node: {}", nodeProxy.text)
            } else {
                log.warn("CompareNodes: Could not identify newly added comparison branch root for tagging on node: {}", nodeProxy.text)
            }
        } catch (Exception e) {
            log.warn("Error calling appendTextOutlineAsBranch or tagging for node {}", nodeProxy.text, e)
        }
    }
}
