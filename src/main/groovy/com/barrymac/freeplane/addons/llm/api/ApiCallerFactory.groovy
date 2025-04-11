package com.barrymac.freeplane.addons.llm.api

import com.barrymac.freeplane.addons.llm.exceptions.ApiException
import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.freeplane.core.util.LogUtils

import java.awt.*
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL

/**
 * Factory for creating API caller functions
 */
class ApiCallerFactory {
    /**
     * Enum for supported API providers
     */
    enum ApiProvider {
        OPENAI('https://api.openai.com/v1/chat/completions'),
        OPENROUTER('https://openrouter.ai/api/v1/chat/completions'),
        NOVITA('https://api.novita.ai/v3beta/flux-1-schnell')

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
                case 'novita': return NOVITA
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
        // We no longer need to treat the logger specially here

        def make_api_call = { String providerStr, String apiKey, Object requestObj ->
            // Convert request object to map if it's an ApiRequest
            def payloadMap = requestObj instanceof ApiRequest ?
                    requestObj.toMap() : requestObj as Map<String, Object>

            try {
                // Convert string provider to enum
                def provider = ApiProvider.fromString(providerStr)
                return handleApiCall(provider, apiKey, payloadMap, ui)
            } catch (ApiException e) {
                // Pass the exception message directly as it should contain necessary context
                ui.errorMessage(e.message)
                return ""
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
                                        Map<String, Object> payloadMap, def ui) {
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
        } else if (provider == ApiProvider.NOVITA) {
            // Novita uses Bearer token in Authorization header, already set above
            // Content-Type is already set to application/json
        }

        // --- LOGGING START 1 ---
        LogUtils.info("Attempting API call to ${provider.name()} at URL: ${apiUrl}")
        LogUtils.info("Using headers: ${headers}") // Log headers (API key is masked by 'Bearer ' prefix)
        // --- LOGGING END 1 ---

        HttpURLConnection post = null // Declare here to access timeout values in catch block
        try {
            // --- LOGGING START 2 ---
            LogUtils.info("Opening connection to ${apiUrl}...")
            // --- LOGGING END 2 ---
            post = new URL(apiUrl).openConnection() as HttpURLConnection
            post.setRequestMethod("POST")
            post.setDoOutput(true)
            // --- MODIFICATION: Add timeouts ---
            post.setConnectTimeout(15000) // 15 seconds connection timeout
            post.setReadTimeout(60000)    // 60 seconds read timeout
            // --- END MODIFICATION ---

            // Apply all headers
            headers.each { key, value -> post.setRequestProperty(key, value) }

            // Use JsonBuilder to create the payload string
            def payload = new JsonBuilder(payloadMap).toString()
            // --- LOGGING START 3 ---
            // Log payload carefully - avoid logging sensitive data if possible, or truncate
            def loggedPayload = payload.length() > 500 ? payload.take(500) + "..." : payload
            LogUtils.info("Sending payload: ${loggedPayload}")
            // --- LOGGING END 3 ---

            // --- LOGGING START 4 ---
            LogUtils.info("Writing payload to output stream...")
            // --- LOGGING END 4 ---
            post.getOutputStream().write(payload.getBytes("UTF-8"))
            // --- LOGGING START 5 ---
            LogUtils.info("Payload written. Waiting for response code...")
            // --- LOGGING END 5 ---

            def postRC = post.getResponseCode()
            LogUtils.info("API Call to ${provider.name()} (${apiUrl}) - Response Code: ${postRC}") // Existing log

            if (postRC == 200) {
                // --- LOGGING START 6 ---
                LogUtils.info("Reading response body (Code 200)...")
                // --- LOGGING END 6 ---
                responseText = post.getInputStream().getText("UTF-8")
                def truncatedResponse = responseText.length() > 200 ? responseText.take(200) + "..." : responseText // Adjusted truncation
                LogUtils.info("${provider.name()} response: ${truncatedResponse}") // Adjusted log
                // --- LOGGING START 7 ---
                LogUtils.info("Successfully read response body.")
                // --- LOGGING END 7 ---
            } else {
                // Handle common error codes centrally
                String errorMsg
                String browseUrl = null
                String errorBody = "" // Variable to store error body

                // --- LOGGING START 8 ---
                LogUtils.warn("Received error response code: ${postRC}. Reading error stream...")
                // --- LOGGING END 8 ---
                try {
                    def errorStream = post.getErrorStream()
                    if (errorStream) {
                        errorBody = errorStream.getText('UTF-8')
                        LogUtils.warn("Error response body: ${errorBody}")
                    } else {
                        LogUtils.warn("No error stream available for response code ${postRC}.")
                    }
                } catch (Exception ignored) {
                    LogUtils.warn("Exception reading error stream: ${ignored.message}")
                    // Ignore errors reading the error stream
                }
                // --- LOGGING START 9 ---
                LogUtils.info("Finished reading error stream (if any). Determining error message...")
                // --- LOGGING END 9 ---

                switch (postRC) {
                    case 401:
                        errorMsg = "Invalid authentication or incorrect API key provided for ${provider.name().toLowerCase()}."
                        browseUrl = (provider == ApiProvider.OPENROUTER) ?
                                "https://openrouter.ai/keys" :
                                (provider == ApiProvider.NOVITA) ?
                                "https://novita.ai/account" : "https://platform.openai.com/account/api-keys"
                        break
                    case 404:
                        errorMsg = (provider == ApiProvider.OPENROUTER) ?
                                "Endpoint not found. Check your OpenRouter configuration." :
                                (provider == ApiProvider.NOVITA) ?
                                "Endpoint not found. Check your Novita configuration." :
                                "You might need organization membership for OpenAI API."
                        break
                    case 429:
                        errorMsg = "Rate limit reached or quota exceeded for ${provider.name().toLowerCase()}."
                        break
                    case 400:
                        if (provider == ApiProvider.NOVITA) {
                            // Try to parse Novita error details from the already read errorBody
                            def parsedErrorBody = [:]
                            try {
                                if (errorBody) parsedErrorBody = new JsonSlurper().parseText(errorBody)
                            } catch(Exception ignored) {}

                            errorMsg = parsedErrorBody.message ?: "Bad request to Novita API"
                            if (parsedErrorBody.details) {
                                errorMsg += ": ${parsedErrorBody.details.join(', ')}"
                            }
                        } else {
                            errorMsg = "Bad request to ${provider.name().toLowerCase()} API."
                        }
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

                // Construct the ApiException message *here* to include the prefix
                // --- LOGGING START 10 ---
                LogUtils.info("Throwing ApiException: API Error (${postRC}): ${errorMsg}")
                // --- LOGGING END 10 ---
                throw new ApiException("API Error (${postRC}): ${errorMsg}", postRC)
            }

        } catch (SocketTimeoutException e) { // Catch specific timeout exception
            // Use post?.getReadTimeout() safely in case post is null (shouldn't happen here but good practice)
            def timeoutMillis = post?.getReadTimeout() ?: 60000 // Default to 60s if post is null
            LogUtils.severe("API call to ${provider.name()} timed out: ${e.message}", e)
            throw new ApiException("API call timed out after ${timeoutMillis / 1000} seconds.", 504, e) // Use 504 Gateway Timeout code
        } catch (ApiException e) {
            // Re-throw API exceptions (already logged if thrown above)
            throw e
        } catch (Exception e) {
            // --- LOGGING START 11 ---
            LogUtils.severe("Exception during API call to ${provider.name()}: ${e.class.simpleName} - ${e.message}", e) // Log exception type
            // --- LOGGING END 11 ---
            // ui.errorMessage("Network or processing error during API call: ${e.message}") // Removed redundant UI message
            // Wrap the original exception in LlmAddonException for consistent handling upstream
            throw new LlmAddonException("API call failed: ${e.message}", e)
        } finally {
            // Ensure the connection is closed if it was opened
            post?.disconnect()
            LogUtils.info("Disconnected HttpURLConnection (if connection was established).")
        }

        return responseText
    }
}
