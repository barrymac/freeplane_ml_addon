package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject

@Subject(ScopeHealth)
class ScopeHealthSpec extends Specification {

    def "EAGAIN is a drain, never an empty mesh"() {
        given:
        def failure = new IOException('readdir failed: errno 11 (EAGAIN) — transient not-ready')

        expect:
        ScopeHealth.classify(0, failure) == ScopeHealth.DEGRADED
        ScopeHealth.isNotReady(failure)
        ScopeHealth.describe(ScopeHealth.DEGRADED).contains('try again')
        !ScopeHealth.describe(ScopeHealth.DEGRADED).contains('real absence')
    }

    def "a healthy empty result is a real absence"() {
        expect:
        ScopeHealth.classify(0, null) == ScopeHealth.EMPTY
        ScopeHealth.describe(ScopeHealth.EMPTY).contains('real absence')
    }

    def "content present is ok"() {
        expect:
        ScopeHealth.classify(9, null) == ScopeHealth.OK
    }

    def "a wired-but-wrong edge is a defect, not a gap"() {
        given:
        def diagnosis = new MeshGaps.Diagnosis(kind: MeshGaps.WRONG)

        expect:
        diagnosis.remedyText().contains('Explicit deletion required')
        !diagnosis.remedyText().contains('image upgrade')
    }

    def "degraded read does not become an enmeshment gap"() {
        when:
        def diagnosis = MeshGaps.diagnose(0, 0, ScopeHealth.DEGRADED)

        then:
        diagnosis.kind == MeshGaps.LINKED
    }
}
