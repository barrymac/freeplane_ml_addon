package com.barrymac.freeplane.addons.llm.api


import com.barrymac.freeplane.addons.llm.prompts.Message

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
