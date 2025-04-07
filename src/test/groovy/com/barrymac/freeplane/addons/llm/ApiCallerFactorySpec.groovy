package com.barrymac.freeplane.addons.llm

import spock.lang.Specification
import spock.lang.Unroll

import java.awt.*

// Interface to properly mock the UI
interface UITest {
    void errorMessage(String message)
    void setDialogLocationRelativeTo(Object dialog, Object frame)
}

// Interface to properly mock the Logger
interface LoggerTest {
    void info(String message)
    void info(String format, Object... args)
    void warn(String message)
    void warn(String format, Object... args)
    void severe(String message)
    void severe(String format, Object... args)
}

class ApiCallerFactorySpec extends Specification {
    def mockLogger = Mock(LoggerTest)
    def mockUi = Mock(UITest)
    def apiCaller

    def setup() {
        // Mock Desktop browsing (prevent actual browser launches)
        Desktop.metaClass.static.browse = { URI uri -> /* no-op */ }

        apiCaller = ApiCallerFactory.createApiCaller(
                [logger: mockLogger, ui: mockUi]
        )
    }

    @Unroll
    def "make_api_call handles #scenario for #provider"() {
        given: "Mock HTTP connection"
        def mockConn = new MockHttpURLConnection(responseCode: statusCode, responseText: responseBody)
        URL.metaClass.openConnection = { mockConn }

        when: "Making API call"
        def result = apiCaller.make_api_call(provider, "fake-key", [
                model   : "test-model",
                messages: [[role: "user", content: "test"]]
        ])

        then: "Verify request construction"
        mockConn.requestMethod == "POST"
        mockConn.getRequestProperty("Content-Type") == "application/json"
        mockConn.getRequestProperty("Authorization") == "Bearer fake-key"

        if (provider == "openrouter") {
            assert mockConn.getRequestProperty("HTTP-Referer") == "https://github.com/barrymac/freeplane_openai_addon"
            assert mockConn.getRequestProperty("X-Title") == "Freeplane GPT AddOn"
        }

        and: "Verify response handling"
        result == expectedResponse
        1 * mockLogger.info("API Call to {} ({}) - Response Code: {}", provider, expectedUrl, statusCode) // This log always happens

        if (statusCode == 200) {
            1 * mockLogger.info("{} response: {}", provider, responseBody.take(200) + "...")
            0 * mockUi.errorMessage(_)
        } else {
            0 * mockLogger.info("{} response: {}", _, _) // Specifically check response log isn't called
            1 * mockUi.errorMessage(expectedError)
        }

        where:
        scenario               | provider     | statusCode | responseBody           | expectedResponse   | expectedUrl                                | expectedError
        "successful response"  | "openai"     | 200        | '{"choices":[{}]}'     | '{"choices":[{}]}' | "https://api.openai.com/v1/chat/completions" | null
        "auth error"           | "openrouter" | 401        | '{"error": "invalid"}' | ""                 | "https://openrouter.ai/api/v1/chat/completions" | "Invalid authentication or incorrect API key provided for openrouter."
        "rate limit"           | "openai"     | 429        | '{"error": "busy"}'    | ""                 | "https://api.openai.com/v1/chat/completions" | "Rate limit reached or quota exceeded for openai."
    }

    def "make_api_call handles network errors"() {
        given: "Simulate network failure"
        URL.metaClass.openConnection = { throw new IOException("Connection timeout") }

        when: "Making API call"
        def result = apiCaller.make_api_call("openai", "fake-key", [
                model   : "test-model",
                messages: [[role: "user", content: "test"]]
        ])

        then: "Verify error handling"
        result == ""
        1 * mockLogger.warn("Exception during API call to {}: {}", "openai", "Connection timeout")
        1 * mockUi.errorMessage("Network or processing error during API call: Connection timeout")
    }

    def "make_api_call handles unsupported provider"() {
        when: "Using invalid provider"
        apiCaller.make_api_call("invalid", "key", [:])

        then: "Show error message"
        1 * mockUi.errorMessage("LLM AddOn Error: Unsupported API provider: invalid")
    }
}

// Groovy mock HTTP connection implementation
class MockHttpURLConnection extends HttpURLConnection {
    int responseCode = 200
    String responseText = ""
    String requestMethod
    Map<String, String> requestProperties = [:]
    String outputStreamContent = ""

    MockHttpURLConnection() {
        super(new URL("http://example.com"))
    }

    void setRequestMethod(String method) {
        requestMethod = method
    }

    void setRequestProperty(String key, String value) {
        requestProperties[key] = value
    }

    String getRequestProperty(String key) {
        return requestProperties[key]
    }

    int getResponseCode() { responseCode }

    InputStream getInputStream() {
        new ByteArrayInputStream(responseText.getBytes("UTF-8"))
    }

    InputStream getErrorStream() {
        new ByteArrayInputStream(responseText.getBytes("UTF-8"))
    }

    OutputStream getOutputStream() {
        new ByteArrayOutputStream() {
            void write(int b) {
                outputStreamContent += (char) b
            }

            void write(byte[] b, int off, int len) {
                outputStreamContent += new String(b, off, len, "UTF-8")
            }
        }
    }

    void connect() {}

    void disconnect() {}

    boolean usingProxy() { false }
}
