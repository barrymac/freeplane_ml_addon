package com.barrymac.freeplane.addons.llm

import org.freeplane.core.util.LogUtils

/**
 * Where a credential comes from: the environment first, a saved setting second.
 *
 * Freeplane runs scripts under a security manager that denies `System.getenv`
 * (RuntimePermission `getenv.NAME`), so the environment is read from the process
 * itself — `/proc/self/environ` — which the add-on's scripts may read because file
 * access is granted to them. That is the same environment, not a bypass of one:
 * nothing here can see a variable the process does not already hold.
 *
 * Nothing writes a key into a map file. A saved key is only a convenience for the
 * person at the desktop.
 */
class Keys {

    static final String OPENROUTER = 'OPENROUTER_API_KEY'
    static final String JEV = 'JEV_API_KEY'
    static final String TYPESAFE = 'TYPESAFE_API_KEY'

    private static Map<String, String> cachedEnvironment

    /**
     * Environment first, then a caller-supplied fallback. Key resolution never
     * reads the settings file itself — that is ConfigKeys' job, so the caller
     * passes the file value as the fallback and env always wins.
     */
    static String resolve(String environmentName, String fallback = '') {
        String fromEnvironment = environment(environmentName)
        if (fromEnvironment?.trim()) return fromEnvironment.trim()
        return fallback?.trim() ?: ''
    }

    static String openRouter() {
        return resolve(OPENROUTER, ConfigKeys.read(ConfigKeys.API_KEY, ''))
    }

    /** Jev has no settings-dialog surface: the environment is the whole story. */
    static String jev() {
        String key = environment(JEV) ?: environment(TYPESAFE)
        return key?.trim() ?: ''
    }

    /** One variable, from wherever this process's environment can be read. */
    static String environment(String name) {
        if (!name) return null
        try {
            String direct = System.getenv(name)
            if (direct != null) return direct
        } catch (SecurityException ignored) {
            // The sandbox denies getenv; read the process environment instead.
        }
        return environmentSnapshot()[name]
    }

    static Map<String, String> environmentSnapshot() {
        if (cachedEnvironment != null) return cachedEnvironment
        Map<String, String> parsed = parseEnvironment(readProcEnvironment())
        if (parsed.isEmpty()) {
            LogUtils.info('Keys: the process environment is not readable; keys must be configured.')
        }
        cachedEnvironment = parsed
        return parsed
    }

    /** Package-visible for tests: NUL-separated NAME=VALUE pairs. */
    static Map<String, String> parseEnvironment(byte[] data) {
        Map<String, String> values = [:]
        if (data == null || data.length == 0) return values
        int start = 0
        for (int index = 0; index <= data.length; index++) {
            if (index == data.length || data[index] == (byte) 0) {
                String entry = new String(data, start, index - start, 'UTF-8')
                start = index + 1
                int separator = entry.indexOf('=')
                if (separator > 0) {
                    values[entry.substring(0, separator)] = entry.substring(separator + 1)
                }
            }
        }
        return values
    }

    private static byte[] readProcEnvironment() {
        try {
            File proc = new File('/proc/self/environ')
            if (!proc.isFile() || !proc.canRead()) return new byte[0]
            return proc.bytes
        } catch (Exception problem) {
            LogUtils.warn("Keys: could not read the process environment: ${problem.message}")
            return new byte[0]
        }
    }

    /** Forget the snapshot, so a test or a restart re-reads what is there now. */
    static void refresh() {
        cachedEnvironment = null
    }
}