package com.barrymac.freeplane.addons.llm

import org.freeplane.core.util.LogUtils

/**
 * Helper class to centralize configuration loading
 */
//@CompileStatic
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
        try {
            def provider = config.getProperty('openai.api_provider', 'openrouter')
            def model = config.getProperty('openai.gpt_model', 'gpt-4')
            def maxTokens = config.getProperty('openai.max_response_length', 2000) as int
            def temperature = config.getProperty('openai.temperature', 0.7) as double

            // Get available models from config or use defaults
            def availableModels = config.getProperty('openai.available_models')?.split('\n') ?: DEFAULT_MODELS

            LogUtils.info("Loaded configuration: provider=${provider}, model=${model}, maxTokens=${maxTokens}, temperature=${temperature}")
            LogUtils.info("Available models: ${availableModels}")

            return new ApiConfig(
                    provider: provider,
                    apiKey: config.getProperty('openai.key', ''),
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
                    model: 'gpt-4',
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
            return config.getProperty("llm.addon.${key}", defaultValue)
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
            config.remove("llm.addon.${key}")
            LogUtils.info("Deleted user property '${key}'")
        } catch (Exception e) {
            LogUtils.severe("Failed to delete property '${key}': ${e.message}")
            throw e
        }
    }
}
