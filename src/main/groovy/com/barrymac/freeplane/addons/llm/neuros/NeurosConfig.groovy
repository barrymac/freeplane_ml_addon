package com.barrymac.freeplane.addons.llm.neuros

import com.barrymac.freeplane.addons.llm.ConfigManager

/**
 * Access to the add-on's neurOS bridge preferences (all stored under the
 * 'llm.addon.neuros.' property prefix via ConfigManager).
 *
 * The two path preferences are DELIBERATELY un-defaulted: the neurOS
 * features are opt-in and refuse loudly (see NeurosClient.assertConfigured)
 * until the user configures them. No silent fallback to a guessed mount or
 * workspace location.
 */
class NeurosConfig {

    static final String MOUNT_PATH_KEY = 'neuros.mount_path'
    static final String WORKSPACE_PATH_KEY = 'neuros.workspace_path'
    static final String RESULTS_LIMIT_KEY = 'neuros.results_limit'
    static final String CARDS_DIR_KEY = 'neuros.cards_dir'

    /** Path of a live neurOS FUSE mount (read surface). No default. */
    static String getMountPath(def config) {
        ConfigManager.getUserProperty(config, MOUNT_PATH_KEY, '').trim()
    }

    /** Path of the watched workspace of that mount (write surface). No default. */
    static String getWorkspacePath(def config) {
        ConfigManager.getUserProperty(config, WORKSPACE_PATH_KEY, '').trim()
    }

    /** Maximum number of search results attached per query. */
    static int getResultsLimit(def config) {
        try {
            return (ConfigManager.getUserProperty(config, RESULTS_LIMIT_KEY, '5') ?: '5') as int
        } catch (Exception ignored) {
            return 5
        }
    }

    /** Directory (relative to the workspace) where pushed cards are written. */
    static String getCardsDir(def config) {
        ConfigManager.getUserProperty(config, CARDS_DIR_KEY, 'neuros-cards').trim() ?: 'neuros-cards'
    }

    static void save(def config, String mountPath, String workspacePath,
                     String resultsLimit, String cardsDir) {
        ConfigManager.setUserProperty(config, MOUNT_PATH_KEY, mountPath?.trim() ?: '')
        ConfigManager.setUserProperty(config, WORKSPACE_PATH_KEY, workspacePath?.trim() ?: '')
        ConfigManager.setUserProperty(config, RESULTS_LIMIT_KEY, (resultsLimit ?: '5').trim())
        ConfigManager.setUserProperty(config, CARDS_DIR_KEY, (cardsDir ?: 'neuros-cards').trim())
    }

    /**
     * Loud refusal when the bridge is not configured — the message tells the
     * user exactly which menu item to open. Returns null when everything is
     * in place.
     */
    static String missingConfiguration(def config) {
        def missing = []
        if (!getMountPath(config)) missing << 'mount path'
        if (!getWorkspacePath(config)) missing << 'workspace path'
        if (missing) {
            return "neurOS bridge is not configured (${missing.join(' and ')} missing).\n" +
                    "Open 'LLM > Configure neurOS' and set the mount and workspace paths first."
        }
        return null
    }
}
