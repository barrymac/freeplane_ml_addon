package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

@Subject(LinkTargets)
class LinkTargetsSpec extends Specification {

    @TempDir
    File directory

    private static class FakeLink {
        File file
        URI uri
    }

    private static class FakeNode {
        FakeLink link
    }

    def "a link Freeplane recognised as a file is used directly"() {
        given:
        File card = new File(directory, 'card.md')
        card.text = 'x'
        def node = new FakeNode(link: new FakeLink(file: card))

        expect:
        LinkTargets.fileFor(node) == card
    }

    def "a file URI resolves when Freeplane left the file null"() {
        given:
        File card = new File(directory, 'card.md')
        card.text = 'x'
        def node = new FakeNode(link: new FakeLink(file: null, uri: card.toURI()))

        expect:
        LinkTargets.fileFor(node) == card
    }

    def "a missing target, a null link and a null node return null"() {
        expect:
        LinkTargets.fileFor(new FakeNode(link: new FakeLink(file: new File(directory, 'gone.md')))) == null
        LinkTargets.fileFor(new FakeNode(link: null)) == null
        LinkTargets.fileFor(null) == null
    }

    def "a non-file URI is not treated as a local entry"() {
        given:
        def node = new FakeNode(link: new FakeLink(uri: new URI('https://example.test/card.md')))

        expect:
        LinkTargets.fileFor(node) == null
    }

    def "a link written as text still resolves"() {
        given:
        File card = new File(directory, 'card.md')
        card.text = 'x'

        expect:
        LinkTargets.textAsFile(card.absolutePath) == card
        LinkTargets.textAsFile(card.toURI().toString()) == card
        LinkTargets.textAsFile('  ') == null
        LinkTargets.textAsFile('https://example.test/card.md') == null
    }
}