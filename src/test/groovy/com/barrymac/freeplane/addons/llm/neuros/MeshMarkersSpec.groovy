package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject

@Subject(MeshMarkers)
class MeshMarkersSpec extends Specification {

    def "typed links become labelled edges"() {
        given:
        String card = '''[[card:11111111-1111-1111-1111-111111111111]]
# A card

[[depends-on:22222222-2222-2222-2222-222222222222]]
[[blocks:33333333-3333-3333-3333-333333333333]]
[[references:44444444-4444-4444-4444-444444444444]]
'''

        when:
        List<MeshEdge> edges = MeshMarkers.read(card, '11111111-1111-1111-1111-111111111111')

        then:
        edges.size() == 3
        edges.collect { it.marker } == ['depends-on', 'blocks', 'references']
        edges.collect { it.label } == ['depends on', 'blocks', 'references']
        edges.every { it.declared }
    }

    def "the entry's own identity marker is not an edge"() {
        given:
        String card = '''[[card:11111111-1111-1111-1111-111111111111]]
# A card
'''

        expect:
        MeshMarkers.read(card, '11111111-1111-1111-1111-111111111111').isEmpty()
    }

    def "a marker the cartridge does not name still reads as an edge"() {
        given:
        String card = '[[relates-to:22222222-2222-2222-2222-222222222222]]'

        when:
        List<MeshEdge> edges = MeshMarkers.read(card, null)

        then:
        edges.size() == 1
        edges[0].marker == 'relates-to'
        edges[0].label == 'relates to'
        !edges[0].declared
    }

    def "repeated identical links collapse and unreadable text yields nothing"() {
        expect:
        MeshMarkers.read('[[blocks:x]] [[blocks:x]]', null).size() == 1
        MeshMarkers.read('', null).isEmpty()
        MeshMarkers.read(null, null).isEmpty()
    }
    def "path-shaped targets normalize to their basename"() {
        given:
        String text = """---
list: Backlog
---
[[card:00000000-1111-2222-3333-444444444444]]
[[references:docs/diagrams/loam-app-logs-cap-guard.puml]]
[[references:packages/api/src/logger.ts]]
[[authored-by:sisyphus]]
"""

        when:
        List<MeshEdge> edges = MeshMarkers.read(text, null)

        then:
        edges.find { it.marker == 'references' }.target == 'loam-app-logs-cap-guard.puml'
        edges.findAll { it.marker == 'references' }*.target.sort() ==
                ['loam-app-logs-cap-guard.puml', 'logger.ts']
        edges.find { it.marker == 'authored-by' }.target == 'sisyphus'
    }
}
