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
            String pole1,
            String pole2,
            String model,
            Closure addModelTag) {
        try {
            def parent = sourceNode.parent
            def centralNode = parent.createChild()
            centralNode.text = "Comparison: ${dimension}"
            centralNode.style.backgroundColorCode = '#E8E8FF'

            // Create and populate source concept node
            def sourceChild = centralNode.createChild(sourceNode.text)
            if (sourceAnalysis?.concepts?.containsKey(sourceNode.text)) {
                NodeHelper.addJsonComparison(sourceChild, sourceAnalysis, sourceNode.text, pole1, pole2)
            } else {
                sourceChild.createChild("(No analysis generated for ${sourceNode.text})")
                LogUtils.warn("Source analysis map did not contain key: ${sourceNode.text}. Keys found: ${sourceAnalysis?.concepts?.keySet()}")
            }

            // Create and populate target concept node
            def targetChild = centralNode.createChild(targetNode.text)
            if (targetAnalysis?.concepts?.containsKey(targetNode.text)) {
                NodeHelper.addJsonComparison(targetChild, targetAnalysis, targetNode.text, pole1, pole2)
            } else {
                targetChild.createChild("(No analysis generated for ${targetNode.text})")
                LogUtils.warn("Target analysis map did not contain key: ${targetNode.text}. Keys found: ${targetAnalysis?.concepts?.keySet()}")
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
