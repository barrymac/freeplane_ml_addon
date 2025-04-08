package com.barrymac.freeplane.addons.llm.mock

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
