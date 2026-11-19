package com.barrymac.freeplane.addons.llm

import spock.lang.Specification
import spock.lang.Subject

@Subject(ConfigKeys)
class ConfigKeysSpec extends Specification {

    private static Map configOf(Map<String, Object> values) {
        return [
                getProperty: { String key, Object fallback = null ->
                    values.containsKey(key) ? values[key] : fallback
                },
                setProperty: { String key, Object value -> values[key] = value },
                values     : values
        ] as Map
    }

    def "a neutral key is read directly"() {
        given:
        def config = configOf([(ConfigKeys.MODEL): 'qwen/qwen4-max'])

        expect:
        ConfigKeys.read(config, ConfigKeys.MODEL) == 'qwen/qwen4-max'
    }

    def "a legacy value is migrated once, then read from the neutral key"() {
        given:
        def values = [(ConfigKeys.LEGACY[ConfigKeys.MODEL]): 'legacy-model']
        def config = configOf(values)

        when:
        String first = ConfigKeys.read(config, ConfigKeys.MODEL, 'fallback')

        then:
        first == 'legacy-model'
        values[ConfigKeys.MODEL] == 'legacy-model'

        when:
        values.remove(ConfigKeys.LEGACY[ConfigKeys.MODEL])
        String second = ConfigKeys.read(config, ConfigKeys.MODEL, 'fallback')

        then:
        second == 'legacy-model'
    }

    def "the neutral key wins over a legacy value"() {
        given:
        def config = configOf([
                (ConfigKeys.PROVIDER)                  : 'openrouter',
                (ConfigKeys.LEGACY[ConfigKeys.PROVIDER]): 'whatever'
        ])

        expect:
        ConfigKeys.read(config, ConfigKeys.PROVIDER) == 'openrouter'
    }

    def "an unset key returns the caller's fallback"() {
        given:
        def config = configOf([:])

        expect:
        ConfigKeys.read(config, ConfigKeys.API_KEY, '') == ''
        ConfigKeys.read(config, ConfigKeys.TEMPERATURE, '0.7') == '0.7'
    }

    def "map storage with only single-argument getProperty still reads"() {
        given:
        // Freeplane's PropertiesProxy exposes getProperty(String) only.
        def storage = [getProperty: { String key ->
            key == ConfigKeys.IMAGE_MODEL ? 'google/gemini-3.1-flash-image' : null
        }] as Map

        expect:
        ConfigKeys.read(storage, ConfigKeys.IMAGE_MODEL, 'fallback') ==
                'google/gemini-3.1-flash-image'
        ConfigKeys.read(storage, ConfigKeys.API_KEY, '') == ''
    }

    def "every neutral key that replaced a legacy one is mapped"() {
        expect:
        ConfigKeys.LEGACY.keySet() == [
                ConfigKeys.PROVIDER, ConfigKeys.MODEL, ConfigKeys.API_KEY,
                ConfigKeys.AVAILABLE_MODELS, ConfigKeys.SYSTEM_MESSAGE_INDEX,
                ConfigKeys.USER_MESSAGE_INDEX, ConfigKeys.TEMPERATURE,
                ConfigKeys.MAX_RESPONSE_LENGTH
        ] as Set
    }
}