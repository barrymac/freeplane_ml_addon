package com.barrymac.freeplane.addons.llm

import groovy.json.JsonSlurper
import org.freeplane.core.util.LogUtils

class ResponseProcessor {
    static Map parseApiResponse(String response, String pole1, String pole2) {
        try {
            def content = JsonUtils.extractLlmContent(response)
            LogUtils.info("Raw API response content:\n---\n${content}\n---")
            
            def analysis = ResponseParser.parseJsonAnalysis(content, pole1, pole2)
            if (analysis.error) {
                throw new Exception("Analysis error: ${analysis.error}")
            }
            
            LogUtils.info("Parsed analysis map: ${analysis}")
            return analysis
            
        } catch (Exception e) {
            LogUtils.severe("Failed to parse API response: ${e.message}")
            throw e
        }
    }
}
