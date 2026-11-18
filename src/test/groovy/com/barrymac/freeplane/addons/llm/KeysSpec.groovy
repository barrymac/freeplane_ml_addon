package com.barrymac.freeplane.addons.llm

import spock.lang.Specification
import spock.lang.Subject

@Subject(Keys)
class KeysSpec extends Specification {

    private static Map configOf(Map<String, Object> values) {
        return [
                getProperty: { String key, Object fallback = null ->
                    values.containsKey(key) ? values[key] : fallback
                }
        ] as Map
    }

    def "the environment wins over a saved key"() {
        given:
        // HOME is always present in a test environment; a missing name is not.
        def config = configOf([(ConfigKeys.API_KEY): 'from-config'])

        expect:
        Keys.resolve('HOME', config, ConfigKeys.API_KEY) == System.getenv('HOME')
    }

    def "a saved key is used only when the environment has none"() {
        given:
        def config = configOf([(ConfigKeys.API_KEY): 'from-config'])

        expect:
        Keys.resolve('A_NAME_THAT_IS_NOT_SET_ANYWHERE', config, ConfigKeys.API_KEY) == 'from-config'
    }

    def "nothing set anywhere resolves to empty rather than a guess"() {
        expect:
        Keys.resolve('A_NAME_THAT_IS_NOT_SET_ANYWHERE', configOf([:]), ConfigKeys.API_KEY) == ''
        Keys.resolve('A_NAME_THAT_IS_NOT_SET_ANYWHERE', null) == ''
    }

    def "Jev reads the environment only"() {
        expect:
        Keys.jev() == (System.getenv('JEV_API_KEY') ?: System.getenv('TYPESAFE_API_KEY') ?: '')
    }
}