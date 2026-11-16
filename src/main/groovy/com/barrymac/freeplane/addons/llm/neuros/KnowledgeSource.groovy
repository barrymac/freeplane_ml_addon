package com.barrymac.freeplane.addons.llm.neuros

/**
 * A neurOS knowledge source for ONE explicitly chosen scope.
 *
 * The class is deliberately transport-neutral: any filesystem path that exposes
 * the standard neurOS read surfaces works, whether it is served by a local
 * daemon or a network filesystem. The add-on never mentions the transport to
 * the user and never infers one.
 *
 * Two layouts are supported:
 *   - a scoped surface: <location>/by-query/...
 *   - a global surface:  <location>/scopes/<explicit-scope>/by-query/...
 *
 * A global surface NEVER selects a scope on the caller's behalf: the scope is
 * required and validated. Every read is resolved through this class so the
 * map's current-scope boundary is enforced in one place.
 */
class KnowledgeSource {

    /** Universal neurOS surface names (protocol contract, not domain data). */
    private static final String SCOPES_DIR = 'scopes'
    private static final String QUERY_DIR = 'by-query'
    private static final String BY_ID_DIR = 'by-id'
    private static final String INGEST_STATUS_DIR = 'ingest-status'

    final String location
    final String scope
    final boolean global

    private final File surfaceRoot

    private KnowledgeSource(String location, String scope,
                            boolean global, File surfaceRoot) {
        this.location = location
        this.scope = scope
        this.global = global
        this.surfaceRoot = surfaceRoot
    }

    /**
     * Opens a knowledge source and pins it to one scope.
     *
     * @param location filesystem path of a scoped or global neurOS surface
     * @param scope    the map's explicitly chosen scope (never guessed)
     */
    static KnowledgeSource open(String location, String scope) {
        String cleanLocation = (location ?: '').trim()
        String cleanScope = validateScope(scope)
        if (!cleanLocation) {
            throw new IllegalArgumentException('Knowledge location is not set.')
        }

        File candidate = new File(cleanLocation)
        if (!candidate.isDirectory() || !candidate.canRead()) {
            throw new IOException(
                    "The knowledge location is not a readable directory: '${cleanLocation}'.")
        }

        File scopesDir = new File(candidate, SCOPES_DIR)
        if (scopesDir.isDirectory()) {
            File scoped = new File(scopesDir, cleanScope)
            if (!scoped.isDirectory() || !scoped.canRead()) {
                throw new IOException(
                        "Knowledge scope '${cleanScope}' is not available at this location.")
            }
            requireQuerySurface(scoped)
            return new KnowledgeSource(cleanLocation, cleanScope, true, scoped)
        }

        requireQuerySurface(candidate)
        return new KnowledgeSource(cleanLocation, cleanScope, false, candidate)
    }

    /** Current-scope path for a relative surface path. */
    File path(String relativePath) {
        return new File(surfaceRoot, relativePath ?: '')
    }

    /** Semantic-search path; '/' is neutralised for filesystem surfaces. */
    File searchPath(String question) {
        if (!question?.trim()) {
            throw new IllegalArgumentException('Empty search question.')
        }
        String safeQuestion = question.trim().replace('/', ' ')
        return path("${QUERY_DIR}/${safeQuestion}")
    }

    /** Source-material path for an internal identifier. */
    File byIdPath(String identifier) {
        String cleanId = (identifier ?: '').trim()
        if (!cleanId || cleanId.contains('/') || cleanId.contains('\\')) {
            throw new IllegalArgumentException('Invalid internal source identifier.')
        }
        return path("${BY_ID_DIR}/${cleanId}")
    }

    /** Availability surface for a written file, if this source exposes one. */
    File ingestStatusPath(String fileName) {
        String cleanName = (fileName ?: '').trim()
        if (!cleanName || cleanName.contains('/') || cleanName.contains('\\')) {
            throw new IllegalArgumentException('Invalid file name.')
        }
        return path("${INGEST_STATUS_DIR}/${cleanName}")
    }

    boolean hasIngestStatusSurface() {
        File dir = path(INGEST_STATUS_DIR)
        return dir.isDirectory() && dir.canRead()
    }

    /**
     * Central scope boundary. Same-scope access proceeds; any other scope
     * requires an operation-specific confirmation, and is only possible on a
     * global source. No caller can silently widen the map's scope.
     */
    File pathForScope(String targetScope, String relativePath,
                      boolean crossScopeConfirmed = false) {
        String cleanTarget = validateScope(targetScope)
        if (cleanTarget == scope) {
            return path(relativePath)
        }
        if (!crossScopeConfirmed) {
            throw new SecurityException(
                    "This operation is limited to the map's current knowledge scope " +
                            "('${scope}'). Confirm access to '${cleanTarget}' first.")
        }
        if (!global) {
            throw new UnsupportedOperationException(
                    "This knowledge location does not provide access to scope '${cleanTarget}'.")
        }
        File targetRoot = new File(new File(new File(location), SCOPES_DIR), cleanTarget)
        if (!targetRoot.isDirectory() || !targetRoot.canRead()) {
            throw new IOException(
                    "Knowledge scope '${cleanTarget}' is not available at this location.")
        }
        return new File(targetRoot, relativePath ?: '')
    }

    /** Human-readable list of scopes; a scoped source only reports itself. */
    List<String> availableScopes() {
        if (!global) {
            return [scope]
        }
        File scopesDir = new File(new File(location), SCOPES_DIR)
        if (!scopesDir.isDirectory()) return []
        return scopesDir.listFiles()
                .findAll { it.isDirectory() && new File(it, QUERY_DIR).isDirectory() }
                .collect { it.name }
                .sort()
    }

    String description() {
        return "Knowledge scope: ${scope}"
    }

    private static void requireQuerySurface(File root) {
        File queryDir = new File(root, QUERY_DIR)
        if (!queryDir.isDirectory() || !queryDir.canRead()) {
            throw new IOException(
                    "The location '${root.path}' does not provide a readable " +
                            'neurOS knowledge search surface.')
        }
    }

    private static String validateScope(String scope) {
        String clean = (scope ?: '').trim()
        if (!clean || clean == '.' || clean == '..' ||
                clean.contains('/') || clean.contains('\\')) {
            throw new IllegalArgumentException(
                    'Choose the knowledge scope for this map (it is never selected for you).')
        }
        return clean
    }
}
