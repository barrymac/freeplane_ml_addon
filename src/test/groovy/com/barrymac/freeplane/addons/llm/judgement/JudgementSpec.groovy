package com.barrymac.freeplane.addons.llm.judgement

import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException
import spock.lang.Specification
import spock.lang.Subject

@Subject(Judgement)
class JudgementSpec extends Specification {

    def "the decision payload carries state and named questions"() {
        given:
        def state = [c0: [atom: 'claim A'], c1: [atom: 'claim B']]
        Map<String, Map> questions = [
                q0: [type   : 'choice',
                     instructions: 'Do these claims contradict?',
                     criteria: [contradicts   : 'They cannot both hold',
                                not_contradiction: 'Both can hold',
                                cannot_tell  : 'Not enough to judge']]
        ]

        when:
        Map payload = Judgement.payloadFor(Judgement.Transport.DECISION,
                Judgement.DECISION_MODEL, state, questions)

        then:
        payload.model == 'inception/mercury-decide'
        payload.state == state
        payload.questions.q0.criteria.keySet() == ['contradicts', 'not_contradiction', 'cannot_tell'] as Set
    }

    def "a choice answer reads with its confidence and distribution"() {
        given:
        String response = '''{"answers":{"q0":{"type":"choice","choice":"contradicts",
            "confidence":0.91,"probabilities":{"contradicts":0.91,"not_contradiction":0.06,"cannot_tell":0.03}}}}'''

        when:
        List<Judgement.Answer> answers = Judgement.parse(Judgement.Transport.DECISION, response)

        then:
        answers.size() == 1
        def answer = answers[0]
        answer.choice == 'contradicts'
        answer.confidence == 0.91
        answer.probabilities['cannot_tell'] == 0.03
        answer.decisive
    }

    def "a low-confidence answer is not actionable"() {
        given:
        String response = '{"answers":{"q0":{"type":"choice","choice":"contradicts","confidence":0.41}}}'

        expect:
        !Judgement.parse(Judgement.Transport.DECISION, response)[0].decisive
    }

    def "an escape is never turned into a verdict"() {
        given:
        String response = '{"answers":{"q0":{"type":"choice","choice":"cannot_tell","confidence":0.88}}}'

        when:
        def answer = Judgement.parse(Judgement.Transport.DECISION, response)[0]

        then:
        answer.escape
        !answer.decisive
    }

    def "a probability answer is actionable above the threshold"() {
        given:
        String response = '{"answers":{"n0":{"type":"noul","noul":0.83}}}'

        when:
        def answer = Judgement.parse(Judgement.Transport.JEV, response)[0]

        then:
        answer.type == 'noul'
        answer.probability == 0.83
        answer.decisive
    }

    def "a score answer keeps its expected value"() {
        given:
        String response = '{"answers":{"s0":{"type":"score","score":1.7,"confidence":0.55,"legend":{},"probabilities":{}}}}'

        when:
        def answer = Judgement.parse(Judgement.Transport.JEV, response)[0]

        then:
        answer.probability == 1.7
        answer.confidence == 0.55
    }

    def "no answers is an error, not an empty verdict list"() {
        when:
        Judgement.parse(Judgement.Transport.DECISION, '{"usage":{}}')

        then:
        thrown(LlmAddonException)
    }

    def "an empty response is rejected"() {
        when:
        Judgement.parse(Judgement.Transport.DECISION, '')

        then:
        thrown(LlmAddonException)
    }

    def "the model and endpoint can be replaced from the environment"() {
        given:
        def environment = [DECISION_MODEL: 'vendor/decide-v2',
                           DECISION_API_URL: 'https://example.test/decisions',
                           JEV_MODEL: 'jev-preview',
                           JEV_API_URL: 'https://example.test/systemone']

        expect:
        Judgement.modelFor(Judgement.Transport.DECISION, environment) == 'vendor/decide-v2'
        Judgement.endpointFor(Judgement.Transport.DECISION, environment) == 'https://example.test/decisions'
        Judgement.modelFor(Judgement.Transport.JEV, environment) == 'jev-preview'
        Judgement.endpointFor(Judgement.Transport.JEV, environment) == 'https://example.test/systemone'
    }

    def "without overrides the documented defaults are used"() {
        expect:
        Judgement.modelFor(Judgement.Transport.JEV, [:]) == 'jev-latest'
        Judgement.endpointFor(Judgement.Transport.JEV, [:]) == 'https://api.typesafe.ai/v1/systemone'
        Judgement.modelFor(Judgement.Transport.DECISION, [:]) == 'inception/mercury-decide'
    }

    def "asking without a key fails loudly with what to set"() {
        when:
        Judgement.ask(Judgement.Transport.JEV, [a: 1], [q0: [type: 'noul', instructions: 'x']], '')

        then:
        def problem = thrown(LlmAddonException)
        problem.message.contains('JEV_API_KEY')
    }
}