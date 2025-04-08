package com.barrymac.freeplane.addons.llm.services

import com.barrymac.freeplane.addons.llm.Dependencies
import com.barrymac.freeplane.addons.llm.ResponseProcessor
import com.barrymac.freeplane.addons.llm.api.ApiPayloadBuilder
import com.barrymac.freeplane.addons.llm.prompts.DimensionGenerator
import com.barrymac.freeplane.addons.llm.prompts.PromptBuilder
import org.freeplane.core.util.LogUtils

class ComparisonService {
    private final Dependencies deps

    ComparisonService(Dependencies deps) {
        this.deps = deps
    }

    Map performFullComparison(def source, def target, String comparisonType) {
        LogUtils.info("Starting full comparison for type: ${comparisonType}")

        // 1. Generate Dimension
        def dimensionData = DimensionGenerator.generateDimension(
                deps.apiCaller.make_api_call.curry(deps.configManager.provider, deps.configManager.apiKey),
                deps.configManager.model,
                deps.messageLoader.dimensionSystemTemplate,
                comparisonType
        )
        def (pole1, pole2) = [dimensionData.pole1, dimensionData.pole2]
        def comparativeDimension = "${pole1} vs ${pole2}"
        LogUtils.info("Generated comparative dimension: ${comparativeDimension}")

        // 2. Build Prompts
        def sourceUserPrompt = PromptBuilder.buildComparisonPrompt(
                source, target,
                deps.messageLoader.compareNodesUserMessageTemplate,
                comparativeDimension, pole1, pole2
        )
        LogUtils.info("Source User Prompt built.")

        def targetUserPrompt = PromptBuilder.buildComparisonPrompt(
                target, source,
                deps.messageLoader.compareNodesUserMessageTemplate,
                comparativeDimension, pole1, pole2
        )
        LogUtils.info("Target User Prompt built.")

        // 3. Build API Payloads
        def systemMessage = deps.messageLoader.systemTemplate
        def sourcePayload = ApiPayloadBuilder.buildPayload(
                deps.configManager.model, systemMessage, sourceUserPrompt, deps.configManager.toMap()
        )
        def targetPayload = ApiPayloadBuilder.buildPayload(
                deps.configManager.model, systemMessage, targetUserPrompt, deps.configManager.toMap()
        )
        LogUtils.info("API Payloads built.")

        // 4. Make API Calls
        LogUtils.info("Requesting analysis for source node: ${source.text}")
        def sourceApiResponse = deps.apiCaller.make_api_call(
                deps.configManager.provider, deps.configManager.apiKey, sourcePayload
        )
        deps.validationHelper.validateApiResponse(sourceApiResponse, source.text)

        LogUtils.info("Requesting analysis for target node: ${target.text}")
        def targetApiResponse = deps.apiCaller.make_api_call(
                deps.configManager.provider, deps.configManager.apiKey, targetPayload
        )
        deps.validationHelper.validateApiResponse(targetApiResponse, target.text)

        // 5. Parse Responses
        LogUtils.info("Parsing API responses.")
        def sourceAnalysis = ResponseProcessor.parseApiResponse(sourceApiResponse, pole1, pole2)
        def targetAnalysis = ResponseProcessor.parseApiResponse(targetApiResponse, pole1, pole2)

        // 6. Validate Dimensions
        deps.validationHelper.validateDimensions(sourceAnalysis.dimension, targetAnalysis.dimension)
        LogUtils.info("Dimension consistency validated.")

        // 7. Return Results
        return [
                dimension     : comparativeDimension,
                pole1         : pole1,
                pole2         : pole2,
                sourceAnalysis: sourceAnalysis,
                targetAnalysis: targetAnalysis
        ]
    }
}
