package com.barrymac.freeplane.addons.llm.neuros

/**
 * Relationships an entry states in plain text: a file path, a class name, a
 * diagram. No judgement is involved and none is wanted — if a card names
 * `MeshDrawing.groovy`, the link exists whether or not a model agrees.
 *
 * This is the evidence half of enmeshment. The judgement half proposes links the
 * text supports but does not spell out; this half proposes the ones it does.
 */
class TextReferences {

    static final int MAX_REFERENCES = 20

    /** File-ish tokens in prose: paths, file names, diagram names. */
    private static final java.util.regex.Pattern TOKEN =
            ~/(?:[\w.\/-]*\/)?[\w-]+\.(?:groovy|java|kt|kts|rs|py|ts|tsx|js|jsx|rb|go|scala|swift|cs|sh|dhall|yaml|yml|json|xml|toml|sql|md|puml|gradle|properties)\b/

    /** A bare type name, the way prose usually names a class. */
    private static final java.util.regex.Pattern IDENTIFIER =
            ~/\b[A-Z][A-Za-z0-9]{2,}(?:Spec|Test|Action|Client|Drawing|Proposal|Vocabulary|Check|Gaps|Lens|Index|Source|Context|Limits|Renderer|Parser|Filter|Sanitizer|Listing|Catalog|Builder|Editor|Dialog|Helper)?\b/

    private static final List<String> CODE_EXTENSIONS = ['groovy', 'java', 'kt', 'rs', 'py', 'ts', 'js']

    /** What an entry says it touches, deduplicated and bounded. */
    static List<String> extract(String text) {
        if (!text?.trim()) return []
        Set<String> found = new LinkedHashSet<>()
        def matcher = TOKEN.matcher(text)
        while (matcher.find() && found.size() < MAX_REFERENCES) {
            String token = matcher.group().replaceAll(/^\.\//, '').trim()
            if (token) found << token
        }
        def identifiers = IDENTIFIER.matcher(text)
        while (identifiers.find() && found.size() < MAX_REFERENCES * 2) {
            found << identifiers.group()
        }
        return found.toList()
    }

    /** The file name alone, which is how entries in a scope are named. */
    static String baseName(String token) {
        if (!token) return ''
        int slash = token.lastIndexOf('/')
        return slash >= 0 ? token.substring(slash + 1) : token
    }

    /**
     * Resolve the tokens an entry names against files that exist in the scope.
     * Returns the files, in the order the entry named them.
     */
    static List<File> resolve(String text, Collection<File> candidates, int limit = MAX_REFERENCES) {
        if (!candidates) return []
        Map<String, File> byName = [:]
        candidates.each { File file ->
            if (file?.isFile()) byName[file.name.toLowerCase()] = file
        }
        List<File> resolved = []
        extract(text).each { token ->
            File match = byName[baseName(token).toLowerCase()] ?: byType(byName, token)
            if (match != null && !resolved.contains(match) && resolved.size() < limit) {
                resolved << match
            }
        }
        return resolved
    }

    /** `MeshDrawing` resolves to MeshDrawing.groovy when no extension is given. */
    private static File byType(Map<String, File> byName, String token) {
        if (token.contains('.') || token.contains('/')) return null
        for (String extension : CODE_EXTENSIONS) {
            File match = byName["${token.toLowerCase()}.${extension}".toString()]
            if (match != null) return match
        }
        return null
    }

    /** Files worth resolving against: code entries and diagrams in this scope. */
    static List<File> scopeFiles(File scopeRoot) {
        if (scopeRoot == null || !scopeRoot.isDirectory()) return []
        List<File> files = []
        ['by-tag/class', 'by-tag/file', 'by-tag/diagram', 'diagrams'].each { String surface ->
            File directory = new File(scopeRoot, surface)
            if (!directory.isDirectory()) return
            File[] listing = directory.listFiles()
            if (listing == null) return
            listing.findAll { it.isFile() }.each { files << it }
        }
        File[] root = scopeRoot.listFiles()
        root?.findAll { it.isFile() && it.name.endsWith('.puml') }?.each { files << it }
        return files
    }

    /**
     * Edges for what the entry names, as provisional connectors ready to draw.
     * `documents` for a file the entry describes, `references` otherwise.
     */
    static List<MeshEdge> proposals(String text, String sourceId, Collection<File> candidates) {
        List<File> resolved = resolve(text, candidates)
        return resolved.collect { File file ->
            String label = file.name.endsWith('.puml') ? 'depicts' : 'references'
            new MeshEdge(
                    source: sourceId,
                    target: file.name,
                    marker: label,
                    label: label,
                    family: MeshVocabulary.REFERENCE,
                    declared: MeshVocabulary.isDeclared(label),
                    proposed: true)
        }
    }
}