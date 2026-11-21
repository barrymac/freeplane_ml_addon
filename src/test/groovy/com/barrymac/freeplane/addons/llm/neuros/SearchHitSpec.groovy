package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject

/**
 * Search rows become human titles, never machine text. Fixtures are the exact
 * names the live by-query surface answered with on 2026-11-21 (brockenhurst).
 */
@Subject(SearchHit)
class SearchHitSpec extends Specification {

    def "a plain file name passes through unchanged"() {
        expect:
        new SearchHit(displayName: '05-ci-architecture.puml', subtype: 'diagram').prettyName() ==
                '05-ci-architecture.puml'
        new SearchHit(displayName: 'Brockenhurst.mm', subtype: 'file').prettyName() ==
                'Brockenhurst.mm'
    }

    def "hashed evidence store names collapse to their human words"() {
        given:
        String blob = '5c6c44b45215460957c881cb178a6982b7fb844d4a80f42ab175570cf6395b86' +
                '.48eaf1226a2d42f5a3fcf7d9b01a3da7f19c333a11fc4fcd228aaf7446d05d54' +
                '.claimjudgements.sha256-356c4d5354be269511a31f7b76df77884e3aee194190e5d6056354456c13719b.evidence'

        when:
        String title = new SearchHit(displayName: blob, subtype: 'evidence').prettyName()

        then:
        title == 'Claimjudgements evidence'
    }

    def "every type of machine segment collapses"() {
        expect:
        new SearchHit(displayName: '48eaf1226a2d42f5a3fcf7d9b01a3da7f19c333a11fc4fcd228aaf7446d05d54' +
                '.gate.sha256-60596f072e13b2ab7203688325c69e8b9731f41a1009d369fb4f8d357a6acece.evidence',
                subtype: 'evidence').prettyName() == 'Gate evidence'
        new SearchHit(displayName: '5c6c44b45215460957c881cb178a6982b7fb844d4a80f42ab175570cf6395b86' +
                '.backend-host.sha256-bf2a2853786384ce3666feebb7659f903d8b28c0cde91a2d8dd32d333334e95a.evidence',
                subtype: 'evidence').prettyName() == 'Backend host evidence'
    }

    def "a name that is pure machine text falls back to the subtype"() {
        expect:
        new SearchHit(displayName: '5c6c44b45215460957c881cb178a6982b7fb844d4a80f42ab175570cf6395b86',
                subtype: 'file').prettyName() == 'File'
        new SearchHit(displayName: '3b8a9e50-0a51-45a6-b6e1-10fb593118e1', subtype: null).prettyName() ==
                'Knowledge entry'
    }

    def "an empty name with no subtype stays honest"() {
        expect:
        new SearchHit(displayName: '', subtype: null).prettyName() == 'Knowledge entry'
        new SearchHit(displayName: null, subtype: 'diagram').prettyName() == 'Diagram'
    }
}
