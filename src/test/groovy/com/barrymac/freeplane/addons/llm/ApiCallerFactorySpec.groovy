package com.barrymac.freeplane.addons.llm

import org.slf4j.Logger
import spock.lang.Specification
import spock.lang.Unroll
import javax.swing.*
import java.awt.Desktop

class ApiCallerFactorySpec extends Specification {
    def mockLogger = Mock(Logger)
    def mockUi = Mock(Object)
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
            model: "test-model",
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
        1 * mockLogger.info("API Call to ${provider} (${expectedUrl}) - Response Code: ${statusCode}".toString())

        where:
        scenario                 | provider     | statusCode | responseBody          | expectedResponse | expectedUrl
        "successful response"    | "openai"     | 200        | '{"choices":[{}]}'    | '{"choices":[{}]}' | "https://api.openai.com/v1/chat/completions"
        "authentication error"   | "openrouter" | 401        | '{"error": "invalid"}' | ""               | "https://openrouter.ai/api/v1/chat/completions"
        "rate limit exceeded"   | "openai"     | 429        | '{"error": "busy"}'    | ""               | "https://api.openai.com/v1/chat/completions"
    }

    def "make_api_call handles network errors"() {
        given: "Simulate network failure"
        URL.metaClass.openConnection = { throw new IOException("Connection timeout") }

        when: "Making API call"
        def result = apiCaller.make_api_call("openai", "fake-key", [
            model: "test-model",
            messages: [[role: "user", content: "test"]]
        ])

        then: "Verify error handling"
        result == ""
        1 * mockLogger.warn("Exception during API call to openai".toString(), _ as Throwable)
        1 * mockUi.errorMessage("Network or processing error during API call: Connection timeout")
    }

    def "make_api_call handles unsupported provider"() {
        when: "Using invalid provider"
        apiCaller.make_api_call("invalid", "key", [:])

        then: "Show error message"
        1 * mockUi.errorMessage("Unsupported API provider: invalid")
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
