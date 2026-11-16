package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

@Subject(KnowledgeListing)
class KnowledgeListingSpec extends Specification {

    @TempDir
    File root

    private KnowledgeSource openSource() {
        new File(root, 'by-query').mkdirs()
        return KnowledgeSource.open(root.path, 'map-scope')
    }

    def "listing is bounded, pageable and never exposes an identifier in the title"() {
        given:
        def source = openSource()
        def typeDir = new File(root, 'by-tag/invented-type')
        typeDir.mkdirs()
        (1..30).each { index ->
            String id = String.format('%08d-aaaa-aaaa-aaaa-aaaaaaaaaaaa', index)
            new File(typeDir, "${id}-entry-${index}.card.md").text = 'body'
        }
        new File(typeDir, 'plain-name.obs').text = 'plain body'

        when:
        def first = KnowledgeListing.list(source, 'by-tag/invented-type', 0, 10, 'invented-type')
        def second = KnowledgeListing.list(source, 'by-tag/invented-type', 10, 10, 'invented-type')

        then:
        first.entries.size() == 10
        first.hasMore
        first.nextOffset == 10
        first.entries.every { !it.displayName.contains('aaaaaaaaaaaa') }
        first.entries.every { it.subtype == 'invented-type' }
        second.entries.size() == 10
        second.hasMore
        second.nextOffset == 20
    }

    def "entry links use source identity when available and the listing file otherwise"() {
        given:
        def source = openSource()
        def typeDir = new File(root, 'by-tag/invented-type')
        typeDir.mkdirs()
        String id = '11111111-1111-1111-1111-111111111111'
        def withId = new File(typeDir, "${id}-named.card.md")
        withId.text = 'body'
        def plain = new File(typeDir, 'plain.obs')
        plain.text = 'plain'
        String uuidOnlyId = '22222222-2222-2222-2222-222222222222'
        new File(typeDir, "${uuidOnlyId}.card.md").text = 'uuid only'

        when:
        def page = KnowledgeListing.list(source, 'by-tag/invented-type', 0, 25, 'invented-type')

        then:
        def identified = page.entries.find { it.uuid == id }
        identified.sourceFile(source).path == source.byIdPath(id).path
        page.entries.find { !it.uuid }.sourceFile(source).path == plain.path
        page.entries.find { it.uuid == uuidOnlyId }.displayName == 'invented-type'
    }

    def "server truncation is reported instead of pretending a complete listing"() {
        given:
        def source = openSource()
        def typeDir = new File(root, 'by-tag/invented-type')
        typeDir.mkdirs()
        new File(typeDir, 'one.obs').text = 'one'
        new File(typeDir, '.listing-status').text =
                'returned: 1\ntotal_available: 100\ntruncated: true\n'

        when:
        def page = KnowledgeListing.list(source, 'by-tag/invented-type', 0, 25, 'invented-type')

        then:
        page.sourceTruncated
        page.totalAvailable == 100
        !page.hasMore
    }

    def "an empty surface returns an honest empty page"() {
        given:
        def source = openSource()
        new File(root, 'by-tag/empty').mkdirs()

        when:
        def page = KnowledgeListing.list(source, 'by-tag/empty', 0, 25, 'empty')

        then:
        page.entries.isEmpty()
        !page.hasMore
        !page.sourceTruncated
    }

    def "page size is capped to protect the source"() {
        given:
        def source = openSource()
        def typeDir = new File(root, 'by-tag/invented-type')
        typeDir.mkdirs()
        (1..110).each { index ->
            new File(typeDir, "entry-${index}.obs").text = 'body'
        }

        when:
        def page = KnowledgeListing.list(source, 'by-tag/invented-type', 0, 1000, 'invented-type')

        then:
        page.entries.size() == KnowledgeListing.MAX_PAGE_SIZE
        page.hasMore
    }
}
