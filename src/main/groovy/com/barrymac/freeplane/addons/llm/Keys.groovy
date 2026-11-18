package com.barrymac.freeplane.addons.llm

/**
 * Where a credential comes from: the environment first, a saved setting second.
 *
 * The environment is the operator's channel and always wins, so an exported key
 * overrides anything a dialog once stored. Nothing here writes a key into a map
 * file; a saved key is only a convenience for the person using the desktop.
 */
class Keys {

    static final String OPENROUTER = 'OPENROUTER_API_KEY'
    static final String JEV = 'JEV_API_KEY'

    /** Environment first, then the configured value, then empty. */
    static String resolve(String environmentName, def config, String configKey = null) {
        String fromEnvironment = System.getenv(environmentName)
        if (fromEnvironment?.trim()) return fromEnvironment.trim()
        if (config == null || !configKey) return ''
        return ((String) config.getProperty((String) configKey, (String) '') ?: '').toString().trim()
    }

    static String openRouter(def config) {
        return resolve(OPENROUTER, config, ConfigKeys.API_KEY)
    }

    /** Jev has no settings-dialog surface: the environment is the whole story. */
    static String jev() {
        String key = System.getenv(JEV) ?: System.getenv('TYPESAFE_API_KEY')
        return key?.trim() ?: ''
    }
}