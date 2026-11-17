package com.barrymac.freeplane.addons.llm.neuros

/**
 * Filters stateful surfaces using the state-machine properties declared by the
 * live cartridge. Ordinary documents without state metadata are not presented
 * as work items. No surface name, lane name or subtype is hardcoded.
 */
class StateMetadataFilter {

    static final int MAX_STATE_READ_BYTES = 8192

    static List<KnowledgeEntry> apply(KnowledgeSource source, String relativePath,
                                      List<KnowledgeEntry> entries) {
        List<String> properties = stateProperties(source, relativePath)
        if (!properties) return entries

        return entries.findAll { entry ->
            Map state = readState(entry.listingFile, properties)
            if (state == null) return false
            entry.state = state.value
            return true
        }
    }

    static List<String> stateProperties(KnowledgeSource source, String relativePath) {
        KnowledgeCatalog catalog = KnowledgeCatalog.read(source)
        String subtype = subtypeFor(relativePath, catalog)
        return subtype ? (catalog.statePropertiesBySubtype[subtype] ?: []) : []
    }

    private static String subtypeFor(String relativePath, KnowledgeCatalog catalog) {
        if (relativePath.startsWith('by-tag/')) {
            return relativePath.split('/')[1]
        }
        String surfaceName = relativePath.split('/')[0]
        def surface = catalog.surfaces.find { it.name == surfaceName }
        if (!surface) return null
        def matcher = (surface.description =~ /primitiveType\s+(\S+)/)
        return matcher.find() ? matcher.group(1) : null
    }

    private static Map readState(File file, List<String> properties) {
        if (!file?.isFile() || !file.canRead()) return null
        try {
            String text
            file.withInputStream { input ->
                byte[] data = new byte[MAX_STATE_READ_BYTES]
                int count = input.read(data, 0, data.length)
                text = count > 0 ? new String(data, 0, count, 'UTF-8') : ''
            }
            for (property in properties) {
                def matcher = (text =~ /(?m)^\s*${java.util.regex.Pattern.quote(property)}:\s*(.+?)\s*$/)
                if (matcher.find()) {
                    return [property: property, value: matcher.group(1).trim()]
                }
            }
        } catch (Exception ignored) {
            return null
        }
        return null
    }
}
