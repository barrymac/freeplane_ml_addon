package com.barrymac.freeplane.addons.llm

import spock.lang.Specification
import spock.lang.Subject

@Subject(ConfigManager)
class ConfigManagerSpec extends Specification {

    /** A Freeplane-like config backed by a map, matching getProperty(key, fallback). */
    private static Map configOf(Map<String, Object> values) {
        return [
                getProperty: { String key, Object fallback = null ->
                    values.containsKey(key) ? values[key] : fallback
                },
                setProperty: { String key, Object value -> values[key] = value },
                values     : values
        ] as Map
    }

    def "base config reads the neutral keys"() {
        given:
        def config = configOf([
                (ConfigKeys.PROVIDER)            : 'openrouter',
                (ConfigKeys.MODEL)               : 'qwen/qwen4-max',
                (ConfigKeys.MAX_RESPONSE_LENGTH) : '2000',
                (ConfigKeys.TEMPERATURE)         : '0.7',
                (ConfigKeys.API_KEY)             : 'test-key'
        ])

        when:
        ApiConfig api = ConfigManager.loadBaseConfig(config)

        then:
        api.provider == 'openrouter'
        api.model == 'qwen/qwen4-max'
        api.maxTokens == 2000
        api.temperature == 0.7
        api.apiKey == 'test-key'
        api.availableModels == ConfigManager.DEFAULT_MODELS
    }

    def "custom values and a model list are respected"() {
        given:
        def config = configOf([
                (ConfigKeys.PROVIDER)         : 'openrouter',
                (ConfigKeys.MODEL)            : 'deepseek/deepseek-v4.1-flash-1017',
                (ConfigKeys.MAX_RESPONSE_LENGTH): '1500',
                (ConfigKeys.TEMPERATURE)      : '0.5',
                (ConfigKeys.AVAILABLE_MODELS) : "model1\nmodel2"
        ])

        when:
        ApiConfig api = ConfigManager.loadBaseConfig(config)

        then:
        api.model == 'deepseek/deepseek-v4.1-flash-1017'
        api.maxTokens == 1500
        api.temperature == 0.5
        api.availableModels == ['model1', 'model2']
    }

    def "a broken value falls back instead of failing the dialog"() {
        given:
        def config = configOf([(ConfigKeys.MAX_RESPONSE_LENGTH): 'not-a-number'])

        when:
        ApiConfig api = ConfigManager.loadBaseConfig(config)

        then:
        noExceptionThrown()
        api.provider == 'openrouter'
        api.model == ConfigManager.DEFAULT_MODEL
    }

    def "the message index reads its neutral key"() {
        given:
        def config = configOf([(ConfigKeys.SYSTEM_MESSAGE_INDEX): '5', (ConfigKeys.USER_MESSAGE_INDEX): '3'])

        expect:
        ConfigManager.getSystemMessageIndex(config) == 5
        ConfigManager.getUserMessageIndex(config) == 3
    }

    def "an unreadable index defaults to zero"() {
        given:
        def config = [
                getProperty: { String key, Object fallback = null ->
                    if (key.startsWith('llm.')) throw new IllegalStateException('boom')
                    return fallback
                }
        ] as Map

        expect:
        ConfigManager.getSystemMessageIndex(config) == 0
        ConfigManager.getUserMessageIndex(config) == 0
    }

    def "default models carry no OpenAI or Anthropic ids"() {
        expect:
        ConfigManager.DEFAULT_MODELS.every { model ->
            !model.toLowerCase().startsWith('openai/') &&
                    !model.toLowerCase().startsWith('anthropic/') &&
                    !model.toLowerCase().startsWith('claude') &&
                    !model.toLowerCase().startsWith('gpt')
        }
        ConfigManager.DEFAULT_MODEL == ConfigManager.DEFAULT_MODELS[0]
    }
}