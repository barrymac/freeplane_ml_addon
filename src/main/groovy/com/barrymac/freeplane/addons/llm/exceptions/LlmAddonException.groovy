package com.barrymac.freeplane.addons.llm.exceptions

/**
 * Base exception class for LLM add-on
 */
class LlmAddonException extends Exception {
    LlmAddonException(String message) {
        super("LLM AddOn Error: $message")
    }

    LlmAddonException(String message, Throwable cause) {
        super("LLM AddOn Error: $message", cause)
    }
}
