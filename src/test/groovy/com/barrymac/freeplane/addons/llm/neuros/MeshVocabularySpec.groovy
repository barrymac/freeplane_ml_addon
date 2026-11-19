package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject

@Subject(MeshVocabulary)
class MeshVocabularySpec extends Specification {

    def "declared markers read as words and carry a colour family"() {
        expect:
        MeshVocabulary.label('depends-on') == 'depends on'
        MeshVocabulary.family('depends-on') == MeshVocabulary.DEPENDENCY
        MeshVocabulary.label('blocks') == 'blocks'
        MeshVocabulary.family('blocks') == MeshVocabulary.BLOCKING
        MeshVocabulary.isDeclared('references')
    }

    def "a marker the cartridge has not named still reads, from its own name"() {
        expect:
        !MeshVocabulary.isDeclared('relates-to')
        MeshVocabulary.label('relates-to') == 'relates to'
        MeshVocabulary.label('owned_by') == 'owned by'
        MeshVocabulary.family('relates-to') == MeshVocabulary.REFERENCE
    }

    def "disagreement is its own family and is drawn dashed"() {
        expect:
        MeshVocabulary.isDeclared('contradicts')
        MeshVocabulary.label('contradicts') == 'contradicts'
        MeshVocabulary.family('contradicts') == MeshVocabulary.CONTRADICTION
        MeshVocabulary.isDashed(MeshVocabulary.CONTRADICTION)
        MeshVocabulary.dashFor(MeshVocabulary.CONTRADICTION) == ([6, 4] as int[])
        !MeshVocabulary.isDashed(MeshVocabulary.DEPENDENCY)
        MeshVocabulary.dashFor(MeshVocabulary.DEPENDENCY) == null
        MeshVocabulary.color(MeshVocabulary.CONTRADICTION) !=
                MeshVocabulary.color(MeshVocabulary.REFERENCE)
    }

    def "colour families stay distinguishable"() {
        expect:
        MeshVocabulary.color(MeshVocabulary.DEPENDENCY) !=
                MeshVocabulary.color(MeshVocabulary.BLOCKING)
        MeshVocabulary.color('not-a-family') == MeshVocabulary.color(MeshVocabulary.REFERENCE)
    }
}