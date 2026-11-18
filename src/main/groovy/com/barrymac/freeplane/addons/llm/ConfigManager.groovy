package com.barrymac.freeplane.addons.llm

import org.freeplane.core.util.LogUtils

/**
 * Helper class to centralize configuration loading
 */
//@CompileStatic
class ConfigManager {
    // Default model list, refreshed 2026-11-17 against the live OpenRouter
    // catalogue. Deliberately excludes OpenAI and Anthropic: both are far more
    // expensive than the open-weight houses, and a user who wants them can add
    // them to 'openai.available_models' themselves.
    static final List<String> DEFAULT_MODELS = [
            // newest fast + flagship, Nov 2026
            'tencent/hy4-flash',
            'qwen/qwen4-max',
            'google/gemini-4-hydrogen',
            // domain-tuned: legal vocabulary and long-contract reasoning
            'z-ai/glm-5.3-flashx-legal',
            // long-context and reasoning houses
            'moonshotai/kimi-k3.1-pro',
            'deepseek/deepseek-v4.1-flash-1017',
            'minimax/minimax-m3.1-flash',
            'x-ai/grok-4.7',
            'mistralai/mistral-large-4-0',
            // vision-capable, for map screenshots and diagrams
            'nvidia/nemotron-3.5-super-vl'
    ]

    /** First entry of DEFAULT_MODELS — the out-of-the-box choice. */
    static final String DEFAULT_MODEL = DEFAULT_MODELS[0]

    /**
     * Loads base configuration from Freeplane config
     *
     * @param config The Freeplane config object
     * @return ApiConfig object with loaded configuration
     */
    static ApiConfig loadBaseConfig(def config) {
        try {
            def provider = config.getProperty('openai.api_provider', 'openrouter')
            def model = config.getProperty('openai.gpt_model', DEFAULT_MODEL)
            def maxTokens = config.getProperty('openai.max_response_length', 2000) as int
            def temperature = config.getProperty('openai.temperature', 0.7) as double

            // Get available models from config or use defaults
            def availableModels = config.getProperty('openai.available_models')?.split('\n') ?: DEFAULT_MODELS

            LogUtils.info("Loaded configuration: provider=${provider}, model=${model}, maxTokens=${maxTokens}, temperature=${temperature}")
            LogUtils.info("Available models: ${availableModels}")

            return new ApiConfig(
                    provider: provider,
                    apiKey: config.getProperty('openai.key', '') ?: '',
                    model: model,
                    maxTokens: maxTokens,
                    temperature: temperature,
                    availableModels: availableModels
            )
        } catch (Exception e) {
            LogUtils.severe("Error loading base configuration: ${e.message}")
            // Return default config on error
            return new ApiConfig(
                    provider: 'openrouter',
                    apiKey: '',
                    model: DEFAULT_MODEL,
                    maxTokens: 2000,
                    temperature: 0.7,
                    availableModels: DEFAULT_MODELS
            )
        }
    }

    /**
     * Gets the add-ons directory path
     *
     * @param config The Freeplane config object
     * @return String path to the add-ons directory
     */
    static String getAddonsDir(def config) {
        def dir = "${config.freeplaneUserDirectory}/addons/promptLlmAddOn"
        LogUtils.info("Add-ons directory: ${dir}")
        return dir
    }

    /**
     * Gets the system message index from config
     *
     * @param config The Freeplane config object
     * @return The system message index
     */
    static int getSystemMessageIndex(def config) {
        try {
            def index = config.getProperty('openai.system_message_index', 0) as int
            LogUtils.info("System message index: ${index}")
            return index
        } catch (Exception e) {
            LogUtils.warn("Error getting system message index, using default: ${e.message}")
            return 0
        }
    }

    /**
     * Gets the user message index from config
     *
     * @param config The Freeplane config object
     * @return The user message index
     */
    static int getUserMessageIndex(def config) {
        try {
            def index = config.getProperty('openai.user_message_index', 0) as int
            LogUtils.info("User message index: ${index}")
            return index
        } catch (Exception e) {
            LogUtils.warn("Error getting user message index, using default: ${e.message}")
            return 0
        }
    }
    
    /**
     * Saves a user-specific property for the LLM add-on
     * @param config The Freeplane config object
     * @param key Property key (will be prefixed with 'llm.addon.')
     * @param value Property value to store
     */
    static void setUserProperty(def config, String key, String value) {
        try {
            config.setProperty("llm.addon.${key}", value)
            // Freeplane config auto-saves, no need to call save()
            LogUtils.info("Saved user property '${key}'")
        } catch (Exception e) {
            LogUtils.severe("Failed to save user property '${key}': ${e.message}")
            throw e
        }
    }

    /**
     * Retrieves a user-specific property for the LLM add-on
     * @param config The Freeplane config object
     * @param key Property key (will be prefixed with 'llm.addon.')
     * @param defaultValue Value to return if property not found
     * @return The stored value or defaultValue
     */
    static String getUserProperty(def config, String key, String defaultValue = '') {
        try {
            // Defensive: always pass a String as default
            return config.getProperty("llm.addon.${key}", defaultValue ?: '')
        } catch (Exception e) {
            LogUtils.warn("Error reading user property '${key}': ${e.message}")
            return defaultValue
        }
    }
    
    /**
     * Deletes a user-specific property
     * @param config The Freeplane config object
     * @param key Property key (will be prefixed with 'llm.addon.')
     */
    static void deleteUserProperty(def config, String key) {
        try {
            // Use setProperty with null value to effectively remove it
            String fullKey = 'llm.addon.' + key
            config.setProperty(fullKey, null) // This clears the property
            LogUtils.info("Deleted user property '${key}'")
        } catch (Exception e) {
            LogUtils.severe("Failed to delete property '${key}': ${e.message}")
            throw e
        }
    }
}
