package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

/**
 * The transitive dependency walk: depth, fan-out and edge caps are honest,
 * cycles are marked on the edge that closes them, and nothing undeclared is
 * ever followed.
 */
@Subject(DependencyWalker)
class DependencyWalkerSpec extends Specification {

    @TempDir
    File scope

    private MeshIndex index

    def setup() {
        MeshIndex.invalidate()
        ['a', 'b', 'c', 'd', 'e', 'f'].each { name ->
            new File(scope, "${name}.card.md").text = "[[card:${name}]]\n"
        }
        index = MeshIndex.load(scope)
    }

    def cleanup() {
        MeshIndex.invalidate()
    }

    private List<MeshEdge> dependenciesOf(String name, Map<String, List<String>> graph) {
        return (graph[name] ?: []).collect { target ->
            new MeshEdge(source: name, target: target, marker: 'depends-on',
                    label: 'depends on', family: MeshVocabulary.DEPENDENCY, declared: true)
        }
    }

    def "a linear chain walks to the end with no cycles and no truncation"() {
        given:
        def graph = [a: ['b'], b: ['c'], c: []]

        when:
        def result = DependencyWalker.walk('a', index) { MeshNode entry ->
            dependenciesOf(entry.file.name.replace('.card.md', ''), graph)
        }

        then:
        result.edges*.target == ['b', 'c']
        result.cycleTargets.isEmpty()
        !result.truncated
    }

    def "a cycle is marked on the closing edge and the walk continues safely"() {
        given:
        def graph = [a: ['b'], b: ['a']]

        when:
        def result = DependencyWalker.walk('a', index) { MeshNode entry ->
            dependenciesOf(entry.file.name.replace('.card.md', ''), graph)
        }

        then:
        result.cycleTargets.contains('a')
        result.edges.count { it.cycle } == 1
        result.edges.find { it.cycle }.target == 'a'
        result.edges.size() == 2
    }

    def "a self-loop is a cycle of length one"() {
        given:
        def graph = [a: ['a']]

        when:
        def result = DependencyWalker.walk('a', index) { MeshNode entry ->
            dependenciesOf(entry.file.name.replace('.card.md', ''), graph)
        }

        then:
        result.cycleTargets.contains('a')
        result.edges.every { it.cycle }
    }

    def "the depth cap truncates honestly"() {
        given:
        def graph = [a: ['b'], b: ['c'], c: ['d'], d: ['e'], e: ['f']]

        when:
        def result = DependencyWalker.walk('a', index) { MeshNode entry ->
            dependenciesOf(entry.file.name.replace('.card.md', ''), graph)
        }

        then:
        result.truncated
        result.edges*.target == ['b', 'c', 'd']
    }

    def "the fan-out cap truncates honestly"() {
        given:
        def graph = [a: (1..20).collect { "x${it}" }]

        when:
        def result = DependencyWalker.walk('a', index) { MeshNode entry ->
            dependenciesOf(entry.file.name.replace('.card.md', ''), graph)
        }

        then:
        result.truncated
        result.edges.size() == DependencyWalker.MAX_FANOUT
    }

    def "an entry that resolves nowhere is skipped, not invented"() {
        given:
        def graph = [a: ['zz-missing']]

        when:
        def result = DependencyWalker.walk('a', index) { MeshNode entry ->
            dependenciesOf(entry.file.name.replace('.card.md', ''), graph)
        }

        then:
        result.edges*.target == ['zz-missing']
        result.cycleTargets.isEmpty()
    }

    def "an empty or absent seed yields nothing rather than guessing"() {
        expect:
        DependencyWalker.walk(null, index) { [] }.edges.isEmpty()
        DependencyWalker.walk('  ', index) { [] }.edges.isEmpty()
    }
}
