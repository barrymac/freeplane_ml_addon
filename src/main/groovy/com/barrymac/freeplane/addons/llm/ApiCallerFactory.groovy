package com.barrymac.freeplane.addons.llm

import groovy.json.JsonBuilder

import java.awt.*

class ApiCallerFactory {
    static def createApiCaller(Map closures) {
        def logger = closures.logger
        def ui = closures.ui

        def make_api_call = { String provider, String apiKey, Map<String, Object> payloadMap ->
            def responseText = ""
            String apiUrl
            Map<String, String> headers = [:]

            if (provider == 'openai') {
                apiUrl = 'https://api.openai.com/v1/chat/completions'
                headers["Authorization"] = "Bearer " + apiKey
            } else if (provider == 'openrouter') {
                apiUrl = 'https://openrouter.ai/api/v1/chat/completions'
                headers["Authorization"] = "Bearer " + apiKey
                // Add OpenRouter specific headers
                headers["HTTP-Referer"] = "https://github.com/barrymac/freeplane_openai_addon"
                headers["X-Title"] = "Freeplane GPT AddOn"
            } else {
                ui.errorMessage("Unsupported API provider: ${provider}".toString())
                return "" // Or throw an exception
            }

            try {
                def post = new URL(apiUrl).openConnection()
                post.setRequestMethod("POST")
                post.setDoOutput(true)
                post.setRequestProperty("Content-Type", "application/json")
                // Apply all headers
                headers.each { key, value -> post.setRequestProperty(key, value) }

                // Use JsonBuilder to create the payload string
                def payload = new JsonBuilder(payloadMap).toString()
                post.getOutputStream().write(payload.getBytes("UTF-8"))

                def postRC = post.getResponseCode()
                logger.info("API Call to ${provider} (${apiUrl}) - Response Code: ${postRC}")

                if (postRC.equals(200)) {
                    responseText = post.getInputStream().getText("UTF-8")
                    logger.info("${provider} response: ${responseText.take(200)}...") // Log truncated response
                } else {
                    // Handle common error codes centrally
                    String errorMsg
                    String browseUrl = null
                    switch (postRC) {
                        case 401:
                            errorMsg = "Invalid authentication or incorrect API key provided for ${provider}.".toString()
                            browseUrl = (provider == 'openrouter') ? "https://openrouter.ai/keys" : "https://platform.openai.com/account/api-keys"
                            break
                        case 404:
                            errorMsg = (provider == 'openrouter') ? "Endpoint not found. Check your OpenRouter configuration.".toString() : "You might need organization membership for OpenAI API.".toString()
                            break
                        case 429:
                            errorMsg = "Rate limit reached or quota exceeded for ${provider}.".toString()
                            break
                        default:
                            errorMsg = "Unhandled error code ${postRC} returned from ${provider} API.".toString()
                    }
                    if (browseUrl) {
                        try {
                            Desktop.desktop.browse(new URI(browseUrl))
                        } catch (Exception browseEx) {
                            logger.warn("Failed to open browser for URL: ${browseUrl}", browseEx as Throwable)
                        }
                    }
                    ui.errorMessage(errorMsg)
                    // Optionally log the full error response body if available
                    try {
                        def errorStream = post.getErrorStream()
                        if (errorStream) {
                            logger.warn("Error response body: ${errorStream.getText('UTF-8')}")
                        }
                    } catch (Exception ignored) {
                    }
                }

            } catch (Exception e) {
                logger.warn("Exception during API call to ${provider}".toString(), e as Throwable)
                ui.errorMessage("Network or processing error during API call: ${e.message}".toString())
            }

            return responseText
        }

        // For backward compatibility
        def make_openai_call = { String apiKey, Map<String, Object> payloadMap ->
            return make_api_call('openai', apiKey, payloadMap)
        }

        def make_openrouter_call = { String apiKey, Map<String, Object> payloadMap ->
            return make_api_call('openrouter', apiKey, payloadMap)
        }

        return [
                make_api_call       : make_api_call,
                make_openai_call    : make_openai_call,
                make_openrouter_call: make_openrouter_call
        ]
    }
}
