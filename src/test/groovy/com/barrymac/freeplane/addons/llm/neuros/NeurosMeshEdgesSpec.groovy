package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

@Subject(NeurosMeshAction)
class NeurosMeshEdgesSpec extends Specification {

    @TempDir
    File scope

    def "a card contributes its typed links and not its identity"() {
        given:
        File card = new File(scope, 'CARD-0f65c7c1-64de-4d96-a0ef-87b89f3a6cfe-bounded.card.md')
        card.text = '''[[card:0f65c7c1-64de-4d96-a0ef-87b89f3a6cfe]]
# Bounded

[[depends-on:2ca53842-dad9-4559-8b25-1c179dc076f9]]
[[blocks:5036a147-543e-41f2-8d52-daf6f1e19030]]
'''
        MeshNode seed = MeshIndex.nodeFor(card)

        when:
        List<MeshEdge> edges = NeurosMeshAction.readEdges(card, seed)

        then:
        edges.collect { it.label } == ['depends on', 'blocks']
        edges.every { it.source == '0f65c7c1-64de-4d96-a0ef-87b89f3a6cfe' }
    }

    def "a diagram contributes the arrows between the cards it depicts"() {
        given:
        File diagram = new File(scope, 'wave.puml')
        diagram.text = '''@startuml
rectangle "One\\n[[card:aaaaaaaa-1111-1111-1111-111111111111]]" as ONE
rectangle "Two\\n[[card:bbbbbbbb-2222-2222-2222-222222222222]]" as TWO
ONE --> TWO : blocks
@enduml
'''

        when:
        List<MeshEdge> edges = NeurosMeshAction.readEdges(diagram, MeshIndex.nodeFor(diagram))

        then:
        edges.size() == 1
        edges[0].source == 'aaaaaaaa-1111-1111-1111-111111111111'
        edges[0].target == 'bbbbbbbb-2222-2222-2222-222222222222'
        edges[0].label == 'blocks'
    }

    def "an entry with no links yields no edges rather than an error"() {
        given:
        File card = new File(scope, 'CARD-11111111-1111-1111-1111-111111111111-empty.card.md')
        card.text = '[[card:11111111-1111-1111-1111-111111111111]]\n# Empty\n'

        expect:
        NeurosMeshAction.readEdges(card, MeshIndex.nodeFor(card)).isEmpty()
    }
}