package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject

@Subject(SearchContextBuilder)
class SearchContextBuilderSpec extends Specification {

    def "without context the query is exactly the question"() {
        expect:
        SearchContextBuilder.build('scope boundaries') == 'scope boundaries'
    }

    def "ancestors and children are folded into one natural-language query"() {
        when:
        String query = SearchContextBuilder.build(
                'scope boundaries',
                ['neurOS add-on', 'safe knowledge exploration'],
                ['cross-scope doors', 'explicit confirmation'])

        then:
        query.startsWith('scope boundaries\n\n')
        query.contains('Context: neurOS add-on > safe knowledge exploration')
        query.contains('Related ideas: cross-scope doors; explicit confirmation')
    }

    def "context limits are honoured without issuing extra queries"() {
        when:
        String query = SearchContextBuilder.build(
                'core question',
                ['one', 'two', 'three'],
                ['child one', 'child two'],
                '',
                [ancestorLimit: 2, childLimit: 1])

        then:
        query.contains('Context: one > two')
        !query.contains('three')
        query.contains('Related ideas: child one')
        !query.contains('child two')
    }

    def "duplicates and the question itself do not pollute the context"() {
        when:
        String query = SearchContextBuilder.build(
                'core question',
                ['core question', 'core question', 'useful parent'],
                ['useful parent'])

        then:
        query.contains('Context: useful parent')
        !query.contains('Related ideas')
    }

    def "notes are included with a bounded, readable fragment"() {
        when:
        String query = SearchContextBuilder.build(
                'core question', [], [], 'A detailed note about boundaries.',
                [noteLimit: 20])

        then:
        query.contains('Notes: A detailed note…')
    }

    def "the total context budget truncates context but never the question"() {
        when:
        String query = SearchContextBuilder.build(
                'core question',
                ['a rather long ancestor title that consumes budget'],
                [],
                '',
                [totalLimit: 60])

        then:
        query.startsWith('core question\n\n')
        query.contains('…')
        query.length() <= 65
    }

    def "blank and over-long questions fail loudly"() {
        when:
        SearchContextBuilder.build('   ')

        then:
        thrown(IllegalArgumentException)

        when:
        SearchContextBuilder.build('x' * (SearchContextBuilder.MAX_QUESTION_CHARS + 1))

        then:
        thrown(IllegalArgumentException)
    }

    def "titleFragment uses only the first line and normalises whitespace"() {
        expect:
        SearchContextBuilder.titleFragment('First line\nSecond line') == 'First line'
        SearchContextBuilder.titleFragment('  spaced    title  ') == 'spaced title'
    }
}
