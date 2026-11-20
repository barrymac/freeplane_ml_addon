package com.barrymac.freeplane.addons.llm

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

@Subject(Keys)
class KeysSpec extends Specification {

    @TempDir
    File directory

    def setup() {
        Keys.refresh()
        ConfigKeys.OVERRIDE_DIR = directory
        new File(directory, ConfigKeys.SETTINGS_FILE).delete()
    }

    def cleanup() {
        ConfigKeys.OVERRIDE_DIR = null
    }

    def "the environment wins over a saved key"() {
        given:
        ConfigKeys.write(ConfigKeys.API_KEY, 'from-file')

        expect:
        // HOME is always present in a test environment
        Keys.resolve('HOME', ConfigKeys.read(ConfigKeys.API_KEY, '')) == System.getenv('HOME')
    }

    def "a saved key is used when the environment has none"() {
        given:
        ConfigKeys.write(ConfigKeys.API_KEY, 'from-file')

        expect:
        Keys.resolve('A_NAME_THAT_IS_NOT_SET_ANYWHERE',
                ConfigKeys.read(ConfigKeys.API_KEY, '')) == 'from-file'
    }

    def "nothing set anywhere resolves to empty rather than a guess"() {
        expect:
        Keys.resolve('A_NAME_THAT_IS_NOT_SET_ANYWHERE', '') == ''
        Keys.resolve('A_NAME_THAT_IS_NOT_SET_ANYWHERE', null) == ''
    }

    def "Jev reads the environment only"() {
        expect:
        Keys.jev() == (Keys.environment('JEV_API_KEY') ?: Keys.environment('TYPESAFE_API_KEY') ?: '')
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
}