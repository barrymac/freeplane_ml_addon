package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

@Subject(NeurosClient)
class NeurosClientSpec extends Specification {

    @TempDir
    File mount

    @TempDir
    File workspace

    def "search parses TSV rows, dedups by uuid and honours the limit"() {
        given:
        new File(mount, 'search').mkdirs()
        new File(mount, 'search/How does branching work').text =
                'BranchGeneratorFactory.groovy\t11111111-1111-1111-1111-111111111111\tclass\n' +
                'MapUpdater.groovy\t22222222-2222-2222-2222-222222222222\tclass\n' +
                'BranchGeneratorFactory.groovy\t11111111-1111-1111-1111-111111111111\tclass\n' + // dup
                'HELP.md\t33333333-3333-3333-3333-333333333333\tfile\n'

        when:
        def hits = NeurosClient.search(mount.path, 'How does branching work', 2)

        then:
        hits.size() == 2
        hits[0].displayName == 'BranchGeneratorFactory.groovy'
        hits[0].uuid == '11111111-1111-1111-1111-111111111111'
        hits[0].subtype == 'class'
        hits[1].uuid == '22222222-2222-2222-2222-222222222222'
    }

    def "search returns an honest empty list when projections are warming"() {
        given:
        new File(mount, 'search').mkdirs()
        new File(mount, 'search/warming').text = NeurosClient.QUERY_EMPTY

        expect:
        NeurosClient.search(mount.path, 'warming', 5) == []
    }

    def "search neutralises slashes in the question"() {
        given:
        new File(mount, 'search').mkdirs()
        new File(mount, 'search/a b').text = 'X\t44444444-4444-4444-4444-444444444444\tfile\n'

        when:
        def hits = NeurosClient.search(mount.path, 'a/b', 5)

        then:
        hits.size() == 1
        hits[0].displayName == 'X'
    }

    def "search refuses loudly when the mount path is not a live mount"() {
        when:
        NeurosClient.search('/nonexistent/mount', 'anything', 5)

        then:
        thrown(IOException)
    }

    def "search refuses an unconfigured mount path"() {
        when:
        NeurosClient.search('', 'anything', 5)

        then:
        thrown(IllegalArgumentException)
    }

    def "writeCard writes into the requested subdirectory, creating it if needed"() {
        when:
        File written = NeurosClient.writeCard(workspace.path, 'neuros-cards',
                '55555555-5555-5555-5555-555555555555-idea.card.md', 'list: Backlog\n')

        then:
        written.exists()
        written.parentFile == new File(workspace, 'neuros-cards')
        written.text == 'list: Backlog\n'
    }

    def "writeCard with empty relative dir writes at the workspace root"() {
        when:
        File written = NeurosClient.writeCard(workspace.path, '', 'root.card.md', 'body')

        then:
        written.parentFile == workspace
        written.text == 'body'
    }

    def "waitForIngest reports VISIBLE with the ingested uuid"() {
        given:
        def statusDir = new File(mount, 'ingest-status')
        statusDir.mkdirs()
        Thread.start {
            sleep 300
            new File(statusDir, 'probe.card.md').text =
                    'status: VISIBLE\nuuid: 66666666-6666-6666-6666-666666666666\n'
        }

        when:
        def result = NeurosClient.waitForIngest(mount.path, 'probe.card.md', 10)

        then:
        result.visible
        result.uuid == '66666666-6666-6666-6666-666666666666'
        result.status == 'VISIBLE'
    }

    def "waitForIngest ignores visibility_status while the primary status is PENDING"() {
        // Regression: the mount reports visibility_status: VISIBLE and
        // projection_status: VISIBLE as SEPARATE lines while the primary
        // 'status:' line is still PENDING_PROJECTION — only the primary line
        // is the ingest verdict (caught live against the real substrate).
        given: "a status file in the intermediate state"
        def statusDir = new File(mount, 'ingest-status')
        statusDir.mkdirs()
        new File(statusDir, 'intermediate.card.md').text =
                'status: PENDING_PROJECTION\n' +
                'uuid: 77777777-7777-7777-7777-777777777777\n' +
                'visibility_status: VISIBLE\n' +
                'projection_status: VISIBLE\n'
        new File(mount, 'ingest-status/never-lands.card.md').exists() // warm the dir listing

        when:
        def result = NeurosClient.waitForIngest(mount.path, 'intermediate.card.md', 1)

        then:
        !result.visible
        result.status == 'PENDING_PROJECTION'
    }

    def "waitForIngest sees the primary status line when everything is VISIBLE"() {
        given: "a fully-projected status file with all three status lines"
        def statusDir = new File(mount, 'ingest-status')
        statusDir.mkdirs()
        new File(statusDir, 'done.card.md').text =
                'status: VISIBLE\n' +
                'uuid: 88888888-8888-8888-8888-888888888888\n' +
                'visibility_status: VISIBLE\n' +
                'projection_status: VISIBLE\n'

        when:
        def result = NeurosClient.waitForIngest(mount.path, 'done.card.md', 2)

        then:
        result.visible
        result.uuid == '88888888-8888-8888-8888-888888888888'
        result.status == 'VISIBLE'
    }

    def "waitForIngest times out honestly when the file never lands"() {
        given:
        new File(mount, 'ingest-status').mkdirs()

        when:
        def result = NeurosClient.waitForIngest(mount.path, 'never.card.md', 1)

        then:
        !result.visible
        result.status == 'PENDING'
    }
}
