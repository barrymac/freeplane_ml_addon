package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject

@Subject(CardBuilder)
class CardBuilderSpec extends Specification {

    def "build emits the fleet card shape: self-marker, title, list line, provenance"() {
        when:
        String card = CardBuilder.build('Refresh the README', [], 'roadmap.mm', 'ID_123', '2026-11-16')

        then:
        card.startsWith('[[card:')
        card.readLines()[1] == '# Refresh the README'
        card.contains('list: Backlog')
        card.contains('type: idea')
        card.contains('source: freeplane-map:roadmap.mm')
        card.contains('node: ID_123')
        card.contains("Pushed from Freeplane map 'roadmap.mm' node ID_123 on 2026-11-16")
    }

    def "only the first line of a node's text becomes the title; the rest is body"() {
        when:
        String card = CardBuilder.build('Title line\nextra detail', [], 'm.mm', 'ID_9')

        then:
        card.readLines()[1] == '# Title line'
        card.contains('extra detail')
    }

    def "children are rendered as an indented recursive outline"() {
        given:
        def children = [
                [text: 'parent', children: [
                        [text: 'child one', children: []],
                        [text: 'child two', children: [
                                [text: 'grandchild', children: []]]]]]
        ]

        when:
        String card = CardBuilder.build('Root', children, 'm.mm', 'ID_1')

        then:
        card.contains('- parent')
        card.contains('  - child one')
        card.contains('  - child two')
        card.contains('    - grandchild')
    }

    def "fileName embeds the uuid and the slugified title"() {
        when:
        def name = CardBuilder.fileName('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                'Fix: the / Push Feature!!')

        then:
        name == 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa-fix-the-push-feature.card.md'
    }

    def "slugify neutralises hostile titles"() {
        expect:
        CardBuilder.slugify(title) == expected

        where:
        title                                || expected
        'Simple Title'                       || 'simple-title'
        '  spaces   and   tabs  '            || 'spaces-and-tabs'
        '!!!'                                || 'card'
        ''                                   || 'card'
        null                                 || 'card'
        'multi\nline title'                  || 'multi'   // slug comes from the FIRST line only
    }

    def "slugify caps long titles at the boundary"() {
        given:
        def longTitle = ('word ' * 40).trim()

        when:
        def slug = CardBuilder.slugify(longTitle)

        then:
        slug.length() <= CardBuilder.SLUG_MAX_LENGTH
        !slug.endsWith('-')
    }

    def "every build mints a fresh uuid self-marker"() {
        when:
        String a = CardBuilder.build('A', [], 'm', 'i1')
        String b = CardBuilder.build('B', [], 'm', 'i2')

        then:
        def uuidA = (a =~ /\[\[card:([0-9a-fA-F-]+)\]\]/)[0][1]
        def uuidB = (b =~ /\[\[card:([0-9a-fA-F-]+)\]\]/)[0][1]
        uuidA != uuidB
        uuidA ==~ /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/
    }

    def "'list: Backlog' is the initial state — legal on enforce mounts"() {
        expect:
        CardBuilder.build('t', [], 'm', 'i').contains('list: Backlog')
    }
}
