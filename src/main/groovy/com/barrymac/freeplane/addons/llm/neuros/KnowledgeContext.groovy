package com.barrymac.freeplane.addons.llm.neuros

import com.barrymac.freeplane.addons.llm.ConfigKeys

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

    /** Settings key designating which *_KNOWLEDGE link this plugin binds to. */
    static final String LINK_KEY = 'llm.neuros.knowledgeLink'

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
        File mapDirectory = mapFile.absoluteFile.parentFile
        while (directory != null) {
            if (isKnowledgeSurface(directory)) {
                return enabledSurface(directory, mapDirectory)
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
                // Multiple cartridges in one workspace is the normal neurOS
                // shape; the operator designates which one this plugin binds
                // to. No guessing: an absent or wrong designation refuses
                // loudly and names the choices.
                String designated = ConfigKeys.read(LINK_KEY, '')?.trim()
                File chosen = validLinks.find { it.name == designated }
                if (chosen != null) {
                    KnowledgeContext fromLink = contextFromLink(chosen, directory)
                    if (fromLink != null) return fromLink
                    unavailableLinkSeen = true
                } else {
                    String names = validLinks.collect { it.name }.sort().join(', ')
                    return disabled(designated ?
                            "The designated knowledge link '${designated}' is not one of the " +
                                    "sources in this workspace (${names})." :
                            'This map workspace has more than one neurOS knowledge source ' +
                                    "(${names}). Pick one in the Chat GPT Communicator " +
                                    'settings, neurOS section: knowledge link.')
                }
            }
            if (validLinks.size() == 1) {
                KnowledgeContext fromLink = contextFromLink(validLinks[0], directory)
                if (fromLink != null) return fromLink
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

    /**
     * Resolve one valid *_KNOWLEDGE link into a context: reads through its
     * surface root, writes where that mount's layout watches. Returns null
     * when the link is valid but its scope is not readable yet.
     */
    private static KnowledgeContext contextFromLink(File link, File mapWorkspace) {
        File target = link.canonicalFile
        File readSurfaces = readRoot(target)
        String scope = readScope(readSurfaces)
        if (!scope) return null
        return enabled(KnowledgeSource.open(readSurfaces.path, scope),
                writeDestinationFor(target, mapWorkspace))
    }

    private static KnowledgeContext enabledSurface(File directory, File mapDirectory) {
        File readSurfaces = readRoot(directory)
        String scope = readScope(readSurfaces)
        if (!scope) {
            return disabled('The neurOS knowledge source at this map location is not ready yet.')
        }
        // A map saved directly on a knowledge surface has no safe save target
        // unless it sits inside the mount's watched workspace, the one
        // directory the substrate ingests from. Then the map's own directory
        // is the write destination; anywhere else on the surface is not.
        File watched = new File(directory, 'workspace')
        String writeDestination = null
        try {
            if (watched.isDirectory() &&
                    mapDirectory.absoluteFile.canonicalPath == watched.canonicalPath) {
                writeDestination = mapDirectory.path
            }
        } catch (Exception ignored) {
            writeDestination = null
        }
        return enabled(KnowledgeSource.open(readSurfaces.path, scope), writeDestination)
    }

    /**
     * Where confirmed card files are written so the substrate ingests them:
     * the mount's watched workspace when the layout provides one (reads and
     * writes separated, the watcher monitors that directory server-side),
     * otherwise the map's own workspace (a substrate watching the local
     * directory the map lives in).
     */
    private static String writeDestinationFor(File mountRoot, File mapWorkspace) {
        File watched = new File(mountRoot, 'workspace')
        if (watched.isDirectory()) return watched.path
        return mapWorkspace?.path
    }

    private static KnowledgeContext enabled(KnowledgeSource source, String writeDestination) {
        return new KnowledgeContext(source, writeDestination, null)
    }

    private static KnowledgeContext disabled(String reason) {
        return new KnowledgeContext(null, null, reason)
    }

    /** The cartridge README or contract is the explicit enablement marker. */
    private static boolean isKnowledgeSurface(File directory) {
        return readRoot(directory) != null
    }

    /**
     * The read-surface root for a mount directory: the directory itself when
     * the surfaces sit at its root, or its KNOWLEDGE subdirectory when the
     * layout separates the read surfaces from the watched workspace.
     */
    private static File readRoot(File directory) {
        if (directory == null || !directory.isDirectory()) return null
        if (hasReadSurfaces(directory)) return directory
        File nested = new File(directory, 'KNOWLEDGE')
        if (hasReadSurfaces(nested)) return nested
        return null
    }

    private static boolean hasReadSurfaces(File directory) {
        if (!directory.isDirectory()) return false
        boolean cartridgePresent = new File(directory, 'README.cartridge').isFile() ||
                new File(directory, 'cartridge-contract').isFile()
        return cartridgePresent && new File(directory, 'by-query').isDirectory()
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
        if (status.isFile() && status.canRead()) {
            try {
                def matcher = (status.getText('UTF-8') =~ /(?m)^scope:\s*(\S+)\s*$/)
                if (matcher.find()) return matcher.group(1)
            } catch (Exception ignored) {
                // Fall through to the mount's declared attachment name.
            }
        }
        return scopeFromMount(surfaceRoot)
    }

    /** Fallback while watcher-status is absent or not yet readable. */
    private static String scopeFromMount(File directory) {
        File mountInfo = new File('/proc/self/mountinfo')
        if (!mountInfo.isFile() || !mountInfo.canRead()) return null
        try {
            return scopeFromMountInfo(mountInfo.getText('UTF-8'), directory)
        } catch (Exception ignored) {
            return null
        }
    }

    /** Package-visible for deterministic mount-option tests. */
    static String scopeFromMountInfo(String mountInfoText, File directory) {
        if (!mountInfoText || directory == null) return null
        String path = directory.absoluteFile.path
        String bestScope = null
        int bestLength = -1
        mountInfoText.readLines().each { line ->
            List<String> fields = line.split(' ').toList()
            int separator = fields.indexOf('-')
            if (separator < 2 || fields.size() <= separator + 3) return
            String mountPoint = unescapeMountPath(fields[4])
            if (path == mountPoint || path.startsWith(mountPoint + '/') ||
                    (mountPoint == '/' && path.startsWith('/'))) {
                if (mountPoint.length() > bestLength) {
                    bestLength = mountPoint.length()
                    String options = fields[separator + 3]
                    def matcher = (options =~ /(?:^|,)aname=([^,]+)/)
                    bestScope = matcher.find() ? matcher.group(1) : null
                }
            }
        }
        return bestScope
    }

    private static String unescapeMountPath(String value) {
        return value.replace('\\040', ' ')
                .replace('\\011', '\t')
                .replace('\\012', '\n')
                .replace('\\134', '\\')
    }
}
