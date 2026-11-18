package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject

@Subject(MeshReveal)
class MeshRevealSpec extends Specification {

    private static class FakeNode {
        boolean folded = true
    }

    private static class FakeDrawing {
        Object container
        Object revealTarget
    }

    private static class FakeController {
        Object selected
        Object centred
        void select(Object node) { selected = node }
        void centerOnNode(Object node) { centred = node }
    }

    def "the container is expanded and the first new entry is shown"() {
        given:
        def container = new FakeNode()
        def created = new FakeNode()
        def drawing = new FakeDrawing(container: container, revealTarget: created)
        def controller = new FakeController()

        when:
        MeshReveal.reveal(controller, drawing)

        then:
        !container.folded
        controller.selected.is(created)
        controller.centred.is(created)
    }

    def "an existing node is revealed when nothing new was created"() {
        given:
        def existing = new FakeNode()
        def drawing = new FakeDrawing(container: null, revealTarget: existing)
        def controller = new FakeController()

        when:
        MeshReveal.reveal(controller, drawing)

        then:
        controller.centred.is(existing)
    }

    def "nothing to reveal is not an error"() {
        given:
        def controller = new FakeController()

        when:
        MeshReveal.reveal(controller, new FakeDrawing(container: null, revealTarget: null))
        MeshReveal.reveal(controller, null)

        then:
        noExceptionThrown()
        controller.selected == null
    }
}