package com.barrymac.freeplane.addons.llm

import groovy.transform.CompileStatic

/**
 * Helper class to centralize configuration loading
 */
@CompileStatic
class ConfigManager {
    // Define default models directly in code
    static final List<String> DEFAULT_MODELS = [
            'meta-llama/llama-3.2-1b-instruct',
            'deepseek/deepseek-r1-zero:free',
            'deepseek/deepseek-r1',
            'anthropic/claude-3-opus',
            'anthropic/claude-3-sonnet',
            'anthropic/claude-3-haiku',
            'openai/o3-mini',
            'openai/o3-mini-high',
            'google/gemini-2.5-pro-exp-03-25:free',
            'google/gemini-pro',
            'meta-llama/llama-3-70b-instruct',
            'gpt-3.5-turbo',
            'gpt-4'
    ]

    /**
     * Loads base configuration from Freeplane config
     *
     * @param config The Freeplane config object
     * @return ApiConfig object with loaded configuration
     */
    static ApiConfig loadBaseConfig(def config) {
        return new ApiConfig(
                provider: config.getProperty('openai.api_provider', 'openrouter'),
                apiKey: config.getProperty('openai.key', ''),
                model: config.getProperty('openai.gpt_model', 'gpt-4'),
                maxTokens: config.getProperty('openai.max_response_length', 2000) as int,
                temperature: config.getProperty('openai.temperature', 0.7) as double,
                availableModels: config.getProperty('openai.available_models')?.split('\n') ?: DEFAULT_MODELS
        )
    }

    /**
     * Gets the add-ons directory path
     *
     * @param config The Freeplane config object
     * @return String path to the add-ons directory
     */
    static String getAddonsDir(def config) {
        return "${config.freeplaneUserDirectory}/addons/promptLlmAddOn"
    }
    
    /**
     * Gets the system message index from config
     * 
     * @param config The Freeplane config object
     * @return The system message index
     */
    static int getSystemMessageIndex(def config) {
        return config.getProperty('openai.system_message_index', 0) as int
    }
    
    /**
     * Gets the user message index from config
     * 
     * @param config The Freeplane config object
     * @return The user message index
     */
    static int getUserMessageIndex(def config) {
        return config.getProperty('openai.user_message_index', 0) as int
    }
}
