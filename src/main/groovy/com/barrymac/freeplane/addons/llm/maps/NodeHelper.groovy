package com.barrymac.freeplane.addons.llm.maps

import org.freeplane.core.util.LogUtils

/**
 * Helper class for node operations
 */
class NodeHelper {
    /**
     * Adds the structured analysis map as child nodes under a given parent node.
     * Expects the map to contain one key (the relevant pole/category).
     *
     * @param parentNode The node under which to add the analysis structure.
     * @param analysisMap A map containing one category key and a list of points.
     */
    static void addJsonComparison(parentNode, Map analysisData, String conceptName, String pole1, String pole2) {
        def conceptSpecificAnalysis = analysisData?.concepts?.get(conceptName)

        if (!conceptSpecificAnalysis) {
            LogUtils.warn("No analysis data found for concept '${conceptName}' in provided map.")
            parentNode.createChild("Invalid or missing analysis format for ${conceptName}")
            return
        }

        [pole1, pole2].each { pole ->
            def points = conceptSpecificAnalysis[pole] ?: []
            if (points.isEmpty()) {
                LogUtils.info("No points found for pole '${pole}' for concept '${conceptName}'")
                return
            }

            def poleNode = parentNode.createChild(pole)
            poleNode.style.backgroundColorCode = getPoleColor(pole, pole1)

            points.eachWithIndex { point, i ->
                def cleanPoint = point.toString().trim()
                if (cleanPoint) {
                    def pointNode = poleNode.createChild("${i + 1}. ${cleanPoint}")
                    pointNode.style.backgroundColorCode = '#FFFFFF'
                }
            }
        }
    }

    private static String getPoleColor(String currentPole, String firstPole) {
        currentPole == firstPole ? '#DFF0D8' : '#F8D7DA'
    }
    /**
     * Validates that exactly two nodes are selected
     * (No longer checks for existing connection)
     *
     * @param selectedNodes The list of selected nodes
     * @return A tuple containing [node1, node2] if valid
     * @throws Exception if validation fails
     */
    static def validateSelectedNodes(selectedNodes) {
        try {
            if (selectedNodes.size() != 2) {
                throw new Exception("Please select exactly two nodes to compare.")
            }

            def node1 = selectedNodes[0]
            def node2 = selectedNodes[1]

            LogUtils.info("Valid selection: Comparing nodes '${node1.text}' and '${node2.text}'")
            return [node1, node2] // Return nodes in selection order
        } catch (Exception e) {
            LogUtils.severe("Node validation failed: ${e.message}")
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
     * @param otherNode Optional reference to the other node being compared
     */
    static void addAnalysisToNodeAsBranch(nodeProxy, Map analysisMap, String comparisonType,
                                          String model, addModelTagRecursivelyFunc = null,
                                          otherNode = null) {
        LogUtils.info("Attempting to add analysis to node: ${nodeProxy.text}")
        if (analysisMap.isEmpty()) {
            LogUtils.warn("No analysis data to add for node: ${nodeProxy.text}")
            return
        }

        NodeOperations.addAnalysisBranch(
                nodeProxy,
                analysisMap,
                null, // No pre-formatted content
                model,
                addModelTagRecursivelyFunc,
                comparisonType
        )
    }
}
