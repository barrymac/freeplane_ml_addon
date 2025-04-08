package com.barrymac.freeplane.addons.llm

import groovy.json.JsonSlurper
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException

class DimensionGenerator {
    static final int MAX_RETRIES = 2
    static final String CORRECTION_PROMPT = """
        Format was incorrect. Please STRICTLY follow:
        Pole 1: [2-3 words]; Pole 2: [2-3 words]
        No other text. Just the poles in this format.
    """
    
    static Map generateDimension(Closure makeApiCall, String model, String systemTemplate, String comparisonType) {
        def payload = [
            'model': model,
            'messages': [
                [role: 'system', content: systemTemplate],
                [role: 'user', content: "Create a focused comparative dimension for analyzing: ${comparisonType}"]
            ],
            'temperature': 0.2,
            'max_tokens': 100
        ]
        
        def attempts = 0
        def response = null
        
        while (attempts <= MAX_RETRIES) {
            try {
                response = makeApiCall(payload)
                def content = new JsonSlurper().parseText(response)?.choices[0]?.message?.content
                def (pole1, pole2) = parseDimensionResponse(content)
                LogUtils.info("Generated comparative dimension: ${pole1} vs ${pole2}")
                return [pole1: pole1, pole2: pole2]
            } catch (Exception e) {
                attempts++
                if (attempts > MAX_RETRIES) throw e
                
                payload.messages += [
                    [role: 'assistant', content: response],
                    [role: 'user', content: CORRECTION_PROMPT]
                ]
            }
        }
    }
    
    private static List<String> parseDimensionResponse(String response) {
        if (!response) throw new LlmAddonException("Empty dimension response")
        
        // Try multiple patterns
        def patterns = [
            ~/(?i)Pole\s*1:\s*([^;]+?)\s*;\s*Pole\s*2:\s*([^\n]+?)\s*$/,
            ~/([A-Z][\w\s]+?)\s*\/\/\s*([A-Z][\w\s]+)/,
            ~/(.+)\s+vs\s+(.+)/,
            ~/^([^;]+);([^;]+)$/
        ]
        
        for (pattern in patterns) {
            def matcher = pattern.matcher(response)
            if (matcher.find() && matcher.groupCount() >= 2) {
                def pole1 = matcher[0][1].trim().replaceAll(/["']/, '')
                def pole2 = matcher[0][2].trim().replaceAll(/["']/, '')
                return [pole1, pole2]
            }
        }
        
        throw new LlmAddonException("""Invalid dimension format. Received: '$response'
            Expected format: 'Pole 1: [concept]; Pole 2: [concept]'""")
    }
}
