package com.barrymac.freeplane.addons.llm.neuros

import com.barrymac.freeplane.addons.llm.ConfigKeys
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.ui.UiHelper

import java.awt.Color

/**
 * Generates a code atlas branch from sitter-structure-v1 renditions.
 *
 * The substrate's transducing writer produces one rendition per source file;
 * this action renders those renditions as a map: language containers, file
 * nodes stamped with the rendition uuid (the idempotency key), declarations
 * as children with signature and source range in their notes. Unresolved
 * imports render as grey chips — no invented cross-file edges, ever.
 *
 * Renditions come from the read surfaces; nothing is re-transduced here. When
 * the index looks stale the action triggers a scoped backlog scan through the
 * mount's .index control and says honestly what it did.
 */
class CodeAtlasAction {

    static final String CONTAINER_TITLE = 'Code atlas'
    static final String CONTAINER_TAG = 'neurOS code atlas'
    static final String FILE_TAG = 'neurOS code file'
    static final String GROUP_TAG = 'neurOS code group'
    static final int MAX_FILES = 50
    static final int MAX_SCAN_POLLS = 6
    static final long POLL_MILLIS = 500

    static void run(def c, def ui) {
        LogUtils.info('CodeAtlas action started.')
        try {
            def node = c.selected
            if (node == null) {
                UiHelper.showInformationMessage(ui, 'Please select a node first.')
                return
            }
            KnowledgeContext context = KnowledgeContext.discover(node.map.file)
            if (!context.isEnabled()) {
                UiHelper.showInformationMessage(ui, context.reason)
                return
            }
            String summary = generate(node, context)
            UiHelper.showInformationMessage(ui, summary)
        } catch (Exception problem) {
            LogUtils.severe("Error in CodeAtlas action: ${problem.message}", problem)
            UiHelper.showErrorMessage(ui, "Code Atlas Error: ${problem.message}")
        }
    }

    /** Generate (or refresh) the atlas branch; returns a reader-facing summary. */
    static String generate(def node, KnowledgeContext context) {
        File readRoot = context.source.path('')
        String scanNote = ensureIndexed(readRoot)

        File surface = context.source.path('by-tag/sitter-structure-v1')
        List<File> renditions = listRenditions(surface)
        if (renditions.isEmpty()) {
            return "${scanNote}\n\nNo code structure renditions are visible on this scope yet. " +
                    'If the source was just added, run this again after the scan drains.'
        }

        List<CodeRendition> parsed = renditions.take(MAX_FILES).collect { file ->
            try {
                return CodeRendition.parse(file.name, file.getText('UTF-8'))
            } catch (Exception problem) {
                LogUtils.warn("CodeAtlas: could not parse ${file.name}: ${problem.message}")
                return null
            }
        }.findAll { it != null }

        def container = findOrBuildContainer(node)
        int files = render(container, parsed, surface)
        String truncation = renditions.size() > MAX_FILES ?
                " Showing the first ${MAX_FILES} of ${renditions.size()}." : ''
        return "${scanNote}\n\nCode atlas: ${files} file${files == 1 ? '' : 's'} " +
                "across ${parsed.collect { it.lang }.unique().size()} language" +
                "${parsed.collect { it.lang }.unique().size() == 1 ? '' : 's'}.${truncation}"
    }

    /**
     * The mount's .index control: a scoped backlog scan is queued when the
     * journal shows nothing visited yet. Idempotent by the substrate's
     * content-hash journal, so a repeated trigger never re-transduces.
     */
    private static String ensureIndexed(File readRoot) {
        File mountRoot = readRoot.parentFile
        if (mountRoot == null) return 'Index location unknown; renditions read as-is.'
        File index = new File(mountRoot, '.index')
        if (!index.isFile()) return 'No index surface on this mount; renditions read as-is.'
        try {
            String before = index.getText('UTF-8')
            if (before.readLines().any { it.startsWith('visited:') && it.trim() != 'visited: 0' }) {
                return 'Index journal already has visits.'
            }
            index.setText('go', 'UTF-8')
            for (int poll = 0; poll < MAX_SCAN_POLLS; poll++) {
                Thread.sleep(POLL_MILLIS)
                String now = index.getText('UTF-8')
                if (now?.readLines()?.any { it.startsWith('state:') && it.contains('completed') } &&
                        now.readLines().any { it.startsWith('visited:') && it.trim() != 'visited: 0' }) {
                    return 'Scoped backlog scan completed before rendering.'
                }
            }
            return 'Scoped backlog scan queued; rendering what is already visible.'
        } catch (Exception problem) {
            LogUtils.warn("CodeAtlas: index trigger failed: ${problem.message}")
            return 'Index trigger failed (' + problem.message + '); renditions read as-is.'
        }
    }

    private static List<File> listRenditions(File surface) {
        try {
            File[] files = surface.listFiles()
            if (files == null) return []
            return files.findAll { it.isFile() && !it.name.startsWith('.') }.sort { it.name }
        } catch (Exception ignored) {
            return []
        }
    }

    private static def findOrBuildContainer(def node) {
        def existing = MeshDrawing.findTagged(node.map.root, 'neurOS code atlas')
        if (existing != null) {
            // Refresh in place, but ONLY what this generator made: tagged
            // groups and file nodes go, anything a person added under the
            // atlas stays exactly where it was. Never destroy reader context.
            new ArrayList(existing.children).each { child ->
                if (child.tags?.contains(GROUP_TAG) || child.tags?.contains(FILE_TAG)) {
                    child.delete()
                }
            }
            return existing
        }
        def container = node.map.root.createChild(CONTAINER_TITLE)
        container.tags.add(CONTAINER_TAG)
        container.note = 'Code structure rendered from the substrate\'s sitter ' +
                'renditions. File nodes carry the rendition uuid; declarations ' +
                'carry their signature and source range. Grey import chips are ' +
                'unresolved — cross-file edges appear only when the substrate ' +
                'resolves them.'
        return container
    }

    private static int render(def container, List<CodeRendition> renditions, File surface) {
        Map<String, List<CodeRendition>> byLanguage = renditions.groupBy { it.lang ?: 'unknown' }
        int files = 0
        byLanguage.each { lang, group ->
            def languageNode = container.createChild("${capitalise(lang)} (${group.size()} file${group.size() == 1 ? '' : 's'})")
            languageNode.tags.add(GROUP_TAG)
            languageNode.setFolded(true)
            group.each { rendition ->
                def fileNode = languageNode.createChild(rendition.title())
                fileNode.tags.add(FILE_TAG)
                fileNode.attributes.add('rendition', rendition.uuid)
                // The declared source file is this node's identity for mesh
                // matching: a diagram referencing the file by name wires here.
                if (rendition.file) fileNode.attributes.add('source-file', rendition.file)
                if (rendition.sourceSha256) fileNode.attributes.add('source_sha256', rendition.sourceSha256)
                fileNode.note = "lang=${rendition.lang} lane=${rendition.lane}" +
                        (rendition.isDegraded() ? '\nDEGRADED: ' +
                                (rendition.partial ? 'partial rendition; ' : '') +
                                "${rendition.errors} parser error${rendition.errors == 1 ? '' : 's'}" : '')
                try {
                    fileNode.link.file = new File(surface, rendition.uuid)
                } catch (Exception ignored) {
                    // The link is navigation sugar; its absence never blocks rendering.
                }
                renderImports(fileNode, rendition)
                rendition.declarations.each { declaration ->
                    def declarationNode = fileNode.createChild(declaration.title())
                    if (declaration.locationNote()) declarationNode.note = declaration.locationNote()
                }
                files++
            }
        }
        return files
    }

    private static void renderImports(def fileNode, CodeRendition rendition) {
        if (rendition.imports.isEmpty()) return
        int unresolved = rendition.imports.count { !it.resolved }
        def importsNode = fileNode.createChild(
                "imports (${rendition.imports.size()}${unresolved ? ", ${unresolved} unresolved" : ''})")
        importsNode.setFolded(true)
        rendition.imports.each { ref ->
            def chip = importsNode.createChild(ref.names ?: ref.specifier)
            chip.note = "from ${ref.specifier}" + (ref.resolved ? '' : '\nUnresolved: no cross-file edge ' +
                    'is drawn until the substrate resolves this import.')
            if (!ref.resolved) {
                try {
                    chip.textColor = new Color(0x88, 0x88, 0x88)
                } catch (Exception ignored) {
                    // Styling is cosmetic; never block rendering on it.
                }
            }
        }
    }

    private static final Map<String, String> LANGUAGE_LABELS = [
            typescript: 'TypeScript', javascript: 'JavaScript', kotlin: 'Kotlin',
            groovy: 'Groovy', rust: 'Rust', go: 'Go', python: 'Python',
            java: 'Java', dhall: 'Dhall', scss: 'SCSS', dockerfile: 'Dockerfile',
            protobuf: 'Protobuf', shell: 'Shell', c: 'C', cpp: 'C++'
    ]

    /** The display form of a language name, with proper casing when known. */
    private static String capitalise(String value) {
        if (!value) return value
        return LANGUAGE_LABELS[value.toLowerCase()] ?:
                value.substring(0, 1).toUpperCase() + value.substring(1)
    }
}
