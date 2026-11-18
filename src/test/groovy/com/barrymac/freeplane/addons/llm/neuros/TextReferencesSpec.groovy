package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

@Subject(TextReferences)
class TextReferencesSpec extends Specification {

    @TempDir
    File scope

    private File entry(String path, String content = 'x') {
        File file = new File(scope, path)
        file.parentFile?.mkdirs()
        file.text = content
        return file
    }

    def "file-like tokens are found in prose and deduplicated"() {
        given:
        String text = '''Evidence: `src/main/groovy/Example.groovy` and Example.groovy again.
Diagram: docs/diagrams/wave.puml. Config: build.gradle, notes.md.'''

        when:
        List<String> tokens = TextReferences.extract(text)

        then:
        tokens.contains('src/main/groovy/Example.groovy')
        tokens.contains('docs/diagrams/wave.puml')
        tokens.contains('build.gradle')
        tokens.collect { TextReferences.baseName(it) }.count { it == 'Example.groovy' } == 2
    }

    def "only tokens that exist in the scope resolve"() {
        given:
        File code = entry('by-tag/class/Example.groovy')
        File diagram = entry('diagrams/wave.puml')
        String text = 'Evidence: src/main/groovy/Example.groovy. Diagram: wave.puml. Also Ghost.groovy.'

        when:
        List<File> resolved = TextReferences.resolve(text, [code, diagram])

        then:
        resolved == [code, diagram]
    }

    def "a token that names a diagram proposes depicts, a file proposes references"() {
        given:
        File code = entry('by-tag/class/Example.groovy')
        File diagram = entry('diagrams/wave.puml')

        when:
        List<MeshEdge> edges = TextReferences.proposals(
                'Example.groovy and wave.puml', 'seed-id', [code, diagram])

        then:
        edges.size() == 2
        edges.every { it.proposed && it.source == 'seed-id' }
        edges.collect { it.label } == ['references', 'depicts']
        edges.collect { it.target } == ['Example.groovy', 'wave.puml']
    }

    def "a bare type name resolves to its source file"() {
        given:
        File vocabulary = entry('by-tag/class/MeshVocabulary.groovy')
        File drawing = entry('by-tag/class/MeshDrawing.groovy')

        when:
        List<File> resolved = TextReferences.resolve(
                'MeshVocabulary carries the family; MeshDrawing draws it.', [vocabulary, drawing])

        then:
        resolved == [vocabulary, drawing]
    }

    def "text with no file names proposes nothing"() {
        expect:
        TextReferences.extract('No paths here, just prose.').isEmpty()
        TextReferences.proposals('No paths here.', 'seed', []).isEmpty()
    }

    def "the scope's own surfaces supply the candidates"() {
        given:
        entry('by-tag/class/Example.groovy')
        entry('by-tag/diagram/wave.puml')
        entry('diagrams/wave.puml')
        entry('tasks/CARD-abc-some.card.md')

        when:
        List<File> files = TextReferences.scopeFiles(scope)

        then:
        files.collect { it.name }.containsAll(['Example.groovy', 'wave.puml'])
        !files.any { it.name.endsWith('.card.md') }
    }
}