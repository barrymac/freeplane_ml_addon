package com.barrymac.freeplane.addons.llm

/**
 * Utility class for parsing LLM responses into structured formats
 */
class ResponseParser {
    /**
     * Parses LLM response text into categories and points.
     * Assumes simple structure: "Category:\n- Point 1\n- Point 2" or just lines of text.
     *
     * @param analysisText The raw text from the LLM response
     * @return A map with categories as keys and lists of points as values
     */
    static Map parseAnalysis(String analysisText) {
        def results = [:] // Map to store results, e.g., ["Pros": ["Point 1", "Point 2"], "Cons": ["Point A"]]
        def currentCategory = null
        def potentialPoints = []

        analysisText.eachLine { line ->
            line = line.trim()
            if (line.isEmpty()) return // continue

            // Basic heading detection (ends with ':')
            if (line.endsWith(':') && line.length() > 1) {
                // Store previous points if any
                if (currentCategory != null && !potentialPoints.isEmpty()) {
                    results[currentCategory] = potentialPoints
                }
                // Start new category
                currentCategory = line.substring(0, line.length() - 1).trim()
                results[currentCategory] = [] // Initialize category
                potentialPoints = [] // Reset points buffer
            } else {
                // Assume it's a point for the current category or general analysis
                def point = line.replaceAll(/^[-*+•·]\s*/, '').trim() // Remove common leading bullets
                if (!point.isEmpty()) {
                    potentialPoints.add(point)
                }
            }
        }

        // Add any remaining points under the last category
        if (currentCategory != null && !potentialPoints.isEmpty()) {
            results[currentCategory] = (results[currentCategory] ?: []) + potentialPoints
        }

        // Handle cases where no headings were found - treat the whole text as one category
        if (results.isEmpty() && !potentialPoints.isEmpty()) {
            results["Analysis"] = potentialPoints
        } else if (results.isEmpty() && !analysisText.trim().isEmpty()) {
            // Fallback if potentialPoints is also empty but text exists
            results["Analysis"] = analysisText.trim().split('\n').collect { it.trim() }.findAll { !it.isEmpty() }
        }

        // Filter out empty categories
        results = results.findAll { category, points -> !points.isEmpty() }

        return results
    }
}
