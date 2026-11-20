package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(ConfirmationPlan)
class ConfirmationPlanDataSpec extends Specification {

    private static ConfirmCandidate candidate(String status, double weight, String mergeTarget = null) {
        def c = new ConfirmCandidate(canonical: 'Maria de Fátima Silva Carvalho', proposedType: 'judge')
        c.status = status
        c.mergeTarget = mergeTarget
        c.variants << new ConfirmCandidate.Variant(name: 'v', sourceClass: 'official-header',
                sourceId: 'doc-1', weight: weight)
        return c
    }

    @Unroll
    def "status #status with weight #weight is writable? #expected"() {
        given:
        def c = candidate(status, weight, mergeTarget)

        expect:
        ConfirmationPlan.isWritable(c) == expected

        where:
        status     | weight | mergeTarget | expected
        'hollow'   | 1.0    | null        | false
        'confirmed'| 0.80   | null        | true
        'confirmed'| 0.74   | null        | false
        'confirmed'| 0.75   | null        | true
        'merged'   | 0.90   | 'Canonical' | true
        'merged'   | 0.90   | null        | false
        'split'    | 1.0    | null        | false
        'rejected' | 1.0    | null        | false
    }

    @Unroll
    def "an outlier '#outlier' is excluded from the diff as excluded"() {
        given:
        def c = candidate('confirmed', 1.0)
        c.variants << new ConfirmCandidate.Variant(name: outlier, sourceClass: 'transcript',
                sourceId: 'audio', weight: 0.25)
        c.excludedVariants = [outlier] as Set

        when:
        String diff = ConfirmationPlan.diff([c])

        then:
        diff.contains("excluded: ${outlier}")
        !diff.contains("variant: ${outlier}")

        where:
        outlier << ['Maria de Fache', 'Desa.-truncated', 'Dr. Maria de Fátima']
    }
}