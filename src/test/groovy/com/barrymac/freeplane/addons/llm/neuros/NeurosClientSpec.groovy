package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

@Subject(NeurosClient)
class NeurosClientSpec extends Specification {

    @TempDir
    File sourceRoot

    @TempDir
    File destination

    private KnowledgeSource openSource() {
        new File(sourceRoot, 'by-query').mkdirs()
        return KnowledgeSource.open(sourceRoot.path, 'map-scope')
    }

    def "search parses TSV rows, dedups by identifier and honours the limit"() {
        given:
        def source = openSource()
        new File(sourceRoot, 'by-query/How does branching work').text =
                'BranchGeneratorFactory.groovy\t11111111-1111-1111-1111-111111111111\tclass\n' +
                'MapUpdater.groovy\t22222222-2222-2222-2222-222222222222\tclass\n' +
                'BranchGeneratorFactory.groovy\t11111111-1111-1111-1111-111111111111\tclass\n' + // dup
                'HELP.md\t33333333-3333-3333-3333-333333333333\tfile\n'

        when:
        def hits = NeurosClient.search(source, 'How does branching work', 2)

        then:
        hits.size() == 2
        hits[0].displayName == 'BranchGeneratorFactory.groovy'
        hits[0].uuid == '11111111-1111-1111-1111-111111111111'
        hits[0].subtype == 'class'
        hits[1].uuid == '22222222-2222-2222-2222-222222222222'
    }

    def "search returns an honest empty list when projections are warming"() {
        given:
        def source = openSource()
        new File(sourceRoot, 'by-query/warming').text = NeurosClient.QUERY_EMPTY

        expect:
        NeurosClient.search(source, 'warming', 5) == []
    }

    def "search neutralises slashes in the question"() {
        given:
        def source = openSource()
        new File(sourceRoot, 'by-query/a b').text =
                'X\t44444444-4444-4444-4444-444444444444\tfile\n'

        when:
        def hits = NeurosClient.search(source, 'a/b', 5)

        then:
        hits.size() == 1
        hits[0].displayName == 'X'
    }

    def "search retries once when a dynamic surface is transiently unavailable"() {
        given:
        def source = openSource()
        Thread.start {
            sleep 100
            new File(sourceRoot, 'by-query/late answer').text =
                    'Late\t99999999-9999-9999-9999-999999999999\tfile\n'
        }

        when:
        def hits = NeurosClient.search(source, 'late answer', 5)

        then:
        hits.size() == 1
        hits[0].displayName == 'Late'
    }

    def "search gives an honest not-ready message instead of referring to removed settings"() {
        given:
        def source = openSource()

        when:
        NeurosClient.search(source, 'no answer has been prepared', 5)

        then:
        def problem = thrown(IOException)
        problem.message.contains('search index')
        problem.message.contains('Try again shortly')
        !problem.message.contains('settings')
    }

    def "search reports a busy source in human language, not a raw transport error"() {
        given:
        def source = openSource()
        new File(sourceRoot, 'by-query/broken').mkdirs()

        when:
        NeurosClient.search(source, 'broken', 5)

        then:
        def problem = thrown(IOException)
        problem.message.contains('busy')
        problem.message.contains('search index')
    }

    def "search refuses an unopened source and an empty question"() {
        given:
        def source = openSource()

        when:
        NeurosClient.search(null, 'anything', 5)

        then:
        thrown(IllegalArgumentException)

        when:
        NeurosClient.search(source, '   ', 5)

        then:
        thrown(IllegalArgumentException)
    }

    def "writeCard writes into the requested destination subdirectory"() {
        when:
        File written = NeurosClient.writeCard(destination.path, 'neuros-cards',
                '55555555-5555-5555-5555-555555555555-idea.card.md', 'list: Backlog\n')

        then:
        written.exists()
        written.parentFile == new File(destination, 'neuros-cards')
        written.text == 'list: Backlog\n'
    }

    def "writeCard with empty relative dir writes at the destination root"() {
        when:
        File written = NeurosClient.writeCard(destination.path, '', 'root.card.md', 'body')

        then:
        written.parentFile == destination
        written.text == 'body'
    }

    def "confirmWrite uses the saved file when no availability surface exists"() {
        given:
        def source = openSource()
        File written = new File(destination, 'saved.card.md')
        written.text = 'card body'

        when:
        def result = NeurosClient.confirmWrite(source, written, 1)

        then:
        result.saved
        !result.available
        result.status == 'SAVED'
        result.method == 'file'
    }

    def "confirmWrite polls the availability surface when the source provides one"() {
        given:
        def source = openSource()
        new File(sourceRoot, 'ingest-status').mkdirs()
        File written = new File(destination, 'probe.card.md')
        written.text = 'card body'
        Thread.start {
            sleep 200
            new File(sourceRoot, 'ingest-status/probe.card.md').text =
                    'status: VISIBLE\nuuid: 66666666-6666-6666-6666-666666666666\n'
        }

        when:
        def result = NeurosClient.confirmWrite(source, written, 10)

        then:
        result.saved
        result.available
        result.status == 'VISIBLE'
        result.method == 'availability-surface'
        result.identifier == '66666666-6666-6666-6666-666666666666'
    }

    def "waitForIngest ignores visibility_status while the primary status is PENDING"() {
        // Regression: a source can report visibility_status: VISIBLE and
        // projection_status: VISIBLE as SEPARATE lines while the primary
        // 'status:' line is still PENDING_PROJECTION — only the primary line
        // is the availability verdict.
        given:
        def source = openSource()
        new File(sourceRoot, 'ingest-status').mkdirs()
        new File(sourceRoot, 'ingest-status/intermediate.card.md').text =
                'status: PENDING_PROJECTION\n' +
                'uuid: 77777777-7777-7777-7777-777777777777\n' +
                'visibility_status: VISIBLE\n' +
                'projection_status: VISIBLE\n'

        when:
        def result = NeurosClient.waitForIngest(source, 'intermediate.card.md', 1)

        then:
        !result.visible
        result.status == 'PENDING_PROJECTION'
    }

    def "waitForIngest sees the primary status line when everything is VISIBLE"() {
        given:
        def source = openSource()
        new File(sourceRoot, 'ingest-status').mkdirs()
        new File(sourceRoot, 'ingest-status/done.card.md').text =
                'status: VISIBLE\n' +
                'uuid: 88888888-8888-8888-8888-888888888888\n' +
                'visibility_status: VISIBLE\n' +
                'projection_status: VISIBLE\n'

        when:
        def result = NeurosClient.waitForIngest(source, 'done.card.md', 2)

        then:
        result.visible
        result.uuid == '88888888-8888-8888-8888-888888888888'
    }

    def "waitForIngest times out honestly when the file never lands"() {
        given:
        def source = openSource()
        new File(sourceRoot, 'ingest-status').mkdirs()

        when:
        def result = NeurosClient.waitForIngest(source, 'never.card.md', 1)

        then:
        !result.visible
        result.status == 'PENDING'
    }
}
