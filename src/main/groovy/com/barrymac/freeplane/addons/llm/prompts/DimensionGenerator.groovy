package com.barrymac.freeplane.addons.llm.prompts

import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException
import groovy.json.JsonSlurper
import org.freeplane.core.util.LogUtils

class DimensionGenerator {
    static class DimensionData {
        String pole1
        String pole2
    }

    static DimensionData generateDimension(Closure apiCall, String model, String systemTemplate, String comparisonType) {
        def dimensionPayload = [
                'model'      : model,
                'messages'   : [
                        [role: 'system', content: systemTemplate],
                        [role: 'user', content: "Create a focused comparative dimension for analyzing: ${comparisonType}"]
                ],
                'temperature': 0.2,
                'max_tokens' : 100
        ]

        LogUtils.info("Generating comparative dimension for: ${comparisonType}")

        def maxRetries = 2
        def attempts = 0
        def dimensionContent = null

        while (attempts <= maxRetries) {
            try {
                def dimensionResponse = apiCall(dimensionPayload)
                dimensionContent = new JsonSlurper().parseText(dimensionResponse)?.choices[0]?.message?.content
                def (pole1, pole2) = parseDimension(dimensionContent)
                return new DimensionData(pole1: pole1, pole2: pole2)
            } catch (Exception e) {
                attempts++
                if (attempts > maxRetries) throw e

                dimensionPayload.messages.add([role: 'assistant', content: dimensionContent])
                dimensionPayload.messages.add([role: 'user', content: """
                    Format was incorrect. Please STRICTLY follow:
                    Pole 1: [2-3 words]; Pole 2: [2-3 words]
                    No other text. Just the poles in this format.
                """.stripIndent()])
            }
        }
        throw new LlmAddonException("Failed to generate valid dimension after ${maxRetries} attempts")
    }

    private static List<String> parseDimension(String response) throws LlmAddonException {
        def pattern = ~/(?i)(Pole\s*1:\s*([^;]+?)\s*;\s*Pole\s*2:\s*([^\n]+?))\s*$/
        def matcher = pattern.matcher(response)

        if (matcher.find()) {
            def pole1 = matcher[0][2].trim().replaceAll(/["']/, '')
            def pole2 = matcher[0][3].trim().replaceAll(/["']/, '')
            return [pole1, pole2]
        }

        def altPatterns = [
                ~/([A-Z][\w\s]+?)\s*\/\/\s*([A-Z][\w\s]+)/,
                ~/(.+)\s+vs\s+(.+)/,
                ~/^([^;]+);([^;]+)$/
        ]

        for (p in altPatterns) {
            matcher = p.matcher(response)
            if (matcher.find() && matcher.groupCount() >= 2) {
                return [matcher[0][1].trim(), matcher[0][2].trim()]
            }
        }

        throw new LlmAddonException("""Invalid dimension format. Received: '$response'
            Expected format: 'Pole 1: [concept]; Pole 2: [concept]'""")
    }
}
