package com.barrymac.freeplane.addons.llm

import org.freeplane.core.util.LogUtils

/**
 * Utility class for parsing LLM responses into structured formats
 */
class ResponseParser {
    /**
     * Parses LLM response text, expecting a single category heading (the relevant pole)
     * followed by bullet points describing the first idea relative to the second.
     * Attempts to gracefully handle cases where the LLM provides more than one heading.
     *
     * @param analysisText The raw text from the LLM response
     * @return A map containing a single category key (the first pole found) and a list of points as its value.
     */
    static Map parseAnalysis(String analysisText) {
        LogUtils.info("ResponseParser: Received raw text:\n---\n${analysisText}\n---")
        try {
            def results = [:]
            String currentCategory = null
            def currentPointsList = null
            
            analysisText.eachLine { line ->
                line = line.trim()
                if (line.isEmpty()) return // continue
                
                // Check for a heading line (ends with ':' and doesn't start with a bullet)
                if (line.endsWith(':') && !line.matches(/^[-*+•·]\s*.*/)) {
                    currentCategory = line.replaceAll(':', '').trim()
                    if (currentCategory) {
                        LogUtils.info("ResponseParser: Found category heading: '${currentCategory}'")
                        currentPointsList = []
                        results[currentCategory] = currentPointsList
                    }
                }
                // Check for a point line (starts with a bullet) after a category was found
                else if (currentCategory != null && line.matches(/^[-*+•·]\s*.*/)) {
                    def point = line.replaceAll(/^[-*+•·]\s*/, '').trim() // Remove leading bullet
                    if (point) {
                        LogUtils.info("ResponseParser: Adding point under '${currentCategory}': '${point}'")
                        currentPointsList.add(point)
                    }
                }
                // Handle lines that might be continuations of points or noise - currently ignored unless they start with a bullet.
            }

            // Fallback: If no category header was found, but the text contains bullet points
            if (results.isEmpty() && analysisText?.contains("- ")) {
                LogUtils.info("ResponseParser: No category heading found, collecting all bullet points under 'Analysis'.")
                def pointsList = analysisText.split('\n')
                        .collect { it.trim() }
                        .findAll { it.startsWith('- ') }
                        .collect { it.substring(2).trim() }
                        .findAll { it != null && !it.isEmpty() }
                if (pointsList) {
                    results["Analysis"] = pointsList
                }
            }

            // Filter out empty categories or categories with empty lists
            results = results.findAll { category, points -> points != null && !points.isEmpty() }

            // Final fallback if parsing yields nothing but text exists
            if (results.isEmpty() && analysisText?.trim()) {
                LogUtils.warn("ResponseParser: Parsing yielded empty results despite non-empty input. Using raw text fallback.")
                results["Analysis"] = analysisText.trim().split('\n').collect { it.trim() }.findAll { !it.isEmpty() }
            }

            LogUtils.info("ResponseParser: Final parsed map: ${results}")
            return results
        } catch (Exception e) {
            LogUtils.warn("ResponseParser: Failed to parse analysis text: ${e.message}")
            // Return a simple map with the raw text on error
            return ["Analysis": [analysisText.trim()]]
        }
    }
}
