package com.barrymac.freeplane.addons.llm.neuros

/**
 * Filesystem client for a neurOS mount (the by-query/search, ingest-status
 * and workspace-write surfaces of neuros-vfs).
 *
 * All methods take explicit base paths — the class holds no state and no
 * Freeplane objects, so it is directly unit-testable against temp
 * directories that mimic the mount layout.
 */
class NeurosClient {

    /** Marker returned by a mount whose projections are still warming. */
    static final String QUERY_EMPTY = '# QUERY_EMPTY'

    /**
     * Semantic search against a mount: reads <mountPath>/search/<question>.
     *
     * @param mountPath the mount root (e.g. /mnt/neuros/software/<scope>)
     * @param question  free-text question; '/' is neutralised
     * @param limit     maximum number of hits returned (<= 0 → no cap)
     * @return list of hits (possibly empty), never null
     */
    static List<SearchHit> search(String mountPath, String question, int limit = 0) {
        if (!mountPath) throw new IllegalArgumentException('mount path is not configured')
        if (!question?.trim()) throw new IllegalArgumentException('empty search question')

        String safeQuestion = question.trim().replace('/', ' ')
        File resultFile = new File(mountPath, "search/${safeQuestion}")
        if (!resultFile.exists()) {
            throw new IOException(
                    "No search surface at ${resultFile.path} — is '${mountPath}' a live neurOS mount?")
        }

        String text = resultFile.getText('UTF-8').trim()
        if (!text || text.startsWith(QUERY_EMPTY)) {
            return []           // projections warming — honest empty answer
        }

        List<SearchHit> hits = []
        def seen = [] as Set
        text.readLines().each { line ->
            if (!line.trim()) return
            def cols = line.split('\t')
            if (cols.length < 3) return
            def hit = new SearchHit(displayName: cols[0]?.trim(), uuid: cols[1]?.trim(), subtype: cols[2]?.trim())
            if (!hit.uuid || seen.contains(hit.uuid)) return
            seen << hit.uuid
            hits << hit
        }
        if (limit > 0 && hits.size() > limit) {
            hits = hits[0..<limit]
        }
        return hits
    }

    /**
     * Writes a card file into the mount's watched workspace (inotify
     * auto-ingest picks it up within ~1-3s).
     *
     * @param workspacePath the workspace root watched by the mount
     * @param relativeDir  subdirectory (created if missing; '' = root)
     * @param fileName     e.g. '<uuid>-<slug>.card.md'
     * @param content      the card body
     * @return the written file
     */
    static File writeCard(String workspacePath, String relativeDir, String fileName, String content) {
        if (!workspacePath) throw new IllegalArgumentException('workspace path is not configured')
        File dir = relativeDir ? new File(workspacePath, relativeDir) : new File(workspacePath)
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create card directory ${dir.path}")
        }
        File target = new File(dir, fileName)
        target.setText(content, 'UTF-8')
        return target
    }

    /**
     * Polls <mountPath>/ingest-status/<fileName> until the substrate reports
     * the file VISIBLE (indexed + projected) or the timeout expires.
     *
     * @return map [visible: boolean, uuid: String?, status: String]
     */
    static Map waitForIngest(String mountPath, String fileName, int timeoutSeconds = 10) {
        File statusFile = new File(mountPath, "ingest-status/${fileName}")
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

    private static String matchGroup(String text, String regex) {
        def m = (text =~ regex)
        return m.find() ? m.group(1) : null
    }
}
