package com.barrymac.freeplane.addons.llm.neuros

import groovy.json.JsonSlurper

/**
 * A domain-neutral catalogue of what the current neurOS cartridge offers.
 *
 * Everything is read from the live cartridge contract, README and handshake.
 * No entity types, surface names, MIME types or door capabilities are baked in.
 */
class KnowledgeCatalog {

    List<KnowledgeSurface> surfaces = []
    List<String> knowledgeTypes = []
    List<ContentType> contentTypes = []
    List<String> doorCapabilities = []
    Map<String, List<String>> statePropertiesBySubtype = [:]
    List<String> warnings = []

    static KnowledgeCatalog read(KnowledgeSource source) {
        if (source == null) throw new IllegalArgumentException('No knowledge source is open.')
        KnowledgeCatalog catalog = new KnowledgeCatalog()

        // The README is the authoritative source for curated surface roots.
        File readme = source.path('README.cartridge')
        if (readme.isFile()) {
            try {
                String text = readme.getText('UTF-8')
                catalog.surfaces = parseSurfaceRoots(text)
                catalog.contentTypes = parseMimeRouting(text)
            } catch (Exception problem) {
                catalog.warnings << 'The cartridge README is not readable right now.'
            }
        }

        // The machine-readable contract supplies the full primitive and MIME
        // vocabulary. It may be warming independently of the README.
        Object contract = readJson(source.path('cartridge-contract'))
        if (contract instanceof Map) {
            List allTypes = contract.primitiveSubtypes?.allSubtypes ?: []
            if (allTypes) {
                catalog.knowledgeTypes = allTypes.collect { it.toString() }.unique().sort()
            }
            Object routing = contract.mimeRouting
            if (routing instanceof Map) {
                catalog.contentTypes = contentFromContract(routing)
            }
            (contract.stateMachines ?: []).each { machine ->
                if (machine?.governedSubtype && machine?.property) {
                    catalog.statePropertiesBySubtype[machine.governedSubtype.toString()] =
                            (machine.property as List).collect { it.toString() }
                }
            }
        } else {
            catalog.warnings << 'The detailed cartridge contract is not ready; ' +
                    'using the live type listing instead.'
        }

        if (!catalog.knowledgeTypes) {
            catalog.knowledgeTypes = listDirectories(source.path('by-tag'))
        }
        if (!catalog.contentTypes) {
            catalog.contentTypes = catalog.contentTypes ?: []
        }

        // Doors are capability-driven. Unknown future capabilities can appear
        // here without any code change; no particular door type is assumed.
        Object handshake = readJson(source.path('handshake'))
        if (handshake instanceof Map) {
            List capabilities = handshake.capabilities ?: []
            catalog.doorCapabilities = capabilities.collect { it.toString() }
                    .findAll { capability ->
                        String normalized = capability.toLowerCase()
                        return normalized.contains('door') || normalized.contains('external-ref')
                    }
                    .unique().sort()
        }

        if (!catalog.surfaces && !catalog.knowledgeTypes && !catalog.contentTypes &&
                !catalog.doorCapabilities) {
            throw new IOException('The knowledge catalog is not ready yet. Try again shortly.')
        }
        return catalog
    }

    /** Parse the README's declared surface roots, not arbitrary directories. */
    static List<KnowledgeSurface> parseSurfaceRoots(String text) {
        List<KnowledgeSurface> surfaces = []
        boolean inSection = false
        (text ?: '').readLines().each { line ->
            if (line.startsWith('## ')) {
                inSection = line.toLowerCase().contains('surface roots')
                return
            }
            if (inSection) {
                def matcher = (line =~ /^\s{2}([^\/#\s]+)\/\s*->\s*(.+)$/)
                if (matcher.find()) {
                    surfaces << new KnowledgeSurface(
                            name: matcher.group(1),
                            description: matcher.group(2).trim())
                }
            }
        }
        return surfaces.unique { it.name }.sort { it.name }
    }

    /** Parse README MIME routing as a fallback when the contract is warming. */
    static List<ContentType> parseMimeRouting(String text) {
        List<ContentType> content = []
        boolean inSection = false
        (text ?: '').readLines().each { line ->
            if (line.startsWith('## ')) {
                inSection = line.toLowerCase().contains('automatic mime routing')
                return
            }
            if (inSection) {
                def matcher = (line =~ /^\s{2}(\S+)\s*->\s*(\S+)\s*$/)
                if (matcher.find()) {
                    content << new ContentType(
                            mime: matcher.group(1), subtype: matcher.group(2), extensions: [])
                }
            }
        }
        return content
    }

    private static Object readJson(File file) {
        if (!file.isFile() || !file.canRead()) return null
        try {
            return new JsonSlurper().parseText(file.getText('UTF-8'))
        } catch (Exception ignored) {
            return null
        }
    }

    private static List<String> listDirectories(File directory) {
        try {
            if (!directory.isDirectory()) return []
            File[] files = directory.listFiles()
            if (files == null) return []
            return files.findAll { it.isDirectory() }.collect { it.name }.unique().sort()
        } catch (Exception ignored) {
            return []
        }
    }

    private static List<ContentType> contentFromContract(Map routing) {
        Map<String, String> mimeToSubtype = routing.mimeToSubtype ?: [:]
        Map<String, String> extensionToMime = routing.fileExtensions ?: [:]
        Map<String, List<String>> extensionsByMime = [:].withDefault { [] }
        extensionToMime.each { extension, mime ->
            extensionsByMime[mime] << extension
        }
        return mimeToSubtype.collect { mime, subtype ->
            new ContentType(mime: mime.toString(), subtype: subtype.toString(),
                    extensions: (extensionsByMime[mime] ?: []).unique().sort())
        }.sort { it.mime }
    }
}

/** A curated listing surface declared by the cartridge README. */
class KnowledgeSurface {
    String name
    String description

    String displayName() { return name }
}

/** One MIME → primitive-subtype interpretation declared by the cartridge. */
class ContentType {
    String mime
    String subtype
    List<String> extensions = []

    String displayName() {
        return subtype && subtype != mime ? "${subtype} (${mime})" : mime
    }

    String description() {
        String suffix = extensions ? "\nExtensions: ${extensions.join(', ')}" : ''
        return "Interprets as: ${subtype}${suffix}"
    }
}
