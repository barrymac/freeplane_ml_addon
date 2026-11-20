package com.barrymac.freeplane.addons.llm

/**
 * Configuration keys for the add-on, and the one-time migration away from the
 * OpenAI-era names.
 *
 * The add-on is provider-neutral: OpenRouter by default, Novita for diffusion
 * images, and any OpenAI-compatible endpoint the user configures. Old `openai.*`
 * values are read once, rewritten under the neutral key, and then ignored, so an
 * existing installation keeps its settings without carrying the vendor name
 * forward.
 */
class ConfigKeys {

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

    /** neutral key -> the legacy key it replaces */
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

    /**
     * Reads a setting by its neutral key, falling back once to the legacy key
     * and migrating the value when it finds one there.
     */
    /**
     * Map storage and the global config expose different getProperty overloads:
     * map storage has getProperty(String) only, the global config has
     * getProperty(String, String). Try the single-argument form first so a map
     * storage never sees an ambiguous two-argument call.
     */
    private static String property(def config, String key, String fallback) {
        if (config == null) return fallback
        try {
            def single = config.getProperty(key)
            if (single != null) return single.toString()
        } catch (MissingMethodException ignored) {
            // fall through to the two-argument form
        }
        try {
            def pair = config.getProperty((String) key, (String) fallback)
            return pair == null ? fallback : pair.toString()
        } catch (Exception ignored) {
            return fallback
        }
    }

    static String read(def config, String key, String fallback = null) {
        if (config == null) return fallback
        def current = property(config, key, null)
        if (current != null && current.toString().trim()) return current.toString()

        String legacy = LEGACY[key]
        if (legacy) {
            def old = property(config, legacy, null)
            if (old != null && old.toString().trim()) {
                config.setProperty(key, old)
                return old.toString()
            }
        }
        return readPersisted(key) ?: fallback
    }

    /** The single settings file for the whole plugin, neurOS and LLM alike. */
    static final String SETTINGS_FILE = 'addons/promptLlmAddOn/plugin-settings.properties'

    private static File persistedFile(def config) {
        if (config != null && config.freeplaneUserDirectory) {
            return new File(config.freeplaneUserDirectory, SETTINGS_FILE)
        }
        // Without a config object (e.g. from a dialog helper that has no scope),
        // resolve the standard Freeplane user directory so reads never silently
        // return nothing.
        String xdg = System.getenv('XDG_CONFIG_HOME') ?: System.getProperty('user.home') + '/.config'
        return new File("${xdg}/freeplane/1.12.x/${SETTINGS_FILE}")
    }

    static String readPersisted(String key) {
        try {
            File file = persistedFile(null)
            if (file.isFile()) {
                Properties props = new Properties()
                file.withInputStream { props.load(it) }
                String value = props.getProperty(key)
                if (value != null) return value
            }
            // Fall back to the old file so a migration never loses a setting.
            File old = new File(file.parentFile, 'neuros-settings.properties')
            if (old.isFile()) {
                Properties legacy = new Properties()
                old.withInputStream { legacy.load(it) }
                return legacy.getProperty(key)
            }
            return null
        } catch (Exception ignored) {
            return null
        }
    }

    static void persist(def config, String key, Object value) {
        try {
            File file = persistedFile(config)
            file.parentFile.mkdirs()
            Properties props = new Properties()
            if (file.isFile()) file.withInputStream { props.load(it) }
            props.setProperty(key, value == null ? '' : value.toString())
            file.withOutputStream { props.store(it, 'neurOS settings') }
        } catch (Exception problem) {
            org.freeplane.core.util.LogUtils.warn("Could not persist ${key}: ${problem.message}")
        }
    }

    static void write(def config, String key, Object value) {
        if (config == null) throw new IllegalArgumentException('No configuration to write to.')
        // Freeplane's setProperty is String-only: a model's maxTokens (Integer)
        // or temperature (Double) must be coerced, never passed as a raw number.
        config.setProperty(key, value == null ? '' : value.toString())
        // Also persist on disk so an abrupt stop never loses the setting.
        persist(config, key, value)
    }
}