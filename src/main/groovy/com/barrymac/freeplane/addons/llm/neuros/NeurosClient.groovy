package com.barrymac.freeplane.addons.llm.neuros

/**
 * Filesystem client for ONE scoped neurOS knowledge source.
 *
 * All reads go through KnowledgeSource, so every operation is pinned to the
 * map's current scope. Writes go to an explicitly configured destination; this
 * class never infers a write target from a read location.
 */
class NeurosClient {

    /** Marker returned by a source whose semantic projections are still warming. */
    static final String QUERY_EMPTY = '# QUERY_EMPTY'

    /**
     * Semantic search within the source's current scope.
     *
     * @param source   the map's open knowledge source
     * @param question free-text question; '/' is neutralised
     * @param limit     maximum number of hits returned (<= 0 → no cap)
     * @return list of hits (possibly empty), never null
     */
    static List<SearchHit> search(KnowledgeSource source, String question, int limit = 0) {
        if (source == null) throw new IllegalArgumentException('No knowledge source is open.')
        if (!question?.trim()) throw new IllegalArgumentException('Empty search question.')

        File resultFile = source.searchPath(question)
        String text = readQueryResult(resultFile)
        if (!text || text.startsWith(QUERY_EMPTY)) {
            return []           // projections warming — honest empty answer
        }
        return parseSearchResults(text, limit)
    }

    /**
     * Writes a file into the map's explicitly configured write destination.
     *
     * @param destinationPath the configured destination root
     * @param relativeDir     subdirectory (created if missing; '' = root)
     * @param fileName        e.g. '<uuid>-<slug>.card.md'
     * @param content         file body
     * @return the written file
     */
    static File writeCard(String destinationPath, String relativeDir,
                          String fileName, String content) {
        if (!destinationPath) {
            throw new IllegalArgumentException('No write destination is configured.')
        }
        File dir = relativeDir ? new File(destinationPath, relativeDir) : new File(destinationPath)
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create the write folder '${dir.path}'.")
        }
        File target = new File(dir, fileName)
        target.setText(content, 'UTF-8')
        return target
    }

    /**
     * Confirms a write using whatever availability surface the source exposes.
     * No transport is assumed: if the source provides an ingest status surface
     * it is polled; otherwise the saved file itself is the honest confirmation.
     *
     * @return [saved: boolean, available: boolean, status: String, method: String]
     */
    static Map confirmWrite(KnowledgeSource source, File written,
                            int timeoutSeconds = 15) {
        if (source == null) throw new IllegalArgumentException('No knowledge source is open.')
        boolean saved = written != null && written.isFile() && written.canRead()
        if (!saved) {
            return [saved: false, available: false, status: 'NOT_SAVED', method: 'file']
        }
        if (!source.hasIngestStatusSurface()) {
            return [saved: true, available: false, status: 'SAVED', method: 'file']
        }
        Map ingest = waitForIngest(source, written.name, timeoutSeconds)
        return [saved       : true,
                available   : ingest.visible,
                status      : ingest.status,
                method      : 'availability-surface',
                identifier  : ingest.uuid]
    }

    /**
     * Polls the source's availability surface until a written file is VISIBLE
     * or the timeout expires.
     *
     * @return map [visible: boolean, uuid: String?, status: String]
     */
    static Map waitForIngest(KnowledgeSource source, String fileName, int timeoutSeconds = 10) {
        if (source == null) throw new IllegalArgumentException('No knowledge source is open.')
        File statusFile = source.ingestStatusPath(fileName)
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L)
        Map result = [visible: false, uuid: null, status: 'PENDING']
        while (System.currentTimeMillis() < deadline) {
            if (statusFile.exists()) {
                String text = statusFile.getText('UTF-8')
                result.status = matchGroup(text, /(?m)^status:\s*(\S+).*/) ?: result.status
                result.uuid = matchGroup(text, /(?m)^uuid:\s*([0-9a-fA-F-]+).*/)
                if (text ==~ /(?s)(?m)^status:\s*VISIBLE.*/) {
                    // line-anchored: must be the primary 'status:' line, never
                    // 'visibility_status:'/'projection_status:' (both also end
                    // in 'status: VISIBLE' while the primary is still PENDING)
                    result.visible = true
                    return result
                }
            }
            sleep(500)
        }
        return result
    }

    /**
     * Opens the dynamic query surface directly instead of relying on exists().
     * Some neurOS surfaces create the query answer only when it is opened, and
     * a stat/open can be transiently refused while the substrate is warming or
     * busy. Retry a small fixed number of times, then report honestly.
     */
    private static String readQueryResult(File resultFile) {
        Exception lastProblem = null
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return resultFile.getText('UTF-8').trim()
            } catch (FileNotFoundException notReady) {
                lastProblem = notReady
            } catch (IOException busy) {
                lastProblem = busy
            }
            if (attempt < 2) {
                sleep 400
            }
        }
        throw new IOException(
                'The knowledge source could not answer this question right now. It may still ' +
                        'be preparing its search index or be busy. Try again shortly.',
                lastProblem)
    }

    private static List<SearchHit> parseSearchResults(String text, int limit) {
        List<SearchHit> hits = []
        Set<String> seen = new HashSet<>()
        text.readLines().each { line ->
            if (!line.trim()) return
            def cols = line.split('\t')
            if (cols.length < 3) return
            def hit = new SearchHit(displayName: cols[0]?.trim(),
                    uuid: cols[1]?.trim(), subtype: cols[2]?.trim())
            if (!hit.uuid || seen.contains(hit.uuid)) return
            seen << hit.uuid
            hits << hit
        }
        if (limit > 0 && hits.size() > limit) {
            hits = hits[0..<limit]
        }
        return hits
    }

    private static String matchGroup(String text, String regex) {
        def m = (text =~ regex)
        return m.find() ? m.group(1) : null
    }
}
