package com.barrymac.freeplane.addons.llm

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

@Subject(ConfigKeys)
class ConfigKeysSpec extends Specification {

    @TempDir
    File directory

    def setup() {
        ConfigKeys.OVERRIDE_DIR = directory
        new File(directory, ConfigKeys.SETTINGS_FILE).delete()
    }

    def cleanup() {
        ConfigKeys.OVERRIDE_DIR = null
    }

    def "a key written to the file reads back directly"() {
        given:
        ConfigKeys.write(ConfigKeys.MODEL, 'qwen/qwen4-max')

        expect:
        ConfigKeys.read(ConfigKeys.MODEL) == 'qwen/qwen4-max'
    }

    def "a neutral key is used directly, no config object needed"() {
        given:
        ConfigKeys.write(ConfigKeys.PROVIDER, 'openrouter')

        expect:
        ConfigKeys.read(ConfigKeys.PROVIDER) == 'openrouter'
    }

    def "the neutral key wins over a legacy value in the file"() {
        given:
        ConfigKeys.writeAll([
                (ConfigKeys.PROVIDER)                  : 'openrouter',
                (ConfigKeys.LEGACY[ConfigKeys.PROVIDER]): 'openai'
        ])

        expect:
        ConfigKeys.read(ConfigKeys.PROVIDER) == 'openrouter'
    }

    def "an unset key returns the fallback"() {
        expect:
        ConfigKeys.read(ConfigKeys.API_KEY, '') == ''
        ConfigKeys.read(ConfigKeys.TEMPERATURE, '0.7') == '0.7'
        ConfigKeys.read('no.such.key') == null
    }

    def "every legacy key maps to a neutral key"() {
        expect:
        ConfigKeys.LEGACY.keySet() == [
                ConfigKeys.PROVIDER, ConfigKeys.MODEL, ConfigKeys.API_KEY,
                ConfigKeys.AVAILABLE_MODELS, ConfigKeys.SYSTEM_MESSAGE_INDEX,
                ConfigKeys.USER_MESSAGE_INDEX, ConfigKeys.TEMPERATURE,
                ConfigKeys.MAX_RESPONSE_LENGTH
        ] as Set
    }

    def "writing removes the legacy twin so there is one key per setting"() {
        given:
        ConfigKeys.write(ConfigKeys.LEGACY[ConfigKeys.MODEL], 'old-value')

        when:
        ConfigKeys.write(ConfigKeys.MODEL, 'new-value')

        then:
        ConfigKeys.read(ConfigKeys.MODEL) == 'new-value'
        ConfigKeys.readAll()[ConfigKeys.LEGACY[ConfigKeys.MODEL]] == null
    }
}