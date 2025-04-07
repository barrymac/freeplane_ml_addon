package com.barrymac.freeplane.addons.llm

import org.freeplane.core.util.LogUtils

/**
 * Utility class for parsing LLM responses into structured formats
 */
class ResponseParser {
    /**
     * Parses LLM response text, expecting a single category heading (the relevant pole)
     * followed by bullet points describing the first idea relative to the second.
     *
     * @param analysisText The raw text from the LLM response
     * @return A map containing a single category key (the pole) and a list of points as its value.
     */
    static Map parseAnalysis(String analysisText) {
        try {
            def results = [:] // Map to store results, e.g., ["Lower Cost": ["Point 1", "Point 2"]]
            String currentCategory = null
            def pointsList = []

            analysisText.eachLine { line ->
                line = line.trim()
                if (line.isEmpty()) return // continue

                // Check for a heading line (ends with ':' and doesn't start with a bullet)
                if (currentCategory == null && line.endsWith(':') && !line.matches(/^[-*+•·]\s*.*/)) {
                    currentCategory = line.replaceAll(':', '').trim()
                    if (currentCategory) {
                        results[currentCategory] = pointsList // Assign the list (might be empty initially)
                    }
                }
                // Check for a point line (starts with a bullet)
                else if (line.matches(/^[-*+•·]\s*.*/)) {
                    def point = line.replaceAll(/^[-*+•·]\s*/, '').trim() // Remove leading bullet
                    if (point) {
                        pointsList.add(point)
                    }
                }
                // Handle lines that are neither headers nor bullet points (could be part of multi-line points or unexpected format)
                // For now, we'll ignore them unless they follow a bullet point (handled implicitly by list add)
                // Or if no category is found yet, maybe the LLM just gave points without a header
                else if (currentCategory == null && !point.isEmpty()) {
                     pointsList.add(line) // Treat as a point if no category found yet
                }
            }

            // If points were collected but no category header was found
            if (currentCategory == null && !pointsList.isEmpty()) {
                 results["Analysis"] = pointsList // Use a generic category key
            }
            // Ensure the category in the map actually points to the final list
            else if (currentCategory != null) {
                 results[currentCategory] = pointsList
            }

            // Filter out empty categories or categories with empty lists
            results = results.findAll { category, points -> points != null && !points.isEmpty() }

            // If still empty after parsing, but original text wasn't, add raw text as fallback
            if (results.isEmpty() && analysisText?.trim()) {
                 results["Analysis"] = analysisText.trim().split('\n').collect { it.trim() }.findAll { !it.isEmpty() }
            }

            return results
        } catch (Exception e) {
            LogUtils.warn("Failed to parse analysis text: ${e.message}")
            // Return a simple map with the raw text on error
            return ["Analysis": [analysisText.trim()]]
        }
    }
}
