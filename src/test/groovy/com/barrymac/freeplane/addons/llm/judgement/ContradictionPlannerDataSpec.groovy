package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import com.barrymac.freeplane.addons.llm.judgement.ContradictionPlanner
import com.barrymac.freeplane.addons.llm.judgement.Judgement

/**
 * The judgement contract for the contradiction adjudicator: every combination
 * of verdict, confidence and conditionality, and the resolution each produces.
 */
@Subject(ContradictionPlanner)
class ContradictionPlannerDataSpec extends Specification {

    private static ContradictionPlanner.Pair pair() {
        return new ContradictionPlanner.Pair(
                leftId: 'a', leftTitle: 'A', leftText: 'Claim A',
                rightId: 'b', rightTitle: 'B', rightText: 'Claim B')
    }

    private static Judgement.Answer verdict(String choice, double confidence) {
        return new Judgement.Answer(question: 'q0', type: 'choice',
                choice: choice, confidence: confidence)
    }

    private static Judgement.Answer conditional(double probability) {
        return new Judgement.Answer(question: 'n0', type: 'noul', probability: probability)
    }

    @Unroll
    def "verdict '#choice'@#confidence + conditional #conditionalP → #expectedKind"() {
        given:
        def answers = [verdict(choice, confidence)]
        if (conditionalP >= 0) answers << conditional(conditionalP)

        when:
        def resolution = ContradictionPlanner.resolve([pair()], answers)[0]

        then:
        resolution.kind == expectedKind

        where:
        choice             | confidence | conditionalP | expectedKind
        'contradicts'      | 0.95       | -1           | ContradictionPlanner.CONTRADICTS
        'contradicts'      | 0.75       | -1           | ContradictionPlanner.CONTRADICTS
        'contradicts'      | 0.74       | -1           | ContradictionPlanner.UNRESOLVED
        'contradicts'      | 0.50       | -1           | ContradictionPlanner.UNRESOLVED
        'not_contradiction'| 0.90       | 0.90         | ContradictionPlanner.RECONCILES
        'not_contradiction'| 0.90       | 0.75         | ContradictionPlanner.RECONCILES
        'not_contradiction'| 0.90       | 0.74         | ContradictionPlanner.UNRESOLVED
        'not_contradiction'| 0.90       | -1           | ContradictionPlanner.UNRESOLVED
        'cannot_tell'      | 0.99       | -1           | ContradictionPlanner.UNRESOLVED
        'none'             | 0.99       | -1           | ContradictionPlanner.UNRESOLVED
    }

    @Unroll
    def "an escape '#choice' is reported with its reason in the ground"() {
        given:
        def answers = [verdict(choice, 0.99)]

        when:
        def resolution = ContradictionPlanner.resolve([pair()], answers)[0]

        then:
        resolution.kind == ContradictionPlanner.UNRESOLVED
        resolution.ground.contains(expectedFragment)

        where:
        choice        | expectedFragment
        'cannot_tell' | 'cannot_tell'
        'none'        | 'judged unclear'
    }

    @Unroll
    def "a #label resolution #desc"() {
        given:
        def answers = [verdict(verdictChoice, verdictConfidence)]
        if (condP >= 0) answers << conditional(condP)
        def resolution = ContradictionPlanner.resolve([pair()], answers)[0]

        expect:
        resolution.isDrawable() == expectedDrawable

        where:
        label             | verdictChoice      | verdictConfidence | condP | expectedDrawable | desc
        'contradiction'   | 'contradicts'      | 0.95              | -1    | true             | 'is drawable'
        'reconciliation'  | 'not_contradiction'| 0.95              | 0.90  | true             | 'is drawable'
        'low-confidence'  | 'contradicts'      | 0.50              | -1    | false            | 'is not drawable'
        'no-condition'    | 'not_contradiction'| 0.95              | -1    | false            | 'is not drawable'
        'escape'          | 'cannot_tell'      | 0.95              | -1    | false            | 'is not drawable'
    }
}