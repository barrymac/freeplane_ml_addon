package com.barrymac.freeplane.addons.llm.api

import com.barrymac.freeplane.addons.llm.prompts.Message
import groovy.transform.Canonical

/**
 * Represents a request to an LLM API
 */
@Canonical
class ApiRequest {
    String model
    List<Message> messages
    double temperature
    int maxTokens

    // Convert to a Map for JSON serialization
    Map<String, Object> toMap() {
        [
                'model'      : model,
                'messages'   : messages.collect { it.toMap() },
                'temperature': temperature,
                'max_tokens' : maxTokens
        ]
    }
}
