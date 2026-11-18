package com.barrymac.freeplane.addons.llm.neuros

import org.freeplane.core.util.LogUtils

/**
 * Resolves relationship targets to entries inside the map's current scope.
 *
 * Only names are indexed — no file is opened and no identifier is shown. A
 * target resolves by the identifier written in the document, by file name, or
 * by its readable title; anything else is honestly unresolved.
 *
 * Listings go through {@link DirectoryListing}, so a busy knowledge host produces
 * a timeout instead of a silent hang, and the result is cached briefly so repeated
 * actions in one session do not re-walk the mount.
 */
class MeshIndex {

    static final int MAX_ENTRIES = 400
    static final long CACHE_MILLIS = 30_000L

    private static final String UUID =
            '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}'

    private static final java.util.regex.Pattern CARD_NAME =
            ~/^(?:CARD-)?(${UUID})-?(.*?)\.card\.md$/

    private static final java.util.regex.Pattern UUID_NAME =
            ~/^(.*?)(${UUID})-?(.*?)\.card\.md$/

    private static final Map<String, Object[]> CACHE = [:]

    final List<MeshNode> entries = []
    private final Map<String, MeshNode> byId = [:]
    private final Map<String, MeshNode> byTitle = [:]
    private final Map<String, MeshNode> byFile = [:]

    static MeshIndex load(File scopeRoot, int limit = MAX_ENTRIES) {
        MeshIndex index = new MeshIndex()
        if (scopeRoot == null || !scopeRoot.isDirectory()) return index

        String key = "${scopeRoot.absolutePath}|${limit}".toString()
        Object[] cached = CACHE[key]
        if (cached != null && (System.currentTimeMillis() - (long) cached[1]) < CACHE_MILLIS) {
            long age = (System.currentTimeMillis() - (long) cached[1]) / 1000
            LogUtils.info("MeshIndex: reusing a listing from ${age}s ago")
            return (MeshIndex) cached[0]
        }

        List<File> candidates = []
        collect(scopeRoot, candidates)
        ['tasks', 'diagrams'].each { surface ->
            File directory = new File(scopeRoot, surface)
            if (directory.isDirectory()) collect(directory, candidates)
        }

        Set<String> paths = new HashSet<>()
        candidates.findAll { paths.add(it.absolutePath) }
                .sort { it.name }
                .take(Math.max(limit, 1))
                .each { index.add(it) }

        CACHE[key] = [index, System.currentTimeMillis()] as Object[]
        return index
    }

    /** Drop the cached listing, so a changed scope is seen immediately. */
    static void invalidate() {
        CACHE.clear()
    }

    MeshNode find(String target) {
        String key = (target ?: '').trim().replaceAll(/^["']|["']$/, '')
        if (!key) return null
        return byId[key.toLowerCase()] ?:
                byFile[key.toLowerCase()] ?:
                byTitle[slug(key)]
    }

    MeshNode forFile(File file) {
        if (file == null) return null
        return byFile[file.name.toLowerCase()] ?: nodeFor(file)
    }

    void add(File file) {
        MeshNode node = nodeFor(file)
        if (node == null) return
        entries << node
        if (node.id) byId[node.id.toLowerCase()] = node
        byFile[node.file.name.toLowerCase()] = node
        byTitle[slug(node.title)] = node
    }

    /** Build the reader-facing node for one file, without indexing side effects. */
    static MeshNode nodeFor(File file) {
        if (file == null) return null
        String name = file.name
        String id = uuidOf(name)
        return new MeshNode(id: id, title: titleOf(name), file: file)
    }

    private static void collect(File directory, List<File> candidates) {
        DirectoryListing.list(directory, directory.name ?: directory.path).findAll { file ->
            file.isFile() && (file.name.endsWith('.card.md') || file.name.endsWith('.puml'))
        }.each { candidates << it }
    }

    static String uuidOf(String name) {
        def matcher = UUID_NAME.matcher(name ?: '')
        return matcher.matches() ? matcher.group(2) : null
    }

    /** `CARD-<uuid>-bounded-dependency-expansion.card.md` reads as a title. */
    static String titleOf(String name) {
        String base = (name ?: '').replaceAll(/\.card\.md$/, '').replaceAll(/\.puml$/, '')
        def card = CARD_NAME.matcher(name ?: '')
        if (card.matches()) {
            base = card.group(2) ?: ''
        } else {
            def uuid = UUID_NAME.matcher(name ?: '')
            if (uuid.matches()) base = [uuid.group(1), uuid.group(3)].findAll { it }.join('')
        }
        String words = base.replace('-', ' ').replace('_', ' ').trim()
        if (!words) return 'Untitled entry'
        return words[0].toUpperCase() + words.substring(1)
    }

    private static String slug(String value) {
        return (value ?: '').toLowerCase().replaceAll(/[^a-z0-9]+/, ' ').trim()
    }
}