package com.barrymac.freeplane.addons.llm.exceptions

/**
 * Exception for API-related errors
 */
class ApiException extends LlmAddonException {
    final int statusCode

    ApiException(String message, int statusCode) {
        // Pass only the message to the super constructor
        super(message)
        this.statusCode = statusCode
    }

    ApiException(String message, int statusCode, Throwable cause) {
        // Pass only the message and cause to the super constructor
        super(message, cause)
        this.statusCode = statusCode
    }
}
