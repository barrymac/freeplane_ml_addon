package com.barrymac.freeplane.addons.llm

import spock.lang.Specification
import spock.lang.Subject

@Subject(Keys)
class KeysSpec extends Specification {

    /**
     * Freeplane's ConfigProperties exposes getProperty(String) plus overloads for
     * String, boolean, int and long. Passing an untyped '' or null is ambiguous;
     * this stub reproduces that shape so the typed call is locked in.
     */
    static class OverloadedConfig {
        Map<String, String> values = [:]
        String getProperty(String key) { values[key] }
        String getProperty(String key, String fallback) { values[key] ?: fallback }
        boolean getProperty(String key, boolean fallback) { fallback }
        int getProperty(String key, int fallback) { fallback }
        long getProperty(String key, long fallback) { fallback }
    }

    def "the typed lookup resolves through Freeplane's overload set"() {
        given:
        def config = new OverloadedConfig(values: [(ConfigKeys.API_KEY): 'from-config'])

        when:
        String key = Keys.resolve('A_NAME_THAT_IS_NOT_SET_ANYWHERE', config, ConfigKeys.API_KEY)
        String model = ConfigKeys.property(config, ConfigKeys.MODEL, 'fallback')

        then:
        key == 'from-config'
        model == 'fallback'
    }


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

    def "the process environment parses NUL-separated pairs"() {
        given:
        byte[] data = 'PATH=/usr/bin\u0000JEV_API_KEY=abc123\u0000EMPTY=\u0000WEIRD'.bytes

        when:
        Map<String, String> values = Keys.parseEnvironment(data)

        then:
        values['PATH'] == '/usr/bin'
        values['JEV_API_KEY'] == 'abc123'
        values['EMPTY'] == ''
        !values.containsKey('WEIRD')
    }

    def "an empty or missing environment parses to nothing"() {
        expect:
        Keys.parseEnvironment(new byte[0]).isEmpty()
        Keys.parseEnvironment(null).isEmpty()
    }

    def "Jev is read from the environment snapshot, not from config"() {
        given:
        Keys.refresh()

        expect:
        Keys.jev() == (Keys.environment('JEV_API_KEY') ?: Keys.environment('TYPESAFE_API_KEY') ?: '')
    }

    def "Jev reads the environment only"() {
        expect:
        Keys.jev() == (System.getenv('JEV_API_KEY') ?: System.getenv('TYPESAFE_API_KEY') ?: '')
    }
}