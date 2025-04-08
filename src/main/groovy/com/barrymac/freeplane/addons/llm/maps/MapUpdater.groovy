package com.barrymac.freeplane.addons.llm.maps

import org.freeplane.core.util.LogUtils
import org.freeplane.plugin.script.proxy.NodeProxy

class MapUpdater {
    static void createComparisonStructure(
            def sourceNode,
            def targetNode,
            Map sourceAnalysis,
            Map targetAnalysis,
            String dimension,
            String model,
            Closure addModelTag) {
        try {
            def parent = sourceNode.parent
            def centralNode = parent.createChild()
            centralNode.text = "Comparison: ${dimension}"
            centralNode.style.backgroundColorCode = '#E8E8FF'

            // Create and populate source concept node
            def sourceChild = centralNode.createChild(sourceNode.text)
            if (!sourceAnalysis.isEmpty()) {
                NodeHelper.addJsonComparison(sourceChild, sourceAnalysis, 'concept_a')
            } else {
                sourceChild.createChild("(No analysis generated)")
            }

            // Create and populate target concept node
            def targetChild = centralNode.createChild(targetNode.text)
            if (!targetAnalysis.isEmpty()) {
                NodeHelper.addJsonComparison(targetChild, targetAnalysis, 'concept_b')
            } else {
                targetChild.createChild("(No analysis generated)")
            }

            // Add connectors
            centralNode.addConnectorTo(sourceNode)
            centralNode.addConnectorTo(targetNode)

            // Apply LLM tag if provided
            if (addModelTag) {
                try {
                    addModelTag(centralNode, model)
                    LogUtils.info("Applied LLM tag to central node: ${centralNode.text}")
                } catch (Exception e) {
                    LogUtils.warn("Failed to apply model tag: ${e.message}")
                }
            }

        } catch (Exception e) {
            LogUtils.severe("Failed to create comparison structure: ${e.message}")
            throw e
        }
    }
}
