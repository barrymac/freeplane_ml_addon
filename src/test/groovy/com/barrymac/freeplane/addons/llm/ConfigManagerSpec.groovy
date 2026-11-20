package com.barrymac.freeplane.addons.llm

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

@Subject(ConfigManager)
class ConfigManagerSpec extends Specification {

    @TempDir
    File directory

    def setup() {
        ConfigKeys.OVERRIDE_DIR = directory
        // Start each test with a clean settings file
        new File(directory, ConfigKeys.SETTINGS_FILE).delete()
    }

    def cleanup() {
        ConfigKeys.OVERRIDE_DIR = null
    }

    private void write(Map<String, String> entries) {
        ConfigKeys.writeAll(entries)
    }

    def "base config reads the neutral keys from the file"() {
        given:
        write([
                (ConfigKeys.PROVIDER)          : 'openrouter',
                (ConfigKeys.MODEL)             : 'qwen/qwen4-max',
                (ConfigKeys.MAX_RESPONSE_LENGTH): '2000',
                (ConfigKeys.TEMPERATURE)       : '0.7',
                (ConfigKeys.API_KEY)           : 'test-key'
        ])

        when:
        ApiConfig api = ConfigManager.loadBaseConfig(null)

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
        write([
                (ConfigKeys.MODEL)             : 'deepseek/deepseek-v4.1-flash-1017',
                (ConfigKeys.MAX_RESPONSE_LENGTH): '1500',
                (ConfigKeys.TEMPERATURE)       : '0.5',
                (ConfigKeys.AVAILABLE_MODELS)  : 'model1\nmodel2'
        ])

        when:
        ApiConfig api = ConfigManager.loadBaseConfig(null)

        then:
        api.model == 'deepseek/deepseek-v4.1-flash-1017'
        api.maxTokens == 1500
        api.temperature == 0.5
        api.availableModels == ['model1', 'model2']
    }

    def "a broken value falls back instead of failing the dialog"() {
        given:
        write([(ConfigKeys.MAX_RESPONSE_LENGTH): 'not-a-number'])

        when:
        ApiConfig api = ConfigManager.loadBaseConfig(null)

        then:
        noExceptionThrown()
        api.provider == 'openrouter'
        api.model == ConfigManager.DEFAULT_MODEL
    }

    def "the message index reads its neutral key"() {
        given:
        write([(ConfigKeys.SYSTEM_MESSAGE_INDEX): '5',
               (ConfigKeys.USER_MESSAGE_INDEX)  : '3'])

        expect:
        ConfigManager.getSystemMessageIndex(null) == 5
        ConfigManager.getUserMessageIndex(null) == 3
    }

    def "an unreadable index defaults to zero"() {
        expect:
        ConfigManager.getSystemMessageIndex(null) == 0
        ConfigManager.getUserMessageIndex(null) == 0
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