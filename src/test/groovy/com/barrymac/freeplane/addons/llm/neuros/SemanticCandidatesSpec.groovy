package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

@Subject(SemanticCandidates)
class SemanticCandidatesSpec extends Specification {

    @TempDir
    File scope

    def "TSV rows are read as display names"() {
        given:
        String tsv = '''CARD-abc-one.card.md\t11111111-1111-1111-1111-111111111111\tcard
another-entry.groovy\t22222222-2222-2222-2222-222222222222\tclass

# a comment
malformed-row'''

        expect:
        SemanticCandidates.parse(tsv) == ['CARD-abc-one.card.md', 'another-entry.groovy']
    }

    def "the surface is queried with a readable question"() {
        given:
        File surface = new File(scope, 'by-query')
        surface.mkdirs()
        new File(surface, 'lens cards bounded diagrams').text =
                'a.card.md\t11111111-1111-1111-1111-111111111111\tcard\n'

        expect:
        SemanticCandidates.query(scope, 'lens cards bounded diagrams') == ['a.card.md']
    }

    def "a missing surface or answer returns nothing instead of failing"() {
        expect:
        SemanticCandidates.query(scope, 'anything').isEmpty()
        SemanticCandidates.query(null, 'anything').isEmpty()
        SemanticCandidates.query(scope, '   ').isEmpty()

        when:
        new File(scope, 'by-query').mkdirs()

        then:
        SemanticCandidates.query(scope, 'no such answer').isEmpty()
    }

    def "the query is short, keeps the title, and drops metadata"() {
        when:
        String query = SemanticCandidates.questionFor('Bounded expansion',
                'list: Backlog\npriority: P1\nOne hop of declared links, bounded by a lens.')

        then:
        query.startsWith('Bounded expansion')
        query.length() <= 120
        !query.contains('Backlog')
        !query.contains('priority')
        query.contains('declared')
        query.contains('lens')
    }
}
