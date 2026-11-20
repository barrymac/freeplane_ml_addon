package com.barrymac.freeplane.addons.llm

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir
import spock.lang.Unroll

@Subject(ConfigKeys)
class ConfigKeysWriteSpec extends Specification {

    @TempDir
    File directory

    private static Map configOf(Map<String, Object> values = [:]) {
        return [
                getProperty : { String key, Object fallback = null ->
                    values.containsKey(key) ? values[key] : fallback
                },
                setProperty : { String key, Object value -> values[key] = value },
                freeplaneUserDirectory: null,
                values      : values
        ] as Map
    }

    // --- write coerces every type to String -------------------------------

    @Unroll
    def "write coerces #type value #value to String '#expected'"() {
        given:
        def config = configOf()

        when:
        ConfigKeys.write(config, ConfigKeys.MODEL, value)

        then:
        config.values[ConfigKeys.MODEL] == expected

        where:
        type     | value                    | expected
        'String' | 'qwen/qwen4-max'         | 'qwen/qwen4-max'
        'Integer' | 2000                     | '2000'
        'Double'  | 0.7                      | '0.7'
        'Boolean'  | true                    | 'true'
        'null'    | null                     | ''
    }

    // --- persist + readPersisted round-trip through the file --------------

    def "write then readPersisted returns the same value"() {
        given:
        def config = configOf()
        ConfigKeys.OVERRIDE_DIR = directory

        when:
        ConfigKeys.write(config, 'llm.neuros.dryRun', 'false')
        String read = ConfigKeys.readPersisted('llm.neuros.dryRun')

        then:
        read == 'false'
        new File(directory, 'addons/promptLlmAddOn/plugin-settings.properties').isFile()

        cleanup:
        ConfigKeys.OVERRIDE_DIR = null
    }

    @Unroll
    def "round-trip: write '#key' as '#value', read back as '#expected'"() {
        given:
        def config = configOf()
        ConfigKeys.OVERRIDE_DIR = directory

        when:
        ConfigKeys.write(config, key, value)
        String back = ConfigKeys.readPersisted(key)

        then:
        back == expected

        cleanup:
        ConfigKeys.OVERRIDE_DIR = null

        where:
        key                              | value      | expected
        'llm.neuros.dryRun'              | 'false'    | 'false'
        'llm.attestedBy'                 | 'barrymac' | 'barrymac'
        'llm.neuros.listTimeoutSeconds'  | 30         | '30'
        'llm.model'                      | 'test/x'   | 'test/x'
        'llm.temperature'                | 0.5        | '0.5'
    }

    // --- read falls back to persisted when config is empty -----------------

    def "read uses the persisted file when the config object has no value"() {
        given:
        def config = configOf()
        ConfigKeys.OVERRIDE_DIR = directory
        ConfigKeys.write(config, 'llm.neuros.dryRun', 'false')

        when:
        // A fresh config with no value for the key
        String fromEmpty = ConfigKeys.read(configOf(), 'llm.neuros.dryRun', 'true')

        then:
        fromEmpty == 'false'

        cleanup:
        ConfigKeys.OVERRIDE_DIR = null
    }

    // --- legacy migration ---------------------------------------------------

    def "a legacy value migrates to the neutral key on read"() {
        given:
        def config = configOf([(ConfigKeys.LEGACY[ConfigKeys.MODEL]): 'legacy-model'])

        when:
        String model = ConfigKeys.read(config, ConfigKeys.MODEL, 'fallback')

        then:
        model == 'legacy-model'
        // The neutral key now holds the migrated value
        config.values[ConfigKeys.MODEL] == 'legacy-model'
    }

    // --- the neutral key wins over legacy ------------------------------------

    def "a neutral value is never overridden by a legacy value"() {
        given:
        def config = configOf([
                (ConfigKeys.PROVIDER)                  : 'openrouter',
                (ConfigKeys.LEGACY[ConfigKeys.PROVIDER]): 'openai'
        ])

        expect:
        ConfigKeys.read(config, ConfigKeys.PROVIDER, 'fallback') == 'openrouter'
    }

    // --- multiple writes to the same file ------------------------------------

    def "multiple writes accumulate in one file, later values win"() {
        given:
        def config = configOf()
        ConfigKeys.OVERRIDE_DIR = directory

        when:
        ConfigKeys.write(config, 'llm.attestedBy', 'alice')
        ConfigKeys.write(config, 'llm.neuros.dryRun', 'false')
        ConfigKeys.write(config, 'llm.attestedBy', 'bob')

        then:
        ConfigKeys.readPersisted('llm.attestedBy') == 'bob'
        ConfigKeys.readPersisted('llm.neuros.dryRun') == 'false'

        cleanup:
        ConfigKeys.OVERRIDE_DIR = null
    }

    // --- write to a null config fails loudly ---------------------------------

    def "writing to a null config fails loudly"() {
        when:
        ConfigKeys.write(null, 'llm.neuros.dryRun', 'false')

        then:
        thrown(IllegalArgumentException)
    }

    // --- the settings file is the single source of truth ----------------------

    def "the settings file lives at the canonical path"() {
        given:
        ConfigKeys.OVERRIDE_DIR = directory

        when:
        def config = configOf()
        ConfigKeys.write(config, 'test.key', 'test.value')
        File settings = new File(directory, ConfigKeys.SETTINGS_FILE)

        then:
        settings.isFile()
        settings.text.contains('test.key=test.value')
        // No other properties file exists
        def parent = settings.parentFile
        parent.listFiles().findAll { it.name.endsWith('.properties') }.size() == 1

        cleanup:
        ConfigKeys.OVERRIDE_DIR = null
    }
}