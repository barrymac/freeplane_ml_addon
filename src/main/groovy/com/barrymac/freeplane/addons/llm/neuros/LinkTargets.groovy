package com.barrymac.freeplane.addons.llm.neuros

import org.freeplane.core.util.LogUtils

/**
 * The file a map node points at, however the link was written.
 *
 * Freeplane exposes `node.link.file` only when it recognised the target as a
 * local file. A map authored elsewhere — or written by a script — can carry a
 * `file://` URI, or just the path as text, and leave `file` null. Every knowledge
 * action then decides the node has no entry and stops, which is exactly the wrong
 * conclusion: the reader can open the link fine.
 *
 * The accessors are tried in order and each failure is logged once, so a link
 * that still cannot be resolved leaves evidence instead of a mystery.
 */
class LinkTargets {

    static File fileFor(def node) {
        if (node == null) return null
        def link
        try {
            link = node.link
        } catch (Exception ignored) {
            return null
        }
        if (link == null) return null

        File direct = attempt { link.file }
        if (direct?.isFile()) return direct

        URI uri = attempt { link.uri }
        File fromUri = uriAsFile(uri)
        if (fromUri != null) return fromUri

        String text = attempt { link.text }
        File fromText = textAsFile(text)
        if (fromText != null) return fromText

        String raw = attempt { link.get() }
        File fromRaw = textAsFile(raw)
        if (fromRaw != null) return fromRaw

        LogUtils.info("LinkTargets: could not resolve the link on '${attempt { node.text }}' " +
                "(file=${direct}, uri=${uri}, text=${text})")
        return null
    }

    /** True when the node carries a link, readable or not. */
    static boolean hasLink(def node) {
        try {
            return node?.link != null
        } catch (Exception ignored) {
            return false
        }
    }

    /** The link as the user sees it, for messages that name the problem. */
    static String describe(def node) {
        try {
            def link = node?.link
            return (link?.text ?: link?.uri?.toString() ?: link?.toString() ?: 'unknown').toString()
        } catch (Exception ignored) {
            return 'unknown'
        }
    }

    /** A file URI, or a URI whose scheme-less form is a path on disk. */
    static File uriAsFile(URI uri) {
        if (uri == null) return null
        try {
            if (uri.scheme == 'file') {
                File file = new File(uri)
                if (file.isFile()) return file
            }
            if (!uri.scheme && uri.path) {
                File file = new File(uri.path)
                if (file.isFile()) return file
            }
        } catch (Exception ignored) {
            return null
        }
        return null
    }

    /** A path written as text, with or without a `file:` prefix. */
    static File textAsFile(String text) {
        String value = (text ?: '').trim()
        if (!value) return null
        try {
            if (value.startsWith('file:')) {
                return uriAsFile(URI.create(value))
            }
            File file = new File(value)
            if (file.isFile()) return file
        } catch (Exception ignored) {
            return null
        }
        return null
    }

    private static <T> T attempt(Closure<T> read) {
        try {
            return read()
        } catch (Exception ignored) {
            return null
        }
    }
}