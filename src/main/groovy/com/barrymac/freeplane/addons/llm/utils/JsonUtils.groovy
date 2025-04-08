package com.barrymac.freeplane.addons.llm.utils

import groovy.json.JsonSlurper
import org.freeplane.core.util.LogUtils

class JsonUtils {
    /**
     * Extracts the LLM content from a raw API response
     */
    static String extractLlmContent(String rawApiResponse) {
        try {
            def json = new JsonSlurper().parseText(rawApiResponse)
            def content = json?.choices[0]?.message?.content
            
            if (!content?.trim()) {
                throw new Exception("Empty content in response")
            }
            
            return content
            
        } catch (Exception e) {
            LogUtils.severe("Failed to extract LLM content: ${e.message}")
            throw e
        }
    }
}
