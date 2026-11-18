package com.barrymac.freeplane.addons.llm.neuros

/**
 * The file a map node points at, however the link was written.
 *
 * Freeplane exposes `node.link.file` only when it recognised the target as a
 * local file. A map authored elsewhere — or written by a script — can carry a
 * `file://` URI that resolves fine for a reader but leaves `file` null, which
 * made actions claim the node had no entry at all. This resolves the URI as a
 * fallback so the check is about the target, not about the spelling.
 */
class LinkTargets {

    static File fileFor(def node) {
        if (node == null) return null
        def link = null
        try {
            link = node.link
        } catch (Exception ignored) {
            return null
        }
        if (link == null) return null

        try {
            File direct = link.file
            if (direct != null && direct.isFile()) return direct
        } catch (Exception ignored) {
            // Fall through to the URI form.
        }

        try {
            def uri = link.uri
            if (uri == null) return null
            if (uri.scheme == 'file') {
                File fromUri = new File(uri)
                if (fromUri.isFile()) return fromUri
            }
        } catch (Exception ignored) {
            return null
        }
        return null
    }
}