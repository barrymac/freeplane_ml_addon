package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

@Subject(KnowledgeSource)
class KnowledgeSourceSpec extends Specification {

    @TempDir
    File root

    def "opens a scoped knowledge surface and pins the explicitly chosen scope"() {
        given:
        new File(root, 'by-query').mkdirs()

        when:
        def source = KnowledgeSource.open(root.path, 'chosen-scope')

        then:
        source.scope == 'chosen-scope'
        !source.global
        source.path('by-query/test').path == new File(root, 'by-query/test').path
    }

    def "a global surface never chooses a scope for the caller"() {
        given:
        new File(root, 'scopes').mkdirs()
        new File(root, 'scopes/first').mkdirs()
        new File(root, 'scopes/first/by-query').mkdirs()
        new File(root, 'scopes/second').mkdirs()
        new File(root, 'scopes/second/by-query').mkdirs()

        when:
        KnowledgeSource.open(root.path, '')

        then:
        thrown(IllegalArgumentException)
    }

    def "a global surface opens only the explicitly selected scope"() {
        given:
        new File(root, 'scopes').mkdirs()
        ['first', 'second'].each { name ->
            def scoped = new File(root, "scopes/${name}")
            scoped.mkdirs()
            new File(scoped, 'by-query').mkdirs()
        }

        when:
        def source = KnowledgeSource.open(root.path, 'second')

        then:
        source.global
        source.scope == 'second'
        source.availableScopes() == ['first', 'second']
        source.path('by-query/x').parentFile.parentFile.name == 'second'
    }

    def "rejects a missing scope on a global surface without guessing another one"() {
        given:
        new File(root, 'scopes').mkdirs()
        new File(root, 'scopes/available').mkdirs()
        new File(root, 'scopes/available/by-query').mkdirs()

        when:
        KnowledgeSource.open(root.path, 'not-present')

        then:
        thrown(IOException)
    }

    def "rejects a location without a readable search surface"() {
        given:
        root.mkdirs()

        when:
        KnowledgeSource.open(root.path, 'some-scope')

        then:
        thrown(IOException)
    }

    def "rejects invalid scope names instead of turning them into paths"() {
        when:
        KnowledgeSource.open(root.path, '../other')

        then:
        thrown(IllegalArgumentException)
    }

    def "same-scope access needs no confirmation"() {
        given:
        new File(root, 'by-query').mkdirs()
        def source = KnowledgeSource.open(root.path, 'current')

        expect:
        source.pathForScope('current', 'by-query/x').path ==
                new File(root, 'by-query/x').path
    }

    def "cross-scope access is refused without explicit confirmation"() {
        given:
        new File(root, 'scopes').mkdirs()
        ['current', 'other'].each { name ->
            def scoped = new File(root, "scopes/${name}")
            scoped.mkdirs()
            new File(scoped, 'by-query').mkdirs()
        }
        def source = KnowledgeSource.open(root.path, 'current')

        when:
        source.pathForScope('other', 'by-query/x')

        then:
        thrown(SecurityException)
    }

    def "a confirmed cross-scope read only works on a global source"() {
        given:
        new File(root, 'scopes').mkdirs()
        ['current', 'other'].each { name ->
            def scoped = new File(root, "scopes/${name}")
            scoped.mkdirs()
            new File(scoped, 'by-query').mkdirs()
        }
        def source = KnowledgeSource.open(root.path, 'current')

        when:
        def path = source.pathForScope('other', 'by-query/x', true)

        then:
        path.path == new File(root, 'scopes/other/by-query/x').path
    }

    def "a scoped source cannot be widened to another scope even after confirmation"() {
        given:
        new File(root, 'by-query').mkdirs()
        def source = KnowledgeSource.open(root.path, 'current')

        when:
        source.pathForScope('other', 'by-query/x', true)

        then:
        thrown(UnsupportedOperationException)
    }

    def "search paths neutralise slashes and by-id paths reject traversal"() {
        given:
        new File(root, 'by-query').mkdirs()
        def source = KnowledgeSource.open(root.path, 'current')

        expect:
        source.searchPath('a/b c').path == new File(root, 'by-query/a b c').path

        when:
        source.byIdPath('../escape')

        then:
        thrown(IllegalArgumentException)
    }
}
