package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(MeshVocabulary)
class MeshVocabularyDataSpec extends Specification {

    @Unroll
    def "marker '#marker' reads as '#label' in family #family"() {
        expect:
        MeshVocabulary.label(marker) == label
        MeshVocabulary.family(marker) == family
        MeshVocabulary.isDeclared(marker)

        where:
        marker         | label                | family
        'depends-on'   | 'depends on'         | MeshVocabulary.DEPENDENCY
        'blocked-by'   | 'blocked by'         | MeshVocabulary.DEPENDENCY
        'blocks'       | 'blocks'             | MeshVocabulary.BLOCKING
        'references'   | 'references'         | MeshVocabulary.REFERENCE
        'contains'     | 'contains'           | MeshVocabulary.CODE
        'tracks'       | 'tracks'             | MeshVocabulary.TRACKING
        'child-of'     | 'child of'           | MeshVocabulary.STRUCTURE
        'presides-over'| 'presides over'      | MeshVocabulary.AUTHORITY
        'cites'        | 'cites'              | MeshVocabulary.CITATION
        'overrules'    | 'overrules'          | MeshVocabulary.CONTRADICTION
        'distinguishes'| 'distinguishes'      | MeshVocabulary.CONTRADICTION
        'bound-by'     | 'bound by'           | MeshVocabulary.GOVERNANCE
        'authorised-by'| 'authorised by'      | MeshVocabulary.AUTHORITY
        'relator'      | 'relator of'         | MeshVocabulary.AUTHORITY
        'party-to'     | 'party to'           | MeshVocabulary.PROCEDURE
    }

    @Unroll
    def "an unknown marker '#marker' still reads by its own name"() {
        expect:
        !MeshVocabulary.isDeclared(marker)
        MeshVocabulary.label(marker) == expectedLabel
        MeshVocabulary.family(marker) == MeshVocabulary.REFERENCE

        where:
        marker       | expectedLabel
        'relates-to' | 'relates to'
        'owned_by'   | 'owned by'
        'supersedes-x'| 'supersedes x'
    }

    @Unroll
    def "cartridge lens '#lens' maps to family #family"() {
        expect:
        MeshVocabulary.familyForLens(lens) == family

        where:
        lens         | family
        'ontic'      | MeshVocabulary.CODE
        'epistemic'  | MeshVocabulary.REFERENCE
        'deontic'    | MeshVocabulary.GOVERNANCE
        'structural' | MeshVocabulary.STRUCTURE
        'unknown'    | MeshVocabulary.REFERENCE
        null         | MeshVocabulary.REFERENCE
    }
}