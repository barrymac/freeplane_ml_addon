package com.barrymac.freeplane.addons.llm.judgement

import com.barrymac.freeplane.addons.llm.neuros.MeshVocabulary
import spock.lang.Specification
import spock.lang.Subject

@Subject(ContradictionPlanner)
class ContradictionPlannerSpec extends Specification {

    private static Map seed(String text = 'Filed on 30 July.') {
        [id: 'seed-id', title: 'Filing notice', text: text]
    }

    private static List<Map> candidates(int count) {
        (1..count).collect { number ->
                [id: "cand-${number}".toString(),
                 title: "Entry ${number}".toString(),
                 text: 'No filing was made before 28 August.'.toString()]
        }
    }

    def "pairs compare the selected entry with each neighbour, bounded"() {
        when:
        def pairs = ContradictionPlanner.pairs(seed(), candidates(20))

        then:
        pairs.size() == ContradictionPlanner.MAX_PAIRS
        pairs.every { it.leftId == 'seed-id' && it.rightTitle.startsWith('Entry') }
        pairs*.rightId.toSet().size() == ContradictionPlanner.MAX_PAIRS
    }

    def "entry text is flattened and cut to what a judgement needs"() {
        given:
        String longText = ('word ' * 500)

        when:
        String trimmed = ContradictionPlanner.trim(longText)

        then:
        trimmed.length() <= ContradictionPlanner.MAX_CHARS + 1
        trimmed.endsWith('…')
        !trimmed.contains('\n')
    }

    def "the battery asks one compatibility question and one conditionality question per pair"() {
        given:
        def pairs = ContradictionPlanner.pairs(seed(), candidates(2))

        when:
        Map battery = ContradictionPlanner.battery(pairs)

        then:
        battery.state.keySet() == ['c0', 'c1'] as Set
        battery.questions.keySet() == ['q0', 'n0', 'q1', 'n1'] as Set
        battery.questions.q0.criteria.keySet() ==
                ['contradicts', 'not_contradiction', 'cannot_tell'] as Set
        battery.questions.n0.type == 'noul'
        battery.state.c0.first.title == 'Filing notice'
    }

    def "a confident contradiction becomes a contradiction resolution"() {
        given:
        def pairs = ContradictionPlanner.pairs(seed(), candidates(1))
        def answers = [
                new Judgement.Answer(question: 'q0', type: 'choice',
                        choice: 'contradicts', confidence: 0.91),
                new Judgement.Answer(question: 'n0', type: 'noul', probability: 0.2)
        ]

        when:
        def resolution = ContradictionPlanner.resolve(pairs, answers)[0]

        then:
        resolution.kind == ContradictionPlanner.CONTRADICTS
        resolution.label == 'contradicts'
        resolution.family == MeshVocabulary.CONTRADICTION
        resolution.drawable
        resolution.ground.contains('0.91')
    }

    def "compatibility plus a condition becomes a reconciliation"() {
        given:
        def pairs = ContradictionPlanner.pairs(seed(), candidates(1))
        def answers = [
                new Judgement.Answer(question: 'q0', type: 'choice',
                        choice: 'not_contradiction', confidence: 0.83),
                new Judgement.Answer(question: 'n0', type: 'noul', probability: 0.88)
        ]

        when:
        def resolution = ContradictionPlanner.resolve(pairs, answers)[0]

        then:
        resolution.kind == ContradictionPlanner.RECONCILES
        resolution.drawable
        resolution.label.contains('reconciles')
    }

    def "an escape is reported as unresolved and never drawn"() {
        given:
        def pairs = ContradictionPlanner.pairs(seed(), candidates(1))
        def answers = [
                new Judgement.Answer(question: 'q0', type: 'choice',
                        choice: 'cannot_tell', confidence: 0.9)
        ]

        when:
        def resolution = ContradictionPlanner.resolve(pairs, answers)[0]

        then:
        resolution.kind == ContradictionPlanner.UNRESOLVED
        !resolution.drawable
        resolution.ground.contains('cannot_tell')
    }

    def "low confidence is not spent on a verdict"() {
        given:
        def pairs = ContradictionPlanner.pairs(seed(), candidates(1))
        def answers = [
                new Judgement.Answer(question: 'q0', type: 'choice',
                        choice: 'contradicts', confidence: 0.5)
        ]

        when:
        def resolution = ContradictionPlanner.resolve(pairs, answers)[0]

        then:
        resolution.kind == ContradictionPlanner.UNRESOLVED
        resolution.ground.contains('below')
    }

    def "a missing answer leaves the pair unresolved"() {
        given:
        def pairs = ContradictionPlanner.pairs(seed(), candidates(1))

        when:
        def resolution = ContradictionPlanner.resolve(pairs, [])[0]

        then:
        resolution.kind == ContradictionPlanner.UNRESOLVED
        resolution.ground == 'no verdict returned'
    }
}