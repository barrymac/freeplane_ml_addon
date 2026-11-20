package com.barrymac.freeplane.addons.llm

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir
import spock.lang.Unroll

@Subject(ConfigKeys)
class ConfigKeysWriteSpec extends Specification {

    @TempDir
    File directory

    def setup() {
        ConfigKeys.OVERRIDE_DIR = directory
    }

    def cleanup() {
        ConfigKeys.OVERRIDE_DIR = null
    }

    @Unroll
    def "write coerces #type value #value to String '#expected'"() {
        when:
        ConfigKeys.write(ConfigKeys.MODEL, value)

        then:
        ConfigKeys.read(ConfigKeys.MODEL) == expected

        where:
        type     | value             | expected
        'String' | 'qwen/qwen4-max'  | 'qwen/qwen4-max'
        'Integer'| 2000              | '2000'
        'Double' | 0.7               | '0.7'
        'Boolean'| true              | 'true'
        'GString'| "${42}"           | '42'
    }

    @Unroll
    def "round-trip: write '#key' as '#value', read back as '#expected'"() {
        when:
        ConfigKeys.write(key, value)

        then:
        ConfigKeys.read(key) == expected

        where:
        key                             | value      | expected
        'llm.neuros.dryRun'             | 'false'    | 'false'
        'llm.attestedBy'                | 'barrymac' | 'barrymac'
        'llm.neuros.listTimeoutSeconds' | 30         | '30'
        'llm.model'                     | 'test/x'   | 'test/x'
        'llm.temperature'               | 0.5        | '0.5'
    }

    def "multiple writes accumulate, later values win"() {
        when:
        ConfigKeys.write('llm.attestedBy', 'alice')
        ConfigKeys.write('llm.neuros.dryRun', 'false')
        ConfigKeys.write('llm.attestedBy', 'bob')

        then:
        ConfigKeys.read('llm.attestedBy') == 'bob'
        ConfigKeys.read('llm.neuros.dryRun') == 'false'
    }

    def "writeAll writes several keys in one file operation"() {
        when:
        ConfigKeys.writeAll([
                'llm.provider': 'openrouter',
                'llm.model'   : 'test/model',
                'llm.key'     : 'sk-test'
        ])

        then:
        ConfigKeys.read('llm.provider') == 'openrouter'
        ConfigKeys.read('llm.model') == 'test/model'
        ConfigKeys.read('llm.key') == 'sk-test'
    }

    def "readAll returns every key that has a value"() {
        given:
        ConfigKeys.writeAll([a: '1', b: '2', c: '3'])

        when:
        Map<String, String> all = ConfigKeys.readAll()

        then:
        all.a == '1'
        all.b == '2'
        all.c == '3'
    }

    def "a legacy value migrates to the neutral key on first read"() {
        given:
        ConfigKeys.write('openai.gpt_model', 'legacy-model')

        when:
        String model = ConfigKeys.read(ConfigKeys.MODEL)

        then:
        model == 'legacy-model'
        // The legacy key is removed from the file
        ConfigKeys.readAll()['openai.gpt_model'] == null
        // The neutral key now holds the value
        ConfigKeys.readAll()[ConfigKeys.MODEL] == 'legacy-model'
    }

    def "an unset key returns the fallback"() {
        expect:
        ConfigKeys.read('llm.nonexistent', 'fallback') == 'fallback'
        ConfigKeys.read('llm.nonexistent') == null
    }

    def "the settings file lives at the canonical path and is the only one"() {
        when:
        ConfigKeys.write('test.key', 'test.value')
        File settings = new File(directory, ConfigKeys.SETTINGS_FILE)

        then:
        settings.isFile()
        settings.text.contains('test.key=test.value')
        settings.parentFile.listFiles()
                .findAll { it.name.endsWith('.properties') }.size() == 1
    }
}