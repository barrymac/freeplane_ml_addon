package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject

@Subject(PumlMeshReader)
class PumlMeshReaderSpec extends Specification {

    private static final String DIAGRAM = '''@startuml
title Relationship wave

rectangle "Publish screenshot tour\\n[[card:fe5ecc8b-d0ca-4075-83f5-fd1746112178]]\\nREADME.md" as PUB #FFF2CC
rectangle "Automatic visible-hit connectors\\n[[card:2ca53842-dad9-4559-8b25-1c179dc076f9]]\\nNeurosClient.groovy" as AUTO #D5E8D4
rectangle "Bounded relationship expansion\\n[[card:0f65c7c1-64de-4d96-a0ef-87b89f3a6cfe]]\\nNeurosBrowseAction.groovy" as EXPAND #D5E8D4

PUB --> AUTO : depends-on
AUTO --> EXPAND : blocks
EXPAND --> PUB : references
@enduml
'''

    def "a diagram contributes real edges between the cards it depicts"() {
        when:
        List<MeshEdge> edges = PumlMeshReader.read(DIAGRAM)

        then:
        edges.size() == 3
        edges[0].source == 'fe5ecc8b-d0ca-4075-83f5-fd1746112178'
        edges[0].target == '2ca53842-dad9-4559-8b25-1c179dc076f9'
        edges[0].label == 'depends-on'
        edges[0].family == MeshVocabulary.DEPENDENCY
        edges[1].label == 'blocks'
        edges[1].family == MeshVocabulary.BLOCKING
    }

    def "an arrow without a label still reads"() {
        given:
        String diagram = '''@startuml
rectangle "One\\n[[card:aaaaaaaa-1111-1111-1111-111111111111]]" as ONE
rectangle "Two\\n[[card:bbbbbbbb-2222-2222-2222-222222222222]]" as TWO
ONE --> TWO
@enduml
'''

        when:
        List<MeshEdge> edges = PumlMeshReader.read(diagram)

        then:
        edges.size() == 1
        edges[0].label == 'relates to'
    }

    def "edges between shapes without cards are ignored"() {
        given:
        String diagram = '''@startuml
rectangle "Scope boundary" as SCOPE
rectangle "Door\\n[[card:aaaaaaaa-1111-1111-1111-111111111111]]" as DOOR
SCOPE --> DOOR : confirms boundary crossing
@enduml
'''

        expect:
        PumlMeshReader.read(diagram).isEmpty()
    }
    def "standalone reference markers become seed edges with basenames"() {
        given:
        String text = """' ── enmeshment ──
' [[references:docs/diagrams/02-access-plane.puml]]
' [[references:tasks/p0-laws-as-types-scaffold.card.md]]
' [[references:src/auth/service.ts]]

@startuml
' [[depicts:ontic-gke-plane]]
' [[owned-by:barry]]
' Card: [[card:063f606f-f01b-4bb1-aa73-7cdf6dfbbced]]
rectangle "hub" as H
@enduml
"""

        when:
        List<MeshEdge> edges = PumlMeshReader.read(text)

        then:
        // Five standalone markers: three references, depicts, owned-by
        edges.size() == 5
        def references = edges.findAll { it.marker == 'references' }
        references.size() == 3
        references.every { it.source == null && it.declared }
        references.every { it.family == MeshVocabulary.REFERENCE }
        // Path targets normalise to basenames for identity matching
        references*.target.sort() == ['02-access-plane.puml', 'p0-laws-as-types-scaffold.card.md', 'service.ts']
        def depicts = edges.find { it.marker == 'depicts' }
        depicts.target == 'ontic-gke-plane'
        depicts.label == 'depicts'
    }

    def "duplicate standalone markers dedupe and the card marker does not become an edge"() {
        given:
        String text = """' [[references:docs/a.puml]]
' [[references:docs/a.puml]]
' [[card:063f606f-f01b-4bb1-aa73-7cdf6dfbbced]]
"""

        when:
        List<MeshEdge> edges = PumlMeshReader.read(text)

        then:
        edges.size() == 1
        edges[0].target == 'a.puml'
    }
}
