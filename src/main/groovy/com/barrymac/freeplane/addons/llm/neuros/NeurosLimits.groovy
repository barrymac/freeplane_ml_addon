package com.barrymac.freeplane.addons.llm.neuros

/**
 * Conservative, optional per-map limits for neurOS operations.
 *
 * These are safety budgets only. Knowledge location and scope are NOT settings:
 * they are discovered from the map's own location by KnowledgeContext.
 */
class NeurosLimits {

    static final String CARD_FOLDER_KEY = 'neuros.card_folder'
    static final String RESULTS_LIMIT_KEY = 'neuros.results_limit'
    static final String WRITE_WAIT_KEY = 'neuros.write_wait_seconds'
    static final String USE_CONTEXT_KEY = 'neuros.use_map_context'
    static final String ANCESTOR_LIMIT_KEY = 'neuros.context.ancestor_limit'
    static final String CHILD_LIMIT_KEY = 'neuros.context.child_limit'
    static final String NOTE_LIMIT_KEY = 'neuros.context.note_limit'
    static final String CONTEXT_LIMIT_KEY = 'neuros.context.total_limit'
    static final String BROWSE_LIMIT_KEY = 'neuros.browse_limit'
    static final String PLANTUML_COMMAND_KEY = 'neuros.plantuml.command'
    static final String PLANTUML_TIMEOUT_KEY = 'neuros.plantuml.timeout_seconds'

    static final int DEFAULT_RESULTS_LIMIT = 5
    static final int MAX_RESULTS_LIMIT = 25
    static final int DEFAULT_WRITE_WAIT_SECONDS = 15
    static final int MAX_WRITE_WAIT_SECONDS = 60
    static final boolean DEFAULT_USE_CONTEXT = true
    static final int DEFAULT_ANCESTOR_LIMIT = 6
    static final int MAX_ANCESTOR_LIMIT = 20
    static final int DEFAULT_CHILD_LIMIT = 8
    static final int MAX_CHILD_LIMIT = 20
    static final int DEFAULT_NOTE_LIMIT = 600
    static final int MAX_NOTE_LIMIT = 2000
    static final int DEFAULT_CONTEXT_LIMIT = 1800
    static final int MAX_CONTEXT_LIMIT = 4000
    static final int DEFAULT_BROWSE_LIMIT = 25
    static final int MAX_BROWSE_LIMIT = 100
    static final String DEFAULT_PLANTUML_COMMAND = 'plantuml'
    static final int DEFAULT_PLANTUML_TIMEOUT_SECONDS = 15
    static final int MAX_PLANTUML_TIMEOUT_SECONDS = 60
    static final String DEFAULT_CARD_FOLDER = 'neuros-cards'

    String cardFolder = DEFAULT_CARD_FOLDER
    int resultsLimit = DEFAULT_RESULTS_LIMIT
    int writeWaitSeconds = DEFAULT_WRITE_WAIT_SECONDS
    boolean useContext = DEFAULT_USE_CONTEXT
    int ancestorLimit = DEFAULT_ANCESTOR_LIMIT
    int childLimit = DEFAULT_CHILD_LIMIT
    int noteLimit = DEFAULT_NOTE_LIMIT
    int contextLimit = DEFAULT_CONTEXT_LIMIT
    int browseLimit = DEFAULT_BROWSE_LIMIT
    String plantumlCommand = DEFAULT_PLANTUML_COMMAND
    int plantumlTimeoutSeconds = DEFAULT_PLANTUML_TIMEOUT_SECONDS

    static NeurosLimits load(def storage) {
        if (storage == null) {
            throw new IllegalArgumentException('This map has no storage for neurOS limits.')
        }
        return new NeurosLimits(
                cardFolder: optionalText(storage, CARD_FOLDER_KEY, DEFAULT_CARD_FOLDER),
                resultsLimit: optionalInt(storage, RESULTS_LIMIT_KEY,
                        DEFAULT_RESULTS_LIMIT, 1, MAX_RESULTS_LIMIT),
                writeWaitSeconds: optionalInt(storage, WRITE_WAIT_KEY,
                        DEFAULT_WRITE_WAIT_SECONDS, 1, MAX_WRITE_WAIT_SECONDS),
                useContext: optionalBoolean(storage, USE_CONTEXT_KEY, DEFAULT_USE_CONTEXT),
                ancestorLimit: optionalInt(storage, ANCESTOR_LIMIT_KEY,
                        DEFAULT_ANCESTOR_LIMIT, 0, MAX_ANCESTOR_LIMIT),
                childLimit: optionalInt(storage, CHILD_LIMIT_KEY,
                        DEFAULT_CHILD_LIMIT, 0, MAX_CHILD_LIMIT),
                noteLimit: optionalInt(storage, NOTE_LIMIT_KEY,
                        DEFAULT_NOTE_LIMIT, 0, MAX_NOTE_LIMIT),
                contextLimit: optionalInt(storage, CONTEXT_LIMIT_KEY,
                        DEFAULT_CONTEXT_LIMIT, 200, MAX_CONTEXT_LIMIT),
                browseLimit: optionalInt(storage, BROWSE_LIMIT_KEY,
                        DEFAULT_BROWSE_LIMIT, 1, MAX_BROWSE_LIMIT),
                plantumlCommand: optionalText(storage, PLANTUML_COMMAND_KEY,
                        DEFAULT_PLANTUML_COMMAND),
                plantumlTimeoutSeconds: optionalInt(storage, PLANTUML_TIMEOUT_KEY,
                        DEFAULT_PLANTUML_TIMEOUT_SECONDS, 1, MAX_PLANTUML_TIMEOUT_SECONDS)
        )
    }

    static void validate(NeurosLimits limits) {
        limits.cardFolder = normalizeCardFolder(limits.cardFolder)
        if (limits.resultsLimit < 1 || limits.resultsLimit > MAX_RESULTS_LIMIT) {
            throw new IllegalArgumentException(
                    "Search results must be between 1 and ${MAX_RESULTS_LIMIT}.")
        }
        if (limits.writeWaitSeconds < 1 || limits.writeWaitSeconds > MAX_WRITE_WAIT_SECONDS) {
            throw new IllegalArgumentException(
                    "The confirmation wait must be between 1 and ${MAX_WRITE_WAIT_SECONDS} seconds.")
        }
        if (limits.ancestorLimit < 0 || limits.ancestorLimit > MAX_ANCESTOR_LIMIT) {
            throw new IllegalArgumentException(
                    "The ancestor context limit must be between 0 and ${MAX_ANCESTOR_LIMIT}.")
        }
        if (limits.childLimit < 0 || limits.childLimit > MAX_CHILD_LIMIT) {
            throw new IllegalArgumentException(
                    "The child context limit must be between 0 and ${MAX_CHILD_LIMIT}.")
        }
        if (limits.noteLimit < 0 || limits.noteLimit > MAX_NOTE_LIMIT) {
            throw new IllegalArgumentException(
                    "The note context limit must be between 0 and ${MAX_NOTE_LIMIT} characters.")
        }
        if (limits.contextLimit < 200 || limits.contextLimit > MAX_CONTEXT_LIMIT) {
            throw new IllegalArgumentException(
                    "The total context limit must be between 200 and ${MAX_CONTEXT_LIMIT} characters.")
        }
        if (limits.browseLimit < 1 || limits.browseLimit > MAX_BROWSE_LIMIT) {
            throw new IllegalArgumentException(
                    "The browse page size must be between 1 and ${MAX_BROWSE_LIMIT}.")
        }
        if (!limits.plantumlCommand?.trim()) {
            throw new IllegalArgumentException('The PlantUML command cannot be empty.')
        }
        if (limits.plantumlTimeoutSeconds < 1 ||
                limits.plantumlTimeoutSeconds > MAX_PLANTUML_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException(
                    "The PlantUML timeout must be between 1 and ${MAX_PLANTUML_TIMEOUT_SECONDS} seconds.")
        }
    }

    Map contextOptions() {
        return [
                ancestorLimit: ancestorLimit,
                childLimit    : childLimit,
                noteLimit     : noteLimit,
                totalLimit    : contextLimit
        ]
    }

    private static String text(def storage, String key) {
        def value = storage[key]
        return value == null ? '' : value.toString().trim()
    }

    private static String optionalText(def storage, String key, String defaultValue) {
        String value = text(storage, key)
        return value ? value : defaultValue
    }

    private static int optionalInt(def storage, String key,
                                   int defaultValue, int min, int max) {
        String raw = text(storage, key)
        if (!raw) return defaultValue
        int value
        try {
            value = Integer.parseInt(raw)
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("'${key}' must be a whole number.")
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    "'${key}' must be between ${min} and ${max}.")
        }
        return value
    }

    private static boolean optionalBoolean(def storage, String key, boolean defaultValue) {
        String raw = text(storage, key)
        if (!raw) return defaultValue
        if (raw.equalsIgnoreCase('true')) return true
        if (raw.equalsIgnoreCase('false')) return false
        throw new IllegalArgumentException("'${key}' must be true or false.")
    }

    private static String normalizeCardFolder(String folder) {
        String clean = (folder ?: DEFAULT_CARD_FOLDER).trim() ?: DEFAULT_CARD_FOLDER
        if (clean.startsWith('/') || clean.startsWith('\\') ||
                clean.contains('\\') || clean.split('/').toList().contains('..')) {
            throw new IllegalArgumentException(
                    'The card folder must be a relative folder inside the write destination.')
        }
        return clean
    }
}
