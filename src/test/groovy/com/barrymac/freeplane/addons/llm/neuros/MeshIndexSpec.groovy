package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

@Subject(MeshIndex)
class MeshIndexSpec extends Specification {

    @TempDir
    File scope

    private static final String EXPAND = '0f65c7c1-64de-4d96-a0ef-87b89f3a6cfe'
    private static final String DOOR = '5036a147-543e-41f2-8d52-daf6f1e19030'

    private File card(String name) {
        File file = new File(scope, name)
        file.text = '[[card:x]]\n# title\n'
        return file
    }

    def setup() {
        MeshIndex.invalidate()
    }

    def "titles read as words, never as identifiers"() {
        expect:
        MeshIndex.titleOf("CARD-${EXPAND}-bounded-relationship-expansion.card.md") ==
                'Bounded relationship expansion'
        MeshIndex.titleOf('neuros-relationship-wave.puml') == 'Neuros relationship wave'
        MeshIndex.titleOf("${DOOR}-lazy-door-navigation.card.md") == 'Lazy door navigation'
    }

    def "a target resolves by identifier, file name or title"() {
        given:
        File expansion = card("CARD-${EXPAND}-bounded-relationship-expansion.card.md")
        card("CARD-${DOOR}-lazy-door-navigation.card.md")
        MeshIndex index = MeshIndex.load(scope)

        expect:
        index.find(EXPAND).file == expansion
        index.find("CARD-${EXPAND}-bounded-relationship-expansion.card.md").file == expansion
        index.find('Bounded relationship expansion').file == expansion
        index.find('nothing like this here') == null
    }

    def "diagrams are indexed next to cards"() {
        given:
        File diagram = new File(scope, 'neuros-relationship-wave.puml')
        diagram.text = '@startuml\n@enduml\n'
        MeshIndex index = MeshIndex.load(scope)

        expect:
        index.find('neuros-relationship-wave.puml').file == diagram
    }

    def "a missing scope resolves nothing instead of failing"() {
        expect:
        MeshIndex.load(null).entries.isEmpty()
        MeshIndex.load(new File(scope, 'absent')).find(EXPAND) == null
    }
}