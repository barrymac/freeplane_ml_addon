package com.barrymac.freeplane.addons.llm

import groovy.json.JsonSlurper
import org.freeplane.core.util.LogUtils

/**
 * Utility class for parsing LLM responses into structured formats
 */
class ResponseParser {
    /**
     * Parses JSON response from LLM into structured map
     */
    static Map parseJsonAnalysis(String jsonResponse, String pole1, String pole2) {
        try {
            def jsonSlurper = new JsonSlurper()
            def raw = jsonSlurper.parseText(extractJsonPayload(jsonResponse))

            // Validate root structure
            if (!raw.comparison?.dimension) {
                throw new Exception("Missing comparison dimension in response")
            }

            def results = [
                    dimension: [
                            pole1: raw.comparison.dimension.pole1,
                            pole2: raw.comparison.dimension.pole2
                    ],
                    concepts : [:]
            ]

            // Process concepts with pole validation
            ['concept_a', 'concept_b'].each { conceptKey ->
                def conceptData = raw.comparison.concepts[conceptKey]
                if (!conceptData) {
                    throw new Exception("Missing concept '$conceptKey' in response")
                }

                results.concepts[conceptKey] = [
                        (pole1): conceptData[pole1] ?: [],
                        (pole2): conceptData[pole2] ?: []
                ]
            }

            return results

        } catch (Exception e) {
            LogUtils.warn("JSON parsing failed: ${e.message}")
            return [error: "Invalid JSON structure: ${e.message}"]
        }
    }

    /**
     * Extracts JSON payload from markdown-formatted response
     */
    private static String extractJsonPayload(String rawResponse) {
        def matcher = rawResponse =~ /```json\n([\s\S]*?)\n```/
        matcher.find() ? matcher[0][1] : rawResponse
    }
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
        def results = [:]
        try {
            // Normalize text and split into sections
            def sections = analysisText.split(/(?m)^(?=\S)/) // Split on section starts

            sections.each { section ->
                def lines = section.readLines()*.trim()
                def currentPole = null

                lines.each { line ->
                    if (line.endsWith(':') && !line.matches(/^[-*+•·]\s*.*/)) {
                        currentPole = line.replaceAll(':', '').trim()
                        if (currentPole) {
                            LogUtils.info("ResponseParser: Found pole heading: '${currentPole}'")
                            results[currentPole] = results[currentPole] ?: []
                        }
                    } else if (currentPole && line.matches(/^[-*+•·]\s*.*/)) {
                        def point = line.replaceAll(/^[-*+•·]\s*/, '').trim()
                        if (point) {
                            LogUtils.info("ResponseParser: Adding point under '${currentPole}': '${point}'")
                            results[currentPole] << point
                        }
                    }
                }
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
