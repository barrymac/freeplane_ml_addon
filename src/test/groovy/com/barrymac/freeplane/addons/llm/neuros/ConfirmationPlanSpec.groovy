package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

@Subject(ConfirmationPlan)
class ConfirmationPlanSpec extends Specification {

    @TempDir
    File directory

    private File fixture() {
        File file = new File(directory, 'candidates.json')
        file.text = new File('demo/confirmation/candidates.json').text
        return file
    }

    def "the fixture loads with variants, source classes and weights"() {
        when:
        List<ConfirmCandidate> candidates = ConfirmationPlan.load(fixture())

        then:
        candidates.size() == 3
        candidates[0].canonical == 'Maria de Fátima Silva Carvalho'
        candidates[0].variants.size() == 4
        candidates[0].strongest() == 1.0
        candidates.every { it.status == 'hollow' }
    }

    def "a hollow candidate is never writable"() {
        given:
        def candidates = ConfirmationPlan.load(fixture())

        expect:
        !ConfirmationPlan.isWritable(candidates[0])
        ConfirmationPlan.diff(candidates).contains('hollow: Maria de Fátima')
        ConfirmationPlan.diff(candidates).contains('0 of 3 candidates would write')
    }

    def "confirmation with strong evidence is writable and produces marker lines"() {
        given:
        def candidates = ConfirmationPlan.load(fixture())
        ConfirmationPlan.recordDecision(null, candidates[0], 'confirm')

        expect:
        ConfirmationPlan.isWritable(candidates[0])
        ConfirmationPlan.diff(candidates).contains('[[judge:Maria de Fátima Silva Carvalho]]')
    }

    def "merge needs a named target; split and reject write nothing"() {
        given:
        def candidates = ConfirmationPlan.load(fixture())
        ConfirmationPlan.recordDecision(null, candidates[0], 'merge', 'Maria de Fátima Silva Carvalho')
        ConfirmationPlan.recordDecision(null, candidates[1], 'split')
        ConfirmationPlan.recordDecision(null, candidates[2], 'reject')

        expect:
        candidates[0].status == 'merged'
        candidates[1].status == 'split'
        candidates[2].status == 'rejected'
        !ConfirmationPlan.isWritable(candidates[1])
        !ConfirmationPlan.isWritable(candidates[2])
        ConfirmationPlan.diff(candidates).contains('split: Marielza')
        ConfirmationPlan.diff(candidates).contains('none: Maria de Fache')
    }

    def "weak evidence cannot be written even when confirmed"() {
        given:
        def candidates = ConfirmationPlan.load(fixture())
        ConfirmationPlan.recordDecision(null, candidates[1], 'confirm')

        expect:
        candidates[1].strongest() == 0.4
        !ConfirmationPlan.isWritable(candidates[1])
    }

    def "decisions are appended to a local log with the evidence strength"() {
        given:
        def candidates = ConfirmationPlan.load(fixture())
        File log = new File(directory, 'decisions.log')

        when:
        ConfirmationPlan.recordDecision(log, candidates[0], 'merge', 'Maria de Fátima Silva Carvalho')

        then:
        log.isFile()
        log.text.contains('merge')
        log.text.contains('Maria de Fátima Silva Carvalho')
        log.text.contains('1.0')
    }

    def "a missing candidates file yields nothing rather than an error"() {
        expect:
        ConfirmationPlan.load(new File(directory, 'absent.json')).isEmpty()
        ConfirmationPlan.load(null).isEmpty()
    }
}