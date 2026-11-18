package com.barrymac.freeplane.addons.llm.judgement

import spock.lang.Specification
import spock.lang.Subject

@Subject(PrivacyCheck)
class PrivacyCheckSpec extends Specification {

    private static Judgement.Answer check(String name, double probability) {
        return new Judgement.Answer(question: name, type: 'noul', probability: probability)
    }

    def "the worst single check decides, because several can be true at once"() {
        when:
        def result = PrivacyCheck.classify([
                check('has_uuid', 0.12),
                check('has_private_path', 0.99),
                check('has_secret', 0.03)])

        then:
        result.status == PrivacyCheck.UNSAFE
        result.worstCheck == 'has_private_path'
        result.worst == 0.99
        !result.publishable
        result.summary().contains('private path')
    }

    def "the uncertain band stays uncertain rather than rounding to safe"() {
        when:
        def result = PrivacyCheck.classify([check('has_uuid', 0.43)])

        then:
        result.status == PrivacyCheck.REVIEW
        !result.publishable
        result.summary().contains('by eye')
    }

    def "a low signal everywhere is safe to publish"() {
        when:
        def result = PrivacyCheck.classify([
                check('has_uuid', 0.05),
                check('has_hostname', 0.02)])

        then:
        result.status == PrivacyCheck.SAFE
        result.publishable
        result.summary().contains('Nothing private')
    }

    def "every check the cartridge asks is represented in the result"() {
        given:
        def answers = PrivacyCheck.CHECKS.keySet().collect { check(it, 0.1) }

        when:
        def result = PrivacyCheck.classify(answers)

        then:
        result.probabilities.keySet() == PrivacyCheck.CHECKS.keySet()
        result.probabilities.size() == 5
    }

    def "answers to questions we did not ask are ignored"() {
        when:
        def result = PrivacyCheck.classify([
                check('has_uuid', 0.9),
                new Judgement.Answer(question: 'verdict', type: 'choice', choice: 'safe', confidence: 0.9)])

        then:
        result.probabilities.keySet() == ['has_uuid'] as Set
        result.status == PrivacyCheck.UNSAFE
    }

    def "an answer set without any known check fails loudly"() {
        when:
        PrivacyCheck.classify([check('something_else', 0.5)])

        then:
        thrown(IllegalStateException)
    }

    def "asking without a key says what to set"() {
        given:
        File image = File.createTempFile('shot', '.png')
        image.deleteOnExit()
        image.bytes = [0x89, 0x50, 0x4e, 0x47] as byte[]

        when:
        PrivacyCheck.check(image, '')

        then:
        def problem = thrown(IllegalStateException)
        problem.message.contains('JEV_API_KEY')
    }
}