package com.barrymac.freeplane.addons.llm

import org.freeplane.core.util.LogUtils
import spock.lang.Specification
import spock.lang.Unroll

// Interface to mock Freeplane's config object

class ConfigManagerSpec extends Specification {

    def mockConfig = Mock(ConfigTest)

    // Mock LogUtils statically before each test
    def setup() {
        GroovyMock(LogUtils, global: true)
    }

    def "loadBaseConfig loads defaults when properties are missing"() {
        given: "Config returns null for properties"
        mockConfig.getProperty('openai.api_provider', 'openrouter') >> 'openrouter'
        mockConfig.getProperty('openai.gpt_model', 'gpt-4') >> 'gpt-4'
        mockConfig.getProperty('openai.max_response_length', 2000) >> 2000
        mockConfig.getProperty('openai.temperature', 0.7) >> 0.7
        mockConfig.getProperty('openai.available_models') >> null // Simulate property not set

        when: "Loading base config"
        def result = ConfigManager.loadBaseConfig(mockConfig)

        then: "Default ApiConfig is returned"
        result.provider == 'openrouter'
        result.apiKey == '' // Default API key is empty string
        result.model == 'gpt-4'
        result.maxTokens == 2000
        result.temperature == 0.7
        result.availableModels == ConfigManager.DEFAULT_MODELS // Should use static default list
        1 * LogUtils.info("Loaded configuration: provider=openrouter, model=gpt-4, maxTokens=2000, temperature=0.7")
        1 * LogUtils.info("Available models: ${ConfigManager.DEFAULT_MODELS}")
    }

    def "loadBaseConfig loads values from config"() {
        given: "Config provides specific values"
        mockConfig.getProperty('openai.api_provider', 'openrouter') >> 'openai'
        mockConfig.getProperty('openai.gpt_model', 'gpt-4') >> 'gpt-3.5-turbo'
        mockConfig.getProperty('openai.max_response_length', 2000) >> 1500
        mockConfig.getProperty('openai.temperature', 0.7) >> 0.5
        mockConfig.getProperty('openai.key', '') >> 'test-key'
        mockConfig.getProperty('openai.available_models') >> "model1\nmodel2" // Custom models

        when: "Loading base config"
        def result = ConfigManager.loadBaseConfig(mockConfig)

        then: "ApiConfig reflects the provided values"
        result.provider == 'openai'
        result.apiKey == 'test-key'
        result.model == 'gpt-3.5-turbo'
        result.maxTokens == 1500
        result.temperature == 0.5
        result.availableModels == ['model1', 'model2'] // Should parse the string
        1 * LogUtils.info("Loaded configuration: provider=openai, model=gpt-3.5-turbo, maxTokens=1500, temperature=0.5")
        1 * LogUtils.info("Available models: ${['model1', 'model2']}")
    }

    def "loadBaseConfig handles exceptions during loading"() {
        given: "Config throws an exception for a numeric property"
        mockConfig.getProperty('openai.api_provider', 'openrouter') >> 'openrouter'
        mockConfig.getProperty('openai.gpt_model', 'gpt-4') >> 'gpt-4'
        mockConfig.getProperty('openai.max_response_length', 2000) >> { throw new NumberFormatException("bad number") } // Simulate error
        // Other properties might be called or not depending on execution path

        when: "Loading base config"
        def result = ConfigManager.loadBaseConfig(mockConfig)

        then: "Default ApiConfig is returned and error is logged"
        result.provider == 'openrouter'
        result.apiKey == ''
        result.model == 'gpt-4'
        result.maxTokens == 2000 // Default value
        result.temperature == 0.7 // Default value
        result.availableModels == ConfigManager.DEFAULT_MODELS
        1 * LogUtils.severe("Error loading base configuration: bad number")
        // Verify defaults are logged if loading succeeds partially before error (depends on implementation order)
        // or verify no success log if error happens early. Here, we assume error log takes precedence.
        0 * LogUtils.info("Loaded configuration: provider=*, model=*, maxTokens=*, temperature=*") // No success log
    }

    def "getAddonsDir returns correct path"() {
        given: "Config provides user directory"
        mockConfig.getFreeplaneUserDirectory() >> "/home/user/.config/freeplane/1.11.x"

        when: "Getting addons directory"
        def result = ConfigManager.getAddonsDir(mockConfig)

        then: "Path is constructed correctly"
        result == "/home/user/.config/freeplane/1.11.x/addons/promptLlmAddOn"
        1 * LogUtils.info("Add-ons directory: ${result}")
    }

    @Unroll
    def "getIndex returns #expectedIndex when #description"() {
        given: "Config setup for the scenario"
        mockConfig.getProperty(configKey, 0) >> propertyValue

        when: "Getting index using #methodName"
        def result = ConfigManager."$methodName"(mockConfig) // Dynamic method call

        then: "Correct index is returned and logged"
        result == expectedIndex
        1 * LogUtils.info("${logPrefix} index: ${expectedIndex}")
        0 * LogUtils.warn(_) // No warnings expected

        where:
        methodName              | configKey                      | description              | propertyValue | expectedIndex | logPrefix
        "getSystemMessageIndex" | 'openai.system_message_index'  | "property is set"        | 5             | 5             | "System message"
        "getSystemMessageIndex" | 'openai.system_message_index'  | "property is missing"    | 0             | 0             | "System message" // Default value passed to getProperty
        "getUserMessageIndex"   | 'openai.user_message_index'    | "property is set"        | 3             | 3             | "User message"
        "getUserMessageIndex"   | 'openai.user_message_index'    | "property is missing"    | 0             | 0             | "User message"   // Default value passed to getProperty
    }

    @Unroll
    def "getIndex handles exception and returns default 0 for #methodName"() {
        given: "Config throws exception when getting property"
        mockConfig.getProperty(configKey, 0) >> { throw new NumberFormatException("bad index") }

        when: "Getting index using #methodName"
        def result = ConfigManager."$methodName"(mockConfig)

        then: "Default index 0 is returned and warning logged"
        result == 0
        1 * LogUtils.warn("Error getting ${logPrefix.toLowerCase()} index, using default: bad index")
        0 * LogUtils.info(_) // No info log expected

        where:
        methodName              | configKey                     | logPrefix
        "getSystemMessageIndex" | 'openai.system_message_index' | "system message"
        "getUserMessageIndex"   | 'openai.user_message_index'   | "user message"
    }
}
