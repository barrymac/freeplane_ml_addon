package com.barrymac.freeplane.addons.llm.neuros

import java.nio.file.Files

/**
 * Discovers the neurOS context from the location of the current Freeplane map.
 *
 * This replaces location/scope configuration. A map is neurOS-enabled when:
 *   - it lives inside a neurOS knowledge surface (README.cartridge is present),
 *   - or an ancestor workspace contains a valid *_KNOWLEDGE link to one.
 *
 * A map in a plain directory has no neurOS connection and the scripts simply
 * explain that neurOS is not enabled for that map. There is no manual mount or
 * scope picker, and no guessed defaults.
 */
class KnowledgeContext {

    final KnowledgeSource source
    final String writeDestination
    final String reason

    private KnowledgeContext(KnowledgeSource source, String writeDestination, String reason) {
        this.source = source
        this.writeDestination = writeDestination
        this.reason = reason
    }

    boolean isEnabled() {
        return source != null
    }

    boolean canWrite() {
        return isEnabled() && writeDestination?.trim()
    }

    String description() {
        return isEnabled() ? source.description() : reason
    }

    /** Discover the current map's context. */
    static KnowledgeContext discover(File mapFile) {
        if (mapFile == null) {
            return disabled('neurOS is not enabled for this map. Save it in a neurOS-enabled ' +
                    'workspace or a neurOS knowledge location first.')
        }
        File directory = mapFile.absoluteFile.parentFile
        if (directory == null) {
            return disabled('neurOS is not enabled for this map because its location is unknown.')
        }

        boolean unavailableLinkSeen = false
        while (directory != null) {
            if (isKnowledgeSurface(directory)) {
                return enabledSurface(directory)
            }

            List<File> links = knowledgeLinks(directory)
            List<File> validLinks = links.findAll { link ->
                try {
                    return isKnowledgeSurface(link.canonicalFile)
                } catch (Exception ignored) {
                    return false
                }
            }
            if (validLinks.size() > 1) {
                return disabled('This map workspace contains more than one neurOS knowledge ' +
                        'source. neurOS cannot choose between them for you.')
            }
            if (validLinks.size() == 1) {
                File target = validLinks[0].canonicalFile
                String scope = readScope(target)
                if (scope) {
                    return enabled(KnowledgeSource.open(target.path, scope), directory.path)
                }
                unavailableLinkSeen = true
            } else if (links) {
                unavailableLinkSeen = true
            }
            directory = directory.parentFile
        }

        if (unavailableLinkSeen) {
            return disabled('This map is in a neurOS-enabled workspace, but its knowledge ' +
                    'source is not ready yet.')
        }
        return disabled('neurOS is not enabled for this map. It is not in a neurOS-enabled ' +
                'workspace or a neurOS knowledge location.')
    }

    private static KnowledgeContext enabledSurface(File directory) {
        String scope = readScope(directory)
        if (!scope) {
            return disabled('The neurOS knowledge source at this map location is not ready yet.')
        }
        // A global single-surface scope is a writable network workspace. A
        // scoped read surface (for example a local knowledge mount) is not
        // automatically a write destination; watched-workspace links below
        // provide the destination for that case.
        String destination = (hasGlobalScopesAncestor(directory) || hasNetworkMount(directory)) ?
                directory.path : null
        return enabled(KnowledgeSource.open(directory.path, scope), destination)
    }

    private static KnowledgeContext enabled(KnowledgeSource source, String writeDestination) {
        return new KnowledgeContext(source, writeDestination, null)
    }

    private static KnowledgeContext disabled(String reason) {
        return new KnowledgeContext(null, null, reason)
    }

    /** The cartridge README is the explicit enablement marker. */
    private static boolean isKnowledgeSurface(File directory) {
        return directory.isDirectory() &&
                new File(directory, 'README.cartridge').isFile() &&
                new File(directory, 'by-query').isDirectory()
    }

    /** Generic workspace discovery, independent of mount/ontology naming. */
    private static List<File> knowledgeLinks(File directory) {
        if (!directory.isDirectory()) return []
        File[] files = directory.listFiles()
        if (files == null) return []
        return files.findAll { file ->
            file.name.endsWith('_KNOWLEDGE') && Files.isSymbolicLink(file.toPath())
        }
    }

    private static String readScope(File surfaceRoot) {
        File status = new File(surfaceRoot, 'watcher-status')
        if (!status.isFile() || !status.canRead()) return null
        try {
            def matcher = (status.getText('UTF-8') =~ /(?m)^scope:\s*(\S+)\s*$/)
            return matcher.find() ? matcher.group(1) : null
        } catch (Exception ignored) {
            return null
        }
    }

    /**
     * Detect a writable network knowledge surface without making any domain or
     * scope-name assumptions. The mount table is a capability probe, never UI
     * text. Scoped FUSE read surfaces remain read-only for saving purposes.
     */
    private static boolean hasNetworkMount(File directory) {
        File mountInfo = new File('/proc/self/mountinfo')
        if (!mountInfo.isFile() || !mountInfo.canRead()) return false
        try {
            return isNetworkSurface(mountInfo.getText('UTF-8'), directory)
        } catch (Exception ignored) {
            return false
        }
    }

    /** Package-visible for deterministic tests. */
    static boolean isNetworkSurface(String mountInfoText, File directory) {
        if (!mountInfoText || directory == null) return false
        String path = directory.absoluteFile.path
        String bestType = null
        int bestLength = -1
        mountInfoText.readLines().each { line ->
            List<String> fields = line.split(' ').toList()
            int separator = fields.indexOf('-')
            if (separator < 2 || fields.size() <= separator + 1) return
            String mountPoint = unescapeMountPath(fields[4])
            if (path == mountPoint || path.startsWith(mountPoint + '/') ||
                    (mountPoint == '/' && path.startsWith('/'))) {
                if (mountPoint.length() > bestLength) {
                    bestLength = mountPoint.length()
                    bestType = fields[separator + 1]
                }
            }
        }
        return bestType == '9p'
    }

    private static String unescapeMountPath(String value) {
        return value.replace('\\040', ' ')
                .replace('\\011', '\t')
                .replace('\\012', '\n')
                .replace('\\134', '\\')
    }

    /** Universal global layout: .../scopes/<scope>/... */
    private static boolean hasGlobalScopesAncestor(File directory) {
        File current = directory.absoluteFile
        while (current != null) {
            if (current.name == 'scopes') return true
            current = current.parentFile
        }
        return false
    }
}
