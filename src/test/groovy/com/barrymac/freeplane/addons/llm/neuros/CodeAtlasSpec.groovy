package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

import java.nio.file.Files

/**
 * The atlas generation decisions, exercised without a running Freeplane:
 * grouping, idempotent refresh, uuid stamping, honest degradation, and the
 * index trigger — all against the separated-mount layout the scopes serve.
 */
@Subject(CodeAtlasAction)
class CodeAtlasSpec extends Specification {

    @TempDir
    File root

    private File mount
    private File surfaces
    private File surface
    private FakeMap map
    private FakeNode seed

    def setup() {
        mount = new File(root, 'mount')
        surfaces = new File(mount, 'KNOWLEDGE')
        surface = new File(surfaces, 'by-tag/sitter-structure-v1')
        surface.mkdirs()
        new File(surfaces, 'by-query').mkdirs()
        new File(surfaces, 'watcher-status').text = 'status: OK\nscope: atlas-scope\n'
        new File(surfaces, 'README.cartridge').text = '# cartridge\n'
        new File(mount, 'workspace').mkdirs()
        new File(mount, '.index').text = 'state: completed\nrun_id: \nvisited: 7\n'
        File workspace = new File(root, 'workspace')
        workspace.mkdirs()
        Files.createSymbolicLink(new File(workspace, 'LEGAL_KNOWLEDGE').toPath(), mount.toPath())
        File mapFile = new File(workspace, 'map.mm')
        mapFile.text = '<map version="freeplane 1.12.1"/>'
        map = new FakeMap()
        seed = map.root.createChild('seed')
        seed.map = map
    }

    private KnowledgeContext context() {
        return KnowledgeContext.discover(new File(root, 'workspace/map.mm'))
    }

    private void rendition(String uuid, String file, String lang, String extra = '') {
        new File(surface, uuid).text = """kind: transduced-rendition
source_sha256: sha-${uuid.take(6)}
transducer: sitter-structure-v1
partial: false
---
# structure: ${file}
lang=${lang} lane=tree-sitter errors=0 decls=1 imports=1 resolved=0

## imports
- ./other.js [thing] (unresolved)

## class ${file.tokenize('.').first().capitalize()} 3:1-40:2
sig: class ${file.tokenize('.').first().capitalize()}
${extra}
"""
    }

    def "renditions render grouped by language with uuid stamps"() {
        given:
        rendition('aaaa1111-0000-0000-0000-000000000001', 'index.ts', 'typescript')
        rendition('aaaa2222-0000-0000-0000-000000000002', 'main.rs', 'rust')

        when:
        String summary = CodeAtlasAction.generate(seed, context())

        then:
        summary.contains('2 files')
        summary.contains('2 languages')
        def container = map.root.children.find { it.tags.contains(CodeAtlasAction.CONTAINER_TAG) }
        container != null
        // Two language containers, one file node each
        def languages = container.children
        languages*.text.sort() == ['Rust (1 file)', 'Typescript (1 file)']
        def tsFile = languages.find { it.text.startsWith('Typescript') }.children[0]
        tsFile.text == 'index.ts'
        tsFile.tags.contains(CodeAtlasAction.FILE_TAG)
        tsFile.attributes['rendition'] == 'aaaa1111-0000-0000-0000-000000000001'
        tsFile.attributes['source_sha256'] == 'sha-aaaa11'
        // Declaration child with signature and range in the note
        def declaration = tsFile.children.find { it.text.startsWith('class') }
        declaration.note.contains('signature: class Index')
        declaration.note.contains('lines 3 col 1-40 col 2')
        // Import chip: unresolved, greyed
        def imports = tsFile.children.find { it.text.startsWith('imports') }
        imports.text == 'imports (1, 1 unresolved)'
        def chip = imports.children[0]
        chip.text == 'thing'
        chip.note.contains('Unresolved')
    }

    def "a person's own nodes under the atlas survive a refresh"() {
        given:
        rendition('aaaa1111-0000-0000-0000-000000000001', 'index.ts', 'typescript')
        CodeAtlasAction.generate(seed, context())
        def container = map.root.children.find { it.tags.contains(CodeAtlasAction.CONTAINER_TAG) }
        def myNote = container.createChild('my reading note')
        myNote.note = 'hand-written context'

        when:
        rendition('aaaa2222-0000-0000-0000-000000000002', 'main.rs', 'rust')
        CodeAtlasAction.generate(seed, context())

        then:
        container.children.any { it.text == 'my reading note' && it.note == 'hand-written context' }
        // and the generated content refreshed
        container.children*.text.sort().contains('Rust (1 file)')
        container.children*.text.sort().contains('my reading note')
    }

    def "a second generate replaces the old atlas, never nests a second one"() {
        given:
        rendition('aaaa1111-0000-0000-0000-000000000001', 'index.ts', 'typescript')
        CodeAtlasAction.generate(seed, context())
        rendition('aaaa2222-0000-0000-0000-000000000002', 'main.rs', 'rust')

        when:
        CodeAtlasAction.generate(seed, context())

        then:
        map.root.children.count { it.tags.contains(CodeAtlasAction.CONTAINER_TAG) } == 1
        def container = map.root.children.find { it.tags.contains(CodeAtlasAction.CONTAINER_TAG) }
        // Both languages are the fresh render, not the old one plus the new one
        container.children*.text.sort() == ['Rust (1 file)', 'Typescript (1 file)']
    }

    def "a degraded rendition renders with an honest note, not hidden"() {
        given:
        new File(surface, 'bbbb1111-0000-0000-0000-000000000003').text = """partial: true
---
# structure: broken.rs
lang=rust lane=tree-sitter errors=3 decls=0 imports=0 resolved=0
"""

        when:
        CodeAtlasAction.generate(seed, context())

        then:
        def container = map.root.children.find { it.tags.contains(CodeAtlasAction.CONTAINER_TAG) }
        def fileNode = container.children[0].children[0]
        fileNode.text == 'broken.rs'
        fileNode.note.contains('DEGRADED')
        fileNode.note.contains('3 parser errors')
    }

    def "an empty surface explains itself instead of drawing an empty atlas"() {
        when:
        String summary = CodeAtlasAction.generate(seed, context())

        then:
        !map.root.children.any { it.tags.contains(CodeAtlasAction.CONTAINER_TAG) }
        summary.contains('No code structure renditions')
        summary.contains('Index journal already has visits')
    }

    def "a stale index gets the scoped scan command written to it"() {
        given:
        new File(mount, '.index').text = 'state: completed\nrun_id: \nvisited: 0\n'
        rendition('aaaa1111-0000-0000-0000-000000000001', 'index.ts', 'typescript')

        when:
        String summary = CodeAtlasAction.generate(seed, context())

        then:
        summary.contains('queued') || summary.contains('completed')
        // The full-workspace scan command was written (the fixture never
        // drains, so the honest message is "queued" and the render still
        // happened)
        new File(mount, '.index').text.contains('go')
    }

    def "the file count is bounded with an honest truncation note"() {
        given:
        (1..55).each { index ->
            rendition(String.format('cccc%04d-0000-0000-0000-000000000000', index),
                    "file${index}.ts", 'typescript')
        }

        when:
        String summary = CodeAtlasAction.generate(seed, context())

        then:
        summary.contains("first ${CodeAtlasAction.MAX_FILES} of 55")
        def container = map.root.children.find { it.tags.contains(CodeAtlasAction.CONTAINER_TAG) }
        def language = container.children.find { it.text.startsWith('Typescript') }
        language.children.size() == CodeAtlasAction.MAX_FILES
    }

    /** The slice of Freeplane's proxies the atlas relies on. */
    static class FakeMap {
        FakeNode root

        FakeMap() {
            root = new FakeNode(text: 'root')
            root.map = this
        }
    }

    static class FakeNode {
        String text
        String note
        String textColor
        boolean folded
        FakeAttributes attributes = new FakeAttributes()
        List<FakeNode> children = []
        Set<String> tags = [] as Set
        FakeMap map

        FakeNode parent

        FakeNode createChild(String title) {
            FakeNode node = new FakeNode(text: title, map: map, parent: this)
            children << node
            return node
        }

        void delete() {
            parent?.children?.remove(this)
        }

        void setFolded(boolean value) { folded = value }

        /** Freeplane's attribute proxy: add(name, value), map-style reads. */
        static class FakeAttributes {
            private final Map<String, String> values = [:]

            void add(String name, String value) { values[name] = value }

            String getAt(String name) { return values[name] }

            int size() { return values.size() }
        }
    }
}
