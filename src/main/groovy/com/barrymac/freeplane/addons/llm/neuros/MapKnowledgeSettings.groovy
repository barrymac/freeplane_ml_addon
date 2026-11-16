package com.barrymac.freeplane.addons.llm.neuros

/**
 * neurOS knowledge settings for ONE Freeplane map.
 *
 * Source, scope, write destination and resource limits are stored in the map's
 * own storage — never in a global default. A new map therefore starts
 * unconnected and must be connected deliberately. No value is guessed from the
 * previous map, the current mount layout, or a hardcoded project.
 *
 * Numeric defaults are intentionally conservative and bounded so a map cannot
 * turn one user action into an unbounded set of substrate reads.
 */
class MapKnowledgeSettings {

    static final String LOCATION_KEY = 'neuros.knowledge_location'
    static final String SCOPE_KEY = 'neuros.knowledge_scope'
    static final String WRITE_DESTINATION_KEY = 'neuros.write_destination'
    static final String CARD_FOLDER_KEY = 'neuros.card_folder'
    static final String RESULTS_LIMIT_KEY = 'neuros.results_limit'
    static final String WRITE_WAIT_KEY = 'neuros.write_wait_seconds'
    static final String USE_CONTEXT_KEY = 'neuros.use_map_context'
    static final String ANCESTOR_LIMIT_KEY = 'neuros.context.ancestor_limit'
    static final String CHILD_LIMIT_KEY = 'neuros.context.child_limit'
    static final String NOTE_LIMIT_KEY = 'neuros.context.note_limit'
    static final String CONTEXT_LIMIT_KEY = 'neuros.context.total_limit'

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
    static final String DEFAULT_CARD_FOLDER = 'neuros-cards'

    String knowledgeLocation = ''
    String scope = ''
    String writeDestination = ''
    String cardFolder = DEFAULT_CARD_FOLDER
    int resultsLimit = DEFAULT_RESULTS_LIMIT
    int writeWaitSeconds = DEFAULT_WRITE_WAIT_SECONDS
    boolean useContext = DEFAULT_USE_CONTEXT
    int ancestorLimit = DEFAULT_ANCESTOR_LIMIT
    int childLimit = DEFAULT_CHILD_LIMIT
    int noteLimit = DEFAULT_NOTE_LIMIT
    int contextLimit = DEFAULT_CONTEXT_LIMIT

    static MapKnowledgeSettings load(def storage) {
        if (storage == null) {
            throw new IllegalArgumentException('This map has no storage for neurOS knowledge settings.')
        }
        return new MapKnowledgeSettings(
                knowledgeLocation: text(storage, LOCATION_KEY),
                scope: text(storage, SCOPE_KEY),
                writeDestination: text(storage, WRITE_DESTINATION_KEY),
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
                        DEFAULT_CONTEXT_LIMIT, 200, MAX_CONTEXT_LIMIT)
        )
    }

    static void save(def storage, MapKnowledgeSettings settings) {
        if (storage == null) {
            throw new IllegalArgumentException('This map has no storage for neurOS knowledge settings.')
        }
        validate(settings)
        storage[LOCATION_KEY] = settings.knowledgeLocation
        storage[SCOPE_KEY] = settings.scope
        storage[WRITE_DESTINATION_KEY] = settings.writeDestination ?: null
        storage[CARD_FOLDER_KEY] = settings.cardFolder
        storage[RESULTS_LIMIT_KEY] = Integer.toString(settings.resultsLimit)
        storage[WRITE_WAIT_KEY] = Integer.toString(settings.writeWaitSeconds)
        storage[USE_CONTEXT_KEY] = Boolean.toString(settings.useContext)
        storage[ANCESTOR_LIMIT_KEY] = Integer.toString(settings.ancestorLimit)
        storage[CHILD_LIMIT_KEY] = Integer.toString(settings.childLimit)
        storage[NOTE_LIMIT_KEY] = Integer.toString(settings.noteLimit)
        storage[CONTEXT_LIMIT_KEY] = Integer.toString(settings.contextLimit)
    }

    static void validate(MapKnowledgeSettings settings) {
        if (!settings.knowledgeLocation?.trim()) {
            throw new IllegalArgumentException('Choose a knowledge location for this map.')
        }
        if (!settings.scope?.trim()) {
            throw new IllegalArgumentException('Choose the knowledge scope for this map.')
        }
        if (settings.writeDestination?.trim()) {
            File destination = new File(settings.writeDestination.trim())
            if (!destination.isDirectory() || !destination.canWrite()) {
                throw new IOException(
                        "The write destination is not a writable directory: '${destination.path}'.")
            }
        }
        settings.cardFolder = normalizeCardFolder(settings.cardFolder)
        if (settings.resultsLimit < 1 || settings.resultsLimit > MAX_RESULTS_LIMIT) {
            throw new IllegalArgumentException(
                    "Search results must be between 1 and ${MAX_RESULTS_LIMIT}.")
        }
        if (settings.writeWaitSeconds < 1 || settings.writeWaitSeconds > MAX_WRITE_WAIT_SECONDS) {
            throw new IllegalArgumentException(
                    "The confirmation wait must be between 1 and ${MAX_WRITE_WAIT_SECONDS} seconds.")
        }
        if (settings.ancestorLimit < 0 || settings.ancestorLimit > MAX_ANCESTOR_LIMIT) {
            throw new IllegalArgumentException(
                    "The ancestor context limit must be between 0 and ${MAX_ANCESTOR_LIMIT}.")
        }
        if (settings.childLimit < 0 || settings.childLimit > MAX_CHILD_LIMIT) {
            throw new IllegalArgumentException(
                    "The child context limit must be between 0 and ${MAX_CHILD_LIMIT}.")
        }
        if (settings.noteLimit < 0 || settings.noteLimit > MAX_NOTE_LIMIT) {
            throw new IllegalArgumentException(
                    "The note context limit must be between 0 and ${MAX_NOTE_LIMIT} characters.")
        }
        if (settings.contextLimit < 200 || settings.contextLimit > MAX_CONTEXT_LIMIT) {
            throw new IllegalArgumentException(
                    "The total context limit must be between 200 and ${MAX_CONTEXT_LIMIT} characters.")
        }
    }

    /** Loud refusal when read features are not configured for this map. */
    String missingForRead() {
        List missing = []
        if (!knowledgeLocation?.trim()) missing << 'knowledge location'
        if (!scope?.trim()) missing << 'knowledge scope'
        if (missing) {
            return "This map is not connected to neurOS knowledge yet " +
                    "(${missing.join(' and ')} missing).\n" +
                    "Open 'LLM > Configure neurOS Knowledge' and connect it first."
        }
        return null
    }

    /** Loud refusal when write features are not configured for this map. */
    String missingForWrite() {
        if (!writeDestination?.trim()) {
            return 'This map has no neurOS write destination.\n' +
                    "Open 'LLM > Configure neurOS Knowledge' and choose a write destination " +
                    'to enable saving branches.'
        }
        return null
    }

    KnowledgeSource openSource() {
        String readProblem = missingForRead()
        if (readProblem) throw new IllegalArgumentException(readProblem)
        return KnowledgeSource.open(knowledgeLocation, scope)
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
