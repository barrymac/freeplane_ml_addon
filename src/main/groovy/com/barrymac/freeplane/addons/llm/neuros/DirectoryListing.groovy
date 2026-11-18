package com.barrymac.freeplane.addons.llm.neuros

import org.freeplane.core.util.LogUtils

/**
 * Directory listings that cannot hang the action.
 *
 * Every knowledge read here crosses a network filesystem, and a plain
 * `listFiles()` on a busy host blocks for minutes with no output — the action
 * looks hung and the operator has nothing to go on. One helper, used everywhere,
 * logs what it is listing and refuses to wait longer than the budget.
 */
class DirectoryListing {

    static final int TIMEOUT_SECONDS = intValue('llm.neuros.listTimeoutSeconds', 20)

    /** Entries in a directory, or an honest failure. */
    static File[] list(File directory, String label) {
        if (directory == null || !directory.isDirectory()) return new File[0]
        File[] result = null
        Throwable failure = null
        Thread worker = new Thread({
            try {
                result = directory.listFiles()
            } catch (Throwable problem) {
                failure = problem
            }
        } as Runnable, "neuros-listing-${label}".toString())
        worker.daemon = true
        LogUtils.info("list: ${label} …")
        long started = System.currentTimeMillis()
        worker.start()
        worker.join(TIMEOUT_SECONDS * 1000L)
        long elapsed = System.currentTimeMillis() - started
        if (worker.alive) {
            LogUtils.severe("list: ${label} did not answer in ${TIMEOUT_SECONDS}s")
            throw new IOException(
                    'The knowledge source is slow right now (listing ' + label + ' timed out). ' +
                            'Try again when it is quieter.')
        }
        if (failure != null) {
            LogUtils.warn("list: ${label} failed: ${failure.message}")
            return new File[0]
        }
        LogUtils.info("list: ${label} → ${result?.length ?: 0} entries in ${elapsed}ms")
        return result ?: new File[0]
    }

    private static int intValue(String property, int fallback) {
        try {
            return Integer.parseInt(System.getProperty(property, String.valueOf(fallback)))
        } catch (Exception ignored) {
            return fallback
        }
    }
}