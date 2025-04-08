package com.barrymac.freeplane.addons.llm

import groovy.transform.Canonical

/**
 * Configuration for API calls
 */
@Canonical
class ApiConfig {
    String provider
    String apiKey
    String model
    int maxTokens
    double temperature

    List<String> availableModels

    Map<String, Object> toMap() {
        [
                provider       : provider,
                apiKey         : apiKey,
                model          : model,
                maxTokens      : maxTokens,
                temperature    : temperature,
                availableModels: availableModels
        ]
    }
}
