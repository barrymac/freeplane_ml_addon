package com.barrymac.freeplane.addons.llm.neuros

import org.freeplane.core.util.LogUtils

/**
 * Discovers where the substrate's watcher is actually listening.
 *
 * The watched workspace is not the map's directory and not the mount: it is
 * the local path the substrate's inotify watcher monitors, declared on the
 * mount's own watcher-status. A card file written anywhere else is a local
 * file; written here, it becomes part of the knowledge graph.
 */
class WatchedWorkspace {

    /** Read the watched path from the mount's watcher-status. */
    static File discover(File scopeRoot) {
        if (scopeRoot == null || !scopeRoot.isDirectory()) return null
        try {
            File status = new File(scopeRoot, 'watcher-status')
            if (!status.isFile() || !status.canRead()) return null
            def matcher = (status.getText('UTF-8') =~ /(?m)^watching:\s*(.+)$/)
            if (!matcher.find()) return null
            String path = matcher.group(1).trim()
            File watched = new File(path)
            return watched.isDirectory() ? watched : null
        } catch (Exception problem) {
            LogUtils.warn("Could not read the watched workspace: ${problem.message}")
            return null
        }
    }

    /** True when a file can be created inside the watched workspace. */
    static boolean isWritable(File watched) {
        if (watched == null || !watched.isDirectory()) return false
        try {
            File probe = new File(watched, '.write-probe')
            boolean created = probe.createNewFile()
            if (created) probe.delete()
            return created
        } catch (Exception ignored) {
            return false
        }
    }
}