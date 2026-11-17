package com.barrymac.freeplane.addons.llm.neuros

/**
 * Bounded, read-only listing of one neurOS surface. No recursive traversal and
 * no semantic query. A page is a view over the entries the surface exposes;
 * truncation is reported honestly rather than pretending a complete listing.
 */
class KnowledgeListing {

    static final int MAX_PAGE_SIZE = 100

    static KnowledgePage list(KnowledgeSource source, String relativePath,
                              int offset, int limit, String subtype = '') {
        if (source == null) throw new IllegalArgumentException('No knowledge source is open.')
        if (offset < 0) throw new IllegalArgumentException('The listing offset cannot be negative.')
        int pageSize = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE)
        File directory = source.path(relativePath)

        File[] files
        try {
            files = directory.listFiles()
        } catch (Exception problem) {
            throw new IOException('This knowledge listing is not ready yet. Try again shortly.', problem)
        }
        if (files == null) {
            throw new IOException('This knowledge listing is not ready yet. Try again shortly.')
        }

        // Do NOT stat every entry: on dynamic/network surfaces that turns one
        // bounded page into hundreds or thousands of remote calls. neurOS
        // listing surfaces already yield entries, so only the status file is
        // excluded here.
        List<File> sorted = files.findAll { file ->
            file.name != '.listing-status'
        }.sort { it.name }

        List<KnowledgeEntry> entries = sorted.drop(offset).take(pageSize).collect { file ->
            toEntry(file, subtype)
        }
        int nextOffset = offset + entries.size()
        boolean hasMore = sorted.size() > nextOffset

        Map truncation = truncationStatus(directory)
        return new KnowledgePage(
                entries: entries,
                nextOffset: nextOffset,
                hasMore: hasMore,
                sourceTruncated: truncation.truncated,
                totalAvailable: truncation.totalAvailable)
    }

    private static KnowledgeEntry toEntry(File file, String subtype) {
        String title = file.name
        String uuid = null
        def matcher = (title =~ /^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})-?(.+)$/)
        if (matcher.find()) {
            uuid = matcher.group(1)
            title = matcher.group(2)
            // UUID-only files have no human slug: use the declared subtype
            // rather than showing an identifier or a bare extension.
            if (title.startsWith('.')) title = ''
        }
        return new KnowledgeEntry(
                displayName: title ?: (subtype ?: 'Knowledge entry'),
                uuid: uuid,
                subtype: subtype ?: '',
                listingFile: file)
    }

    private static Map truncationStatus(File directory) {
        Map result = [truncated: false, totalAvailable: null]
        File status = new File(directory, '.listing-status')
        if (!status.isFile() || !status.canRead()) return result
        try {
            String text = status.getText('UTF-8')
            result.truncated = (text =~ /(?m)^truncated:\s*true\s*$/).find()
            def total = (text =~ /(?m)^total_available:\s*(\d+)\s*$/)
            if (total.find()) result.totalAvailable = total.group(1) as int
        } catch (Exception ignored) {
            // A missing status read does not turn a successful listing into a failure.
        }
        return result
    }
}

/** One entry in a browse listing. The identifier is internal plumbing only. */
class KnowledgeEntry {
    String displayName
    String uuid
    String subtype
    String state
    File listingFile

    File sourceFile(KnowledgeSource source) {
        // Prefer the actual listing file: it is the entry the user browsed and
        // is available even when a canonical by-id projection is missing or
        // belongs to another scope. Fall back to by-id only when necessary.
        if (listingFile?.isFile() && listingFile.canRead()) {
            return listingFile
        }
        return uuid ? source.byIdPath(uuid) : listingFile
    }
}

/** One page plus explicit truncation information. */
class KnowledgePage {
    List<KnowledgeEntry> entries = []
    int nextOffset
    boolean hasMore
    boolean sourceTruncated
    Integer totalAvailable
}
