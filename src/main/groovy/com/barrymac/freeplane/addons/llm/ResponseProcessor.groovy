package com.barrymac.freeplane.addons.llm

import groovy.json.JsonSlurper
import org.freeplane.core.util.LogUtils

class ResponseProcessor {
    static Map parseApiResponse(String response, String pole1, String pole2) {
        try {
            def json = new JsonSlurper().parseText(response)
            def content = json?.choices[0]?.message?.content
            
            LogUtils.info("Raw API response content:\n---\n${content}\n---")
            
            if (!content?.trim()) {
                throw new Exception("Empty content in response. Model may have hit token limit.")
            }
            
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
