package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir
import spock.lang.Unroll

@Subject(WriteBack)
class WriteBackSpec extends Specification {

    @TempDir
    File workspace

    private ConfirmCandidate maria() {
        def c = new ConfirmCandidate(
                id: 'maria', canonical: 'Maria de Fátima Silva Carvalho', proposedType: 'judge')
        c.status = 'merged'
        c.mergeTarget = 'Maria de Fátima Silva Carvalho'
        c.decision = 'merge'
        c.variants << new ConfirmCandidate.Variant(name: 'Maria de Fátima Silva Carvalho',
                sourceClass: 'official-header', sourceId: 'doc-1', weight: 1.0)
        c.variants << new ConfirmCandidate.Variant(name: 'Desa. Maria de Fátima',
                sourceClass: 'court-header', sourceId: 'doc-2', weight: 0.95)
        c.variants << new ConfirmCandidate.Variant(name: 'Maria de Fache',
                sourceClass: 'transcript', sourceId: 'doc-3', weight: 0.25)
        c.excludedVariants = ['Maria de Fache'] as Set
        return c
    }

    def "a merged candidate writes a card file with the right marker and exclusions"() {
        when:
        WriteBack.Outcome outcome = WriteBack.write([maria()], workspace, 'barrymac')

        then:
        outcome.written.size() == 1
        File card = outcome.written[0]
        card.isFile()
        card.name.startsWith('CARD-')
        card.name.endsWith('.card.md')
        String text = card.text
        text.contains('[[judge:Maria de Fátima Silva Carvalho]]')
        text.contains('# Maria de Fátima Silva Carvalho')
        text.contains('list: Backlog')
        text.contains('type: judge')
        text.contains('Merged into Maria de Fátima Silva Carvalho')
        text.contains('EXCLUDED: Maria de Fache')
        !text.contains('variant: Maria de Fache')
        text.contains('Attested by: barrymac')
    }

    @Unroll
    def "a candidate with status #status and weight #weight writes #expected file(s)"() {
        given:
        def c = new ConfirmCandidate(canonical: 'Test Judge', proposedType: 'judge')
        c.status = status
        c.mergeTarget = mergeTarget
        c.excludedVariants = [] as Set
        c.variants << new ConfirmCandidate.Variant(name: 'v', sourceClass: 'official-header',
                sourceId: 'doc', weight: weight)

        when:
        WriteBack.Outcome outcome = WriteBack.write([c], workspace, 'tester')

        then:
        outcome.written.size() == expected

        where:
        status      | weight | mergeTarget | expected
        'confirmed' | 0.90   | null        | 1
        'merged'    | 0.90   | 'Canonical' | 1
        'merged'    | 0.90   | null        | 0
        'split'     | 1.00   | null        | 0
        'rejected'  | 1.00   | null        | 0
        'confirmed' | 0.50   | null        | 0
        'hollow'    | 1.00   | null        | 0
    }

    def "the summary is honest about what happened"() {
        when:
        WriteBack.Outcome nothing = WriteBack.write([], workspace, 'tester')
        WriteBack.Outcome something = WriteBack.write([maria()], workspace, 'tester')

        then:
        nothing.summary.contains('Nothing confirmed enough')
        something.summary.contains('1 card file')
        something.summary.contains(workspace.path)
    }

    def "a null workspace fails loudly"() {
        when:
        WriteBack.write([maria()], null, 'tester')

        then:
        def problem = thrown(IllegalArgumentException)
        problem.message.contains('No local watched workspace')
    }
}