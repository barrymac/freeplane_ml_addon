package com.barrymac.freeplane.addons.llm.neuros

import com.barrymac.freeplane.addons.llm.judgement.EnmeshProposal
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

@Subject(MeshVocabulary)
class ContractVocabularySpec extends Specification {

    @TempDir
    File directory

    private Map<String, List<String>> saved

    def setup() {
        saved = new LinkedHashMap<>(MeshVocabulary.DECLARED)
    }

    def cleanup() {
        MeshVocabulary.DECLARED.clear()
        MeshVocabulary.DECLARED.putAll(saved)
    }

    private File contract() {
        File file = new File(directory, 'cartridge-contract')
        file.text = '''{
  "ontologyId": "sys:hohfeldian-v1",
  "markerGrammar": [
    {"markerToken": "presides-over", "edgeName": "PRESIDES_OVER", "lens": "deontic"},
    {"markerToken": "cites", "edgeName": "REFERENCES", "lens": "epistemic"},
    {"markerToken": "bound-by", "edgeName": "BOUND_BY", "lens": "deontic"},
    {"markerToken": "future-travel-marker", "edgeName": "LOCATED_IN", "lens": "structural"}
  ]
}'''
        return file
    }

    def "the cartridge's declared markers become lens candidates without code changes"() {
        when:
        MeshVocabulary.loadContract(contract())

        then:
        MeshVocabulary.isDeclared('presides-over')
        MeshVocabulary.isDeclared('cites')
        MeshVocabulary.isDeclared('bound-by')
        MeshVocabulary.isDeclared('future-travel-marker')
        MeshVocabulary.label('presides-over') == 'presides over'
        MeshVocabulary.family('bound-by') == MeshVocabulary.GOVERNANCE
        MeshVocabulary.family('future-travel-marker') == MeshVocabulary.STRUCTURE
        // A curated family wins over the lens default: cites reads as citation,
        // not as a generic reference.
        MeshVocabulary.family('cites') == MeshVocabulary.CITATION
    }

    def "lens families derive from the cartridge, not from a fixed list"() {
        when:
        MeshVocabulary.loadContract(contract())

        then:
        MeshLens.families().containsKey(MeshVocabulary.AUTHORITY)
        MeshLens.families().containsKey(MeshVocabulary.GOVERNANCE)
        MeshLens.allFamilies().contains(MeshVocabulary.AUTHORITY)
    }

    def "enmeshment criteria include the cartridge's own relationships"() {
        when:
        MeshVocabulary.loadContract(contract())
        Map<String, String> criteria = EnmeshProposal.criteria()

        then:
        criteria.containsKey('presides_over')
        criteria.containsKey('cites')
        criteria.containsKey('bound_by')
        criteria.containsKey('future_travel_marker')
        criteria.containsKey('none')
        criteria.containsKey('cannot_tell')
    }

    def "an unreadable contract leaves the base vocabulary intact"() {
        given:
        int before = MeshVocabulary.DECLARED.size()

        when:
        MeshVocabulary.loadContract(new File(directory, 'absent'))

        then:
        MeshVocabulary.DECLARED.size() == before
    }
}