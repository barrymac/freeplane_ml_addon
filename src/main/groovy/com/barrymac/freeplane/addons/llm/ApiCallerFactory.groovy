package com.barrymac.freeplane.addons.llm

import com.barrymac.freeplane.addons.llm.exceptions.ApiException
import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException
import groovy.json.JsonBuilder
import org.freeplane.core.util.LogUtils

import java.awt.*

/**
 * Factory for creating API caller functions
 */
//@CompileStatic
class ApiCallerFactory {
    /**
     * Enum for supported API providers
     */
    enum ApiProvider {
        OPENAI('https://api.openai.com/v1/chat/completions'),
        OPENROUTER('https://openrouter.ai/api/v1/chat/completions')

        final String endpoint

        ApiProvider(String endpoint) {
            this.endpoint = endpoint
        }

        /**
         * Get provider from string
         */
        static ApiProvider fromString(String provider) {
            switch (provider.toLowerCase()) {
                case 'openai': return OPENAI
                case 'openrouter': return OPENROUTER
                default: throw new LlmAddonException("Unsupported API provider: $provider")
            }
        }
    }

    /**
     * Creates an API caller with the necessary functions
     *
     * @param closures Map containing ui
     * @return Map of API caller functions
     */
    static Map createApiCaller(Map closures) {
        def ui = closures.ui
        def logger = closures.logger // Use provided logger if available

        def make_api_call = { String providerStr, String apiKey, Object requestObj ->
            // Convert request object to map if it's an ApiRequest
            def payloadMap = requestObj instanceof ApiRequest ?
                    requestObj.toMap() : requestObj as Map<String, Object>

            try {
                // Convert string provider to enum
                def provider = ApiProvider.fromString(providerStr)
                return handleApiCall(provider, apiKey, payloadMap, ui, logger)
            } catch (LlmAddonException e) {
                ui.errorMessage(e.message)
                return ""
            }
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

    /**
     * Handles the API call to the specified provider
     *
     * @param provider The API provider
     * @param apiKey The API key
     * @param payloadMap The request payload
     * @param ui The UI
     * @return The response text
     */
    private static String handleApiCall(ApiProvider provider, String apiKey,
                                        Map<String, Object> payloadMap, def ui, def logger) {
        def responseText = ""
        String apiUrl = provider.endpoint
        Map<String, String> headers = [
                "Content-Type" : "application/json",
                "Authorization": "Bearer $apiKey"
        ]

        // Add provider-specific headers
        if (provider == ApiProvider.OPENROUTER) {
            headers["HTTP-Referer"] = "https://github.com/barrymac/freeplane_openai_addon"
            headers["X-Title"] = "Freeplane GPT AddOn"
        }

        try {
            def post = new URL(apiUrl).openConnection() as HttpURLConnection
            post.setRequestMethod("POST")
            post.setDoOutput(true)

            // Apply all headers
            headers.each { key, value -> post.setRequestProperty(key, value) }

            // Use JsonBuilder to create the payload string
            def payload = new JsonBuilder(payloadMap).toString()
            post.getOutputStream().write(payload.getBytes("UTF-8"))

            def postRC = post.getResponseCode()
            if (logger) {
                logger.info("API Call to {} ({}) - Response Code: {}", provider.name().toLowerCase(), apiUrl, postRC)
            } else {
                LogUtils.info("API Call to {} ({}) - Response Code: {}", [provider.name().toLowerCase(), apiUrl, postRC] as Object[])
            }

            if (postRC == 200) {
                responseText = post.getInputStream().getText("UTF-8")
                if (logger) {
                    logger.info("{} response: {}", provider.name().toLowerCase(), responseText.take(200) + "...")
                } else {
                    LogUtils.info("{} response: {}", [provider.name().toLowerCase(), responseText.take(200) + "..."] as Object[])
                }
                // Log truncated response
            } else {
                // Handle common error codes centrally
                String errorMsg
                String browseUrl = null

                switch (postRC) {
                    case 401:
                        errorMsg = "Invalid authentication or incorrect API key provided for ${provider.name().toLowerCase()}."
                        browseUrl = (provider == ApiProvider.OPENROUTER) ?
                                "https://openrouter.ai/keys" : "https://platform.openai.com/account/api-keys"
                        break
                    case 404:
                        errorMsg = (provider == ApiProvider.OPENROUTER) ?
                                "Endpoint not found. Check your OpenRouter configuration." :
                                "You might need organization membership for OpenAI API."
                        break
                    case 429:
                        errorMsg = "Rate limit reached or quota exceeded for ${provider.name().toLowerCase()}."
                        break
                    default:
                        errorMsg = "Unhandled error code $postRC returned from ${provider.name().toLowerCase()} API."
                }

                if (browseUrl) {
                    try {
                        Desktop.desktop.browse(new URI(browseUrl))
                    } catch (Exception browseEx) {
                        LogUtils.warn("Failed to open browser for URL: ${browseUrl}: ${browseEx.message}")
                    }
                }

                ui.errorMessage("LLM AddOn Error: API Error (${postRC}): ${errorMsg}")

                // Log the error response body if available
                try {
                    def errorStream = post.getErrorStream()
                    if (errorStream) {
                        LogUtils.warn("Error response body: ${errorStream.getText('UTF-8')}")
                    }
                } catch (Exception ignored) {
                    // Ignore errors reading the error stream
                }

                throw new ApiException(errorMsg, postRC)
            }

        } catch (ApiException e) {
            // Re-throw API exceptions
            throw e
        } catch (Exception e) {
            if (logger) {
                logger.warn("Exception during API call to {}: {}", provider.name().toLowerCase(), e.message)
            } else {
                LogUtils.warn("Exception during API call to {}: {}", provider.name().toLowerCase(), e.message)
            }
            ui.errorMessage("Network or processing error during API call: ${e.message}")
            throw new LlmAddonException("API call failed: ${e.message}", e)
        }

        return responseText
    }
}
