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

    def "duplicate copies of one decision count as one independent source"() {
        given:
        def candidate = new ConfirmCandidate(canonical: 'Maria de Fátima Silva Carvalho', proposedType: 'judge')
        candidate.variants << new ConfirmCandidate.Variant(
                name: 'Maria de Fátima', sourceClass: 'official-header',
                sourceId: 'escavador-copy-1', evidenceId: 'PJe-93533725', weight: 1.0)
        candidate.variants << new ConfirmCandidate.Variant(
                name: 'Desa. Maria de Fátima', sourceClass: 'official-header',
                sourceId: 'escavador-copy-2', evidenceId: 'PJe-93533725', weight: 1.0)
        candidate.variants << new ConfirmCandidate.Variant(
                name: 'Maria de Fátima', sourceClass: 'official-header',
                sourceId: 'djen-cert', evidenceId: 'PJe-94266481', weight: 1.0)

        expect:
        candidate.variants.size() == 3
        candidate.independentSources() == 2
    }

    def "source-class granularity is preserved rather than collapsed to 1.0"() {
        given:
        def candidates = ConfirmationPlan.load(fixture())

        expect:
        candidates[0].variants.collect { it.sourceClass } ==
                ['official-header', 'court-header', 'filing-caption', 'transcript']
        candidates[0].variants.collect { it.weight } == [1.0, 0.95, 0.7, 0.25]
    }

    def "a human-attested decision records the attesting name"() {
        given:
        def candidates = ConfirmationPlan.load(fixture())
        File log = new File(directory, 'decisions.log')

        when:
        ConfirmationPlan.recordDecision(log, candidates[0], 'confirm', null, 'barrymac')

        then:
        log.text.contains('barrymac')
        !log.text.contains('unattested')
    }

    def "an unattended decision is recorded as such, not silently accepted"() {
        given:
        def candidates = ConfirmationPlan.load(fixture())
        File log = new File(directory, 'decisions.log')

        when:
        ConfirmationPlan.recordDecision(log, candidates[0], 'confirm')

        then:
        log.text.contains('unattested')
    }

    def "a missing candidates file yields nothing rather than an error"() {
        expect:
        ConfirmationPlan.load(new File(directory, 'absent.json')).isEmpty()
        ConfirmationPlan.load(null).isEmpty()
    }
}