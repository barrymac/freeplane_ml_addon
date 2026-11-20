package com.barrymac.freeplane.addons.llm

import org.freeplane.core.util.LogUtils

/**
 * THE single configuration gateway for the whole plugin.
 *
 * One file (plugin-settings.properties), one read, one write. No Freeplane
 * config object, no in-memory cache, no dual source of truth. The file is the
 * truth; every read hits it; every write lands in it immediately.
 *
 * Migration from legacy keys is done in the file on first read and written back,
 * so after one run the file contains only neutral keys.
 */
class ConfigKeys {

    static final String SETTINGS_FILE = 'addons/promptLlmAddOn/plugin-settings.properties'

    /** Neutral key -> the legacy Freeplane-config key it replaced. */
    static final Map<String, String> LEGACY = [
            (PROVIDER)            : 'openai.api_provider',
            (MODEL)               : 'openai.gpt_model',
            (API_KEY)             : 'openai.key',
            (AVAILABLE_MODELS)    : 'openai.available_models',
            (SYSTEM_MESSAGE_INDEX): 'openai.system_message_index',
            (USER_MESSAGE_INDEX)  : 'openai.user_message_index',
            (TEMPERATURE)         : 'openai.temperature',
            (MAX_RESPONSE_LENGTH) : 'openai.max_response_length'
    ]

    // --- key constants -------------------------------------------------------
    static final String PROVIDER = 'llm.provider'
    static final String MODEL = 'llm.model'
    static final String API_KEY = 'llm.key'
    static final String AVAILABLE_MODELS = 'llm.available_models'
    static final String SYSTEM_MESSAGE_INDEX = 'llm.system_message_index'
    static final String USER_MESSAGE_INDEX = 'llm.user_message_index'
    static final String TEMPERATURE = 'llm.temperature'
    static final String MAX_RESPONSE_LENGTH = 'llm.max_response_length'
    static final String BASE_URL = 'llm.base_url'
    static final String IMAGE_PROVIDER = 'imagegen.provider'
    static final String IMAGE_MODEL = 'imagegen.model'
    static final String ATTESTED_BY = 'llm.attestedBy'
    static final String DRY_RUN = 'llm.neuros.dryRun'
    static final String CANDIDATES_FILE = 'llm.neuros.candidatesFile'
    static final String LIST_TIMEOUT = 'llm.neuros.listTimeoutSeconds'

    /** Test override for the settings file's parent directory. */
    static File OVERRIDE_DIR = null

    // --- the file ------------------------------------------------------------

    private static File settingsFile() {
        if (OVERRIDE_DIR != null) {
            return new File(OVERRIDE_DIR, SETTINGS_FILE)
        }
        String xdg = System.getenv('XDG_CONFIG_HOME') ?: System.getProperty('user.home') + '/.config'
        return new File("${xdg}/freeplane/1.12.x/${SETTINGS_FILE}")
    }

    private static Properties load() {
        Properties props = new Properties()
        File file = settingsFile()
        if (file.isFile()) {
            try { file.withInputStream { props.load(it) } } catch (Exception ignored) {}
        }
        return props
    }

    private static void store(Properties props) {
        File file = settingsFile()
        file.parentFile?.mkdirs()
        file.withOutputStream { props.store(it, 'Freeplane LLM add-on settings') }
    }

    // --- read ----------------------------------------------------------------

    /**
     * Read one setting. The file is the only source: no Freeplane config, no
     * cache, no in-memory state. A legacy key is migrated into the neutral key
     * on first read so the file converges.
     */
    static String read(String key, String fallback = null) {
        Properties props = load()

        // Neutral key first
        String value = props.getProperty(key)
        if (value != null && value.trim()) return value

        // Legacy migration: if the legacy key exists in the file, move it
        String legacy = LEGACY[key]
        if (legacy) {
            String old = props.getProperty(legacy)
            if (old != null && old.trim()) {
                props.setProperty(key, old)
                props.remove(legacy)
                store(props)
                return old
            }
        }

        // Legacy fallback: the old key might still be in Freeplane's config
        // (one-time migration, then gone)
        if (legacy) {
            try {
                String fromFreeplane = freeplaneConfigValue(legacy)
                if (fromFreeplane != null && fromFreeplane.trim()) {
                    write(key, fromFreeplane)
                    return fromFreeplane
                }
            } catch (Exception ignored) {}
        }

        return fallback
    }

    // --- write ---------------------------------------------------------------

    /**
     * Write one setting. Lands in the file immediately; no in-memory state
     * to go stale, no second config to diverge.
     */
    static void write(String key, Object value) {
        Properties props = load()
        props.setProperty(key, value == null ? '' : value.toString())
        // Remove any legacy twin so there is exactly one key per setting
        String legacy = LEGACY.find { it.key == key }?.value
        if (legacy) props.remove(legacy)
        store(props)
    }

    /** Write several settings in one file operation. */
    static void writeAll(Map<String, Object> entries) {
        Properties props = load()
        entries.each { key, value ->
            props.setProperty(key.toString(), value == null ? '' : value.toString())
            String legacy = LEGACY[key]
            if (legacy) props.remove(legacy)
        }
        store(props)
    }

    /** Read every key that currently has a value. */
    static Map<String, String> readAll() {
        Properties props = load()
        Map<String, String> result = [:]
        props.propertyNames().each { name ->
            result[name.toString()] = props.getProperty(name.toString())
        }
        return result
    }

    // --- Freeplane legacy migration (read-only, one-time) --------------------

    private static String freeplaneConfigValue(String key) {
        // This is the ONLY place the Freeplane config is ever read, and only
        // during migration from a legacy key. After one successful read the
        // value lives in the file and this path is never taken again.
        return null // Migration from Freeplane's config is complete; the file is the truth
    }
}