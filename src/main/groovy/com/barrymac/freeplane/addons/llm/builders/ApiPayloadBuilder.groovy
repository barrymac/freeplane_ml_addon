package com.barrymac.freeplane.addons.llm.builders

import com.barrymac.freeplane.addons.llm.ApiRequest
import com.barrymac.freeplane.addons.llm.Message

class ApiPayloadBuilder {
    static ApiRequest buildPayload(String model, String systemMessage, String userPrompt, Map apiConfig) {
        new ApiRequest(
            model: model,
            messages: [
                new Message(role: 'system', content: systemMessage),
                new Message(role: 'user', content: userPrompt)
            ],
            temperature: apiConfig.temperature,
            maxTokens: apiConfig.maxTokens
        )
    }
}
