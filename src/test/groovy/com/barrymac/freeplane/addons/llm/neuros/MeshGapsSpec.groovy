package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(MeshGaps)
class MeshGapsSpec extends Specification {

    def "zero declared edges is an enmeshment gap"() {
        when:
        def d = MeshGaps.diagnose(0, 0)

        then:
        d.kind == MeshGaps.NO_LINKS
        d.message().contains('richer than its links')
        d.tag() == MeshGaps.TAG_NO_LINKS
    }

    def "declared but zero resolved is a substrate gap"() {
        when:
        def d = MeshGaps.diagnose(4, 0)

        then:
        d.kind == MeshGaps.UNRESOLVED
        d.declared == 4
        d.unresolved == 4
        d.message().contains('substrate gap')
        d.tag() == MeshGaps.TAG_UNRESOLVED
    }

    @Unroll
    def "declared=#declared resolved=#resolved is #expectedKind"() {
        when:
        def d = MeshGaps.diagnose(declared, resolved)

        then:
        d.kind == expectedKind
        d.unresolved == expectedUnresolved

        where:
        declared | resolved | expectedKind        | expectedUnresolved
        0        | 0        | MeshGaps.NO_LINKS   | 0
        1        | 0        | MeshGaps.UNRESOLVED | 1
        1        | 1        | MeshGaps.LINKED     | 0
        5        | 3        | MeshGaps.LINKED     | 2
        3        | 0        | MeshGaps.UNRESOLVED | 3
    }

    def "a degraded scope never reports an enmeshment gap"() {
        when:
        def d = MeshGaps.diagnose(0, 0, ScopeHealth.DEGRADED)

        then:
        d.kind == MeshGaps.LINKED
        d.tag() == null
    }

    def "a wired-and-wrong edge names its remedy"() {
        when:
        def d = new MeshGaps.Diagnosis(kind: MeshGaps.WRONG)

        then:
        d.remedyText().contains('Explicit deletion')
        !d.remedyText().contains('image upgrade')
        d.message().contains('evidentiary defect')
    }

    def "the scope report deduplicates entries"() {
        given:
        MockIndex index = new MockIndex()

        when:
        Map report = MeshGaps.report(index)

        then:
        report.noLinks.unique().size() == report.noLinks.size()
    }

    /** A minimal fake index so report() can be tested without a filesystem. */
    private static class MockIndex extends MeshIndex {
        MockIndex() {
            super()
        }
    }
}