package com.barrymac.freeplane.addons.llm.prompts

import groovy.transform.Canonical

/**
 * Represents a message in an LLM conversation
 */
@Canonical
class Message {
    String role
    String content

    // Convert to a Map for JSON serialization
    Map<String, String> toMap() {
        [role: role, content: content]
    }
}
