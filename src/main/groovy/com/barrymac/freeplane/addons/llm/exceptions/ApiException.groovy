package com.barrymac.freeplane.addons.llm.exceptions

/**
 * Exception for API-related errors
 */
class ApiException extends LlmAddonException {
    final int statusCode

    ApiException(String message, int statusCode) {
        super("API Error ($statusCode): $message")
        this.statusCode = statusCode
    }

    ApiException(String message, int statusCode, Throwable cause) {
        super("API Error ($statusCode): $message", cause)
        this.statusCode = statusCode
    }
}
