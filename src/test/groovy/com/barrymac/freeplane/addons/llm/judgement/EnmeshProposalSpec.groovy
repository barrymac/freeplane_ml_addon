package com.barrymac.freeplane.addons.llm.judgement

import com.barrymac.freeplane.addons.llm.neuros.MeshVocabulary
import spock.lang.Specification
import spock.lang.Subject

@Subject(EnmeshProposal)
class EnmeshProposalSpec extends Specification {

    private static List<Map> candidates(int count) {
        (1..count).collect { number ->
                [id: "cand-${number}".toString(),
                 title: "Entry ${number}".toString(),
                 text: 'Some material about the entry.'.toString()]
        }
    }

    def "the battery asks one relationship question per candidate"() {
        when:
        Map battery = EnmeshProposal.battery(candidates(3), 'Selected entry')

        then:
        battery.state.keySet() == ['seed', 'c0', 'c1', 'c2'] as Set
        battery.questions.keySet() == ['r0', 'r1', 'r2'] as Set
        battery.questions.r0.criteria.keySet().containsAll(
                ['depends_on', 'blocks', 'references', 'tracks', 'contains', 'reconciles',
                 'none', 'cannot_tell'])
        battery.state.seed.title == 'Selected entry'
    }

    def "a confident choice becomes a provisional edge in the right family"() {
        given:
        def answer = new Judgement.Answer(question: 'r0', type: 'choice',
                choice: 'references', confidence: 0.88)

        when:
        def proposals = EnmeshProposal.proposals(candidates(1), 'seed-id', [answer])

        then:
        proposals.size() == 1
        def edge = proposals[0]
        edge.source == 'seed-id'
        edge.target == 'cand-1'
        edge.marker == 'references'
        edge.family == MeshVocabulary.REFERENCE
        edge.label == 'proposed: references'
        edge.proposed
    }

    def "none, escapes and low confidence propose nothing"() {
        expect:
        EnmeshProposal.proposals(candidates(1), 'seed-id', [
                new Judgement.Answer(question: 'r0', type: 'choice',
                        choice: 'none', confidence: 0.99)]).isEmpty()
        EnmeshProposal.proposals(candidates(1), 'seed-id', [
                new Judgement.Answer(question: 'r0', type: 'choice',
                        choice: 'cannot_tell', confidence: 0.99)]).isEmpty()
        EnmeshProposal.proposals(candidates(1), 'seed-id', [
                new Judgement.Answer(question: 'r0', type: 'choice',
                        choice: 'depends_on', confidence: 0.4)]).isEmpty()
    }

    def "a missing answer proposes nothing"() {
        expect:
        EnmeshProposal.proposals(candidates(2), 'seed-id', []).isEmpty()
    }

    def "each proposed relationship maps to its own family"() {
        expect:
        EnmeshProposal.proposals(candidates(1), 'seed',
                [new Judgement.Answer(question: 'r0', type: 'choice',
                        choice: choice, confidence: 0.9)])[0].family == family

        where:
        choice      | family
        'depends_on'| MeshVocabulary.DEPENDENCY
        'blocks'    | MeshVocabulary.BLOCKING
        'tracks'    | MeshVocabulary.TRACKING
        'contains'  | MeshVocabulary.STRUCTURE
        'reconciles'| MeshVocabulary.TRACKING
    }
}