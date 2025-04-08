package com.barrymac.freeplane.addons.llm.services

import com.barrymac.freeplane.addons.llm.*
import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException
import org.freeplane.plugin.script.proxy.NodeProxy

class ComparisonService {
    private final Dependencies deps
    
    ComparisonService(Dependencies deps) {
        this.deps = deps
    }

    def compareNodes(NodeProxy source, NodeProxy target, String comparisonType) {
        def dimensionData = DimensionGenerator.generateDimension(
            deps.apiCaller.make_api_call.curry(deps.configManager.provider, deps.configManager.apiKey),
            deps.configManager.model,
            deps.messageLoader.dimensionSystemTemplate,
            comparisonType
        )
        
        [
            dimension: "${dimensionData.pole1} vs ${dimensionData.pole2}",
            pole1: dimensionData.pole1,
            pole2: dimensionData.pole2,
            prompts: [
                source: buildPrompt(source, target, dimensionData),
                target: buildPrompt(target, source, dimensionData)
            ]
        ]
    }

    private String buildPrompt(NodeProxy subject, NodeProxy other, DimensionGenerator.DimensionData dimension) {
        PromptBuilder.buildComparisonPrompt(
            subject, other,
            deps.messageLoader.compareNodesUserMessageTemplate,
            "${dimension.pole1} vs ${dimension.pole2}",
            dimension.pole1,
            dimension.pole2
        )
    }
}
