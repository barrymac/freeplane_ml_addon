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
            if (!raw.comparison?.concepts || raw.comparison.concepts.isEmpty()) {
                throw new Exception("Missing or empty 'concepts' object in response")
            }

            def conceptsData = raw.comparison.concepts
            def conceptKeys = conceptsData.keySet()

            if (conceptKeys.size() != 2) {
                throw new Exception("Expected exactly 2 concepts in response, found ${conceptKeys.size()}. Keys: ${conceptKeys}")
            }

            // Extract the two concept keys found in the JSON
            def foundKey1 = conceptKeys.toList()[0]
            def foundKey2 = conceptKeys.toList()[1]

            def results = [
                    dimension: [
                            pole1: pole1,
                            pole2: pole2
                    ],
                    concepts : [:] // Initialize empty concepts map
            ]

            // Process concepts using the keys found in the JSON
            [foundKey1, foundKey2].each { conceptKey ->
                def conceptData = conceptsData[conceptKey]
                if (!conceptData) {
                    throw new Exception("Missing data for concept key '$conceptKey' in response")
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
    /**
     * Parses Novita API response to extract image URLs
     * 
     * @param jsonResponse The raw JSON response from Novita API
     * @return List of image URLs
     * @throws Exception if parsing fails or no images found
     */
    static List<String> parseNovitaResponse(String jsonResponse) {
        try {
            if (!jsonResponse?.trim()) {
                throw new Exception("Empty response from Novita API")
            }
            
            def jsonSlurper = new JsonSlurper()
            def parsed = jsonSlurper.parseText(jsonResponse)
            
            if (!parsed.images) {
                throw new Exception("No images found in Novita API response")
            }
            
            def imageUrls = parsed.images.collect { it.image_url }
            if (imageUrls.isEmpty()) {
                throw new Exception("Empty image URLs list in Novita API response")
            }
            
            LogUtils.info("Parsed ${imageUrls.size()} image URLs from Novita response")
            return imageUrls
            
        } catch (Exception e) {
            LogUtils.severe("Failed to parse Novita response: ${e.message}")
            throw new Exception("Failed to parse Novita API response: ${e.message}", e)
        }
    }
    
    /**
     * Parses LLM response to extract generated prompt text
     * 
     * @param llmResponse The raw JSON response from OpenAI API
     * @return The generated prompt text or null if parsing fails
     */
    static String extractGeneratedPrompt(String llmResponse) {
        try {
            def json = new JsonSlurper().parseText(llmResponse)
            return json.choices[0].message.content.trim()
        } catch (Exception e) {
            LogUtils.warn("Failed to extract generated prompt: ${e.message}")
            return null
        }
    }
    
    /**
     * Extracts JSON payload from a potentially noisy response.
     * Handles markdown code fences (```json ... ```) and attempts to find
     * the first '{' if no code fence is present, ignoring leading text.
     */
    private static String extractJsonPayload(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return "" // Return empty if input is null or empty
        }
        // First, try to find markdown code fence
        def matcher = rawResponse =~ /(?s)```json\s*(\{.*?\})\s*```/
        if (matcher.find()) {
            LogUtils.info("ResponseParser: Extracted JSON from markdown code fence.")
            return matcher[0][1] // Return content within the braces
        }

        // If no code fence, find the first opening brace '{'
        int firstBrace = rawResponse.indexOf('{')
        if (firstBrace != -1) {
            // Find the matching closing brace '}' - simple heuristic, might fail on nested structures if there's trailing text
            // A more robust approach might involve counting braces, but let's start simple.
            int lastBrace = rawResponse.lastIndexOf('}')
            if (lastBrace > firstBrace) {
                LogUtils.info("ResponseParser: Extracted JSON by finding first '{' and last '}'. Ignored leading text.")
                return rawResponse.substring(firstBrace, lastBrace + 1)
            }
        }

        // If neither worked, return the original response (parser will likely fail, triggering retry)
        LogUtils.warn("ResponseParser: Could not reliably extract JSON payload. Returning raw response.")
        return rawResponse
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
