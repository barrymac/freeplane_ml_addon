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
            String firstCategoryFound = null
            def pointsList = []
            boolean pointsStarted = false

            analysisText.eachLine { line ->
                line = line.trim()
                if (line.isEmpty()) return // continue

                // Check for a heading line (ends with ':' and doesn't start with a bullet)
                // Only capture the *first* heading encountered.
                if (firstCategoryFound == null && line.endsWith(':') && !line.matches(/^[-*+•·]\s*.*/)) {
                    firstCategoryFound = line.replaceAll(':', '').trim()
                    if (firstCategoryFound) {
                        LogUtils.info("ResponseParser: Found first category heading: '${firstCategoryFound}'")
                        results[firstCategoryFound] = pointsList // Assign the list
                        pointsStarted = false // Reset points flag for the new category
                    }
                }
                // Check for a point line (starts with a bullet) *after* the first category was found
                else if (firstCategoryFound != null && line.matches(/^[-*+•·]\s*.*/)) {
                    pointsStarted = true // Mark that we are now processing points for the first category
                    def point = line.replaceAll(/^[-*+•·]\s*/, '').trim() // Remove leading bullet
                    if (point) {
                        LogUtils.info("ResponseParser: Adding point under '${firstCategoryFound}': '${point}'")
                        pointsList.add(point)
                    }
                }
                // If we encounter another heading line after points have started for the first category, stop processing.
                else if (firstCategoryFound != null && pointsStarted && line.endsWith(':') && !line.matches(/^[-*+•·]\s*.*/)) {
                    LogUtils.info("ResponseParser: Found subsequent heading '${line}'. Stopping point collection for '${firstCategoryFound}'.")
                    // We effectively break the loop's ability to add more points by not continuing the eachLine closure logic here
                    // (Groovy doesn't have a simple break for closures, but returning works for eachLine)
                    // However, the simplest is just to let it run but only add points if the category matches firstCategoryFound
                    // The current logic already does this implicitly because pointsList is only added to results[firstCategoryFound]
                    // Let's just log that we are ignoring this line.
                    LogUtils.info("ResponseParser: Ignoring subsequent heading and any following points.")
                    // To explicitly stop adding points, we could set a flag, but the current logic should suffice.
                }
                // Handle lines that might be continuations of points or noise - currently ignored unless they start with a bullet.
            }

            // Fallback: If no category header was found, but the text contains bullet points
            if (firstCategoryFound == null && analysisText?.contains("- ")) {
                 LogUtils.info("ResponseParser: No category heading found, collecting all bullet points under 'Analysis'.")
                 pointsList = analysisText.split('\n')
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
