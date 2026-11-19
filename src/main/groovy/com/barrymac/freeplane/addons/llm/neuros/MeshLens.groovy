package com.barrymac.freeplane.addons.llm.neuros

/**
 * Which relationship families the map is currently looking through.
 *
 * A lens is a set of families — dependency, blocking, structure, tracking,
 * reference, code, governance, contradiction. The selection is remembered per
 * installation, so drawing and proposing both respect it without asking again.
 */
class MeshLens {

    static final String CONFIG_KEY = 'llm.neuros.lenses'

    /** Family -> the wording a reader sees in the picker. */
    static final Map<String, String> FAMILIES = [
            (MeshVocabulary.DEPENDENCY)   : 'Dependency (depends on, blocked by)',
            (MeshVocabulary.BLOCKING)     : 'Blocking (blocks)',
            (MeshVocabulary.STRUCTURE)    : 'Structure (contains, part of, child of)',
            (MeshVocabulary.TRACKING)     : 'Tracking (tracks, reconciles)',
            (MeshVocabulary.REFERENCE)    : 'Reference (references, depicts, documents)',
            (MeshVocabulary.CODE)         : 'Code (calls, extends, implements, tested by)',
            (MeshVocabulary.GOVERNANCE)   : 'Governance (tenets, rules, violations, requires)',
            (MeshVocabulary.CONTRADICTION): 'Contradiction (contradicts, disputes, overrules)'
    ] as LinkedHashMap

    static Set<String> allFamilies() {
        return families().keySet()
    }

    /** Every family the vocabulary currently knows, including runtime additions. */
    static Map<String, String> families() {
        Map<String, String> result = new LinkedHashMap<>(FAMILIES)
        MeshVocabulary.DECLARED.each { marker, pair ->
            String family = pair[1]
            if (!result.containsKey(family)) {
                result[family] = family[0].toUpperCase() + family.substring(1)
            }
        }
        return result
    }

    /** Map storage and the global config expose slightly different accessors. */
    private static String stored(def config) {
        if (config == null) return null
        try {
            return ((String) config.getProperty((String) CONFIG_KEY, (String) null) ?: '').toString()
        } catch (MissingMethodException ignored) {
            try {
                return (config.getProperty(CONFIG_KEY) ?: '').toString()
            } catch (Exception ignoredAgain) {
                return null
            }
        }
    }

    private static void store(def config, String value) {
        if (config == null) return
        try {
            config.setProperty(CONFIG_KEY, value)
        } catch (Exception problem) {
            org.freeplane.core.util.LogUtils.warn("Could not save the relationship lens: ${problem.message}")
        }
    }

    /** The configured lens, defaulting to every family. */
    static Set<String> read(def config) {
        String stored = stored(config)
        if (!stored?.trim()) return allFamilies()
        Set<String> chosen = stored.split(',').collect { it.trim().toLowerCase() }.findAll { it }
                .findAll { families().containsKey(it) } as Set
        return chosen ?: allFamilies()
    }

    static void write(def config, Collection<String> selected) {
        Set<String> chosen = (selected ?: []).findAll { families().containsKey(it) } as Set
        store(config, (chosen ?: allFamilies()).sort().join(','))
    }

    /** Keep only edges inside the lens. Edges with an unknown family are kept. */
    static List<MeshEdge> filter(List<MeshEdge> edges, Set<String> selected) {
        if (!edges) return []
        if (selected == null || selected.isEmpty()) return edges
        return edges.findAll { !it.family || selected.contains(it.family) }
    }
}