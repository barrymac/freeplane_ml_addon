package com.barrymac.freeplane.addons.llm

/**
 * Helper class for node operations
 */
class NodeHelper {
    /**
     * Validates that exactly two connected nodes are selected
     *
     * @param selectedNodes The list of selected nodes
     * @return A tuple containing [sourceNode, targetNode] if valid
     * @throws Exception if validation fails
     */
    static def validateAndGetConnectedNodes(selectedNodes) {
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

        // Return the nodes in selection order
        return [node1, node2]
    }

    /**
     * Formats the analysis map into an indented string and adds it as a branch
     *
     * @param nodeProxy The node to add the branch to
     * @param analysisMap The map of analysis data
     * @param comparisonType The type of comparison performed
     * @param model The LLM model used
     * @param logger The logger instance
     * @param addModelTagRecursivelyFunc Optional function to tag nodes with model info
     */
    static void addAnalysisToNodeAsBranch(nodeProxy, Map analysisMap, String comparisonType, String model, logger, addModelTagRecursivelyFunc = null) {
        logger.info("Attempting to add analysis to node: ${nodeProxy.text}")
        if (analysisMap.isEmpty()) {
            logger.warn("No analysis data to add for node: ${nodeProxy.text}")
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
        logger.info("Formatted analysis string for node ${nodeProxy.text}:\n---\n${formattedAnalysis}\n---")

        // Add the formatted string as a new branch
        try {
            // Get the set of children *before* adding
            def childrenBeforeSet = nodeProxy.children.toSet()
            nodeProxy.appendTextOutlineAsBranch(formattedAnalysis) // Call method on the NodeProxy
            // Get the set of children *after* adding
            def childrenAfterSet = nodeProxy.children.toSet()
            // Find the newly added root node (difference between the sets)
            def addedBranchRoot = (childrenAfterSet - childrenBeforeSet).find { true } // Get the single added node

            logger.info("Successfully called appendTextOutlineAsBranch for node: ${nodeProxy.text}")

            // Use the passed-in tagging function
            if (addedBranchRoot && addModelTagRecursivelyFunc != null) {
                try {
                    // Use the passed function reference
                    addModelTagRecursivelyFunc(addedBranchRoot, model, logger)
                    logger.info("CompareNodes: Tag 'LLM:${model.replace('/', '_')}' applied to comparison branch starting with node: ${addedBranchRoot.text}")
                } catch (Exception e) {
                    logger.warn("Failed to apply node tagger function".toString(), e as Throwable)
                }
            } else if (addModelTagRecursivelyFunc == null) {
                logger.warn("CompareNodes: Node tagging function was not provided for node: ${nodeProxy.text}")
            } else {
                logger.warn("CompareNodes: Could not identify newly added comparison branch root for tagging on node: ${nodeProxy.text}")
            }
        } catch (Exception e) {
            // Force message to String and ensure exception is Throwable
            logger.warn("Error calling appendTextOutlineAsBranch or tagging for node ${nodeProxy.text}".toString(), e as Throwable)
        }
    }
}
