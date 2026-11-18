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
        battery.state.keySet() == ['selected', 'c0', 'c1', 'c2'] as Set
        battery.questions.keySet() == ['r0', 'r1', 'r2'] as Set
        battery.questions.r0.criteria.keySet().containsAll(
                ['selected_depends_on_candidate', 'candidate_depends_on_selected',
                 'selected_blocks_candidate', 'candidate_blocks_selected', 'references',
                 'tracks', 'contains', 'none', 'cannot_tell'])
        battery.questions.r0.instructions.contains('Entry 1')
        battery.questions.r0.instructions.contains('Selected entry')
        battery.state.selected.title == "Selected entry"
    }

    def "stage one ranks candidates by the probability that an edge is warranted"() {
        given:
        def answers = [
                new Judgement.Answer(question: 'w0', type: 'noul', probability: 0.12),
                new Judgement.Answer(question: 'w1', type: 'noul', probability: 0.91),
                new Judgement.Answer(question: 'w2', type: 'noul', probability: 0.64),
                new Judgement.Answer(question: 'w3', type: 'noul', probability: 0.30)
        ]

        when:
        List<Integer> shortlist = EnmeshProposal.shortlisted(answers, 4, 10, 0.5)

        then:
        shortlist == [1, 2]
    }

    def "stage one can shortlist everything, or nothing"() {
        given:
        def strong = (0..<3).collect { new Judgement.Answer(question: "w${it}", type: 'noul', probability: 0.8) }
        def weak = (0..<3).collect { new Judgement.Answer(question: "w${it}", type: 'noul', probability: 0.1) }

        expect:
        EnmeshProposal.shortlisted(strong, 3, 2, 0.5) == [0, 1]
        EnmeshProposal.shortlisted(weak, 3, 10, 0.5).isEmpty()
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
                        choice: 'selected_depends_on_candidate', confidence: 0.4)]).isEmpty()
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
        choice                        | family
        'selected_depends_on_candidate'| MeshVocabulary.DEPENDENCY
        'candidate_depends_on_selected'| MeshVocabulary.DEPENDENCY
        'selected_blocks_candidate'    | MeshVocabulary.BLOCKING
        'references'                   | MeshVocabulary.REFERENCE
        'tracks'                       | MeshVocabulary.TRACKING
        'contains'                     | MeshVocabulary.STRUCTURE
    }
}