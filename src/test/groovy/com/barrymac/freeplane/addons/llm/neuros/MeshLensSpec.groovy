package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject

@Subject(MeshLens)
class MeshLensSpec extends Specification {

    private static Map storage(Map<String, Object> values = [:]) {
        [getProperty : { String key, Object fallback = null -> values.containsKey(key) ? values[key] : fallback },
         setProperty : { String key, Object value -> values[key] = value },
         values      : values] as Map
    }

    def "with nothing configured the lens is every family"() {
        expect:
        MeshLens.read(storage()) == MeshLens.allFamilies()
    }

    def "a chosen lens is remembered and read back"() {
        given:
        def store = storage()

        when:
        MeshLens.write(store, [MeshVocabulary.DEPENDENCY, MeshVocabulary.CONTRADICTION])

        then:
        MeshLens.read(store) == [MeshVocabulary.DEPENDENCY, MeshVocabulary.CONTRADICTION] as Set
    }

    def "unknown families are dropped rather than stored"() {
        given:
        def store = storage()

        when:
        MeshLens.write(store, ['dependency', 'nonsense'])

        then:
        MeshLens.read(store) == [MeshVocabulary.DEPENDENCY] as Set
    }

    def "an empty selection falls back to every family"() {
        given:
        def store = storage()

        when:
        MeshLens.write(store, [])

        then:
        MeshLens.read(store) == MeshLens.allFamilies()
    }

    def "filtering keeps the chosen families and passes unknown ones through"() {
        given:
        List<MeshEdge> edges = [
                new MeshEdge(family: MeshVocabulary.DEPENDENCY),
                new MeshEdge(family: MeshVocabulary.CONTRADICTION),
                new MeshEdge(family: null)
        ]

        expect:
        MeshLens.filter(edges, [MeshVocabulary.DEPENDENCY] as Set).size() == 2
        MeshLens.filter(edges, MeshLens.allFamilies()).size() == 3
        MeshLens.filter([], MeshLens.allFamilies()).isEmpty()
    }
}