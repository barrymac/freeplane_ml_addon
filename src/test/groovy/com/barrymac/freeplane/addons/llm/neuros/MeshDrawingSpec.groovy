package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

import java.awt.Color

/**
 * The drawing decisions, exercised without a running Freeplane.
 *
 * MeshDrawing talks to nodes through properties Freeplane's node proxy exposes
 * (children, link.file, tags, connectorsOut, addConnectorTo), so a small fake
 * tree stands in for the real map and every rule — reuse, bounded growth,
 * no duplicate connectors, honest unresolved count — is checked here.
 */
@Subject(MeshDrawing)
class MeshDrawingSpec extends Specification {

    @TempDir
    File scope

    private static final String EXPAND = '0f65c7c1-64de-4d96-a0ef-87b89f3a6cfe'
    private static final String AUTO = '2ca53842-dad9-4559-8b25-1c179dc076f9'
    private static final String DOOR = '5036a147-543e-41f2-8d52-daf6f1e19030'
    private static final String MESH = 'c4262631-47c7-44a9-be90-5df6ef33f132'

    private File expansion
    private File automatic
    private File door
    private File mesh
    private MeshIndex index

    def setup() {
        // The index caches listings for the session; a test that writes files must
        // not inherit an earlier test's snapshot.
        MeshIndex.invalidate()
        expansion = card(EXPAND, 'bounded-relationship-expansion')
        automatic = card(AUTO, 'automatic-visible-hit-connectors')
        door = card(DOOR, 'lazy-door-navigation')
        mesh = card(MESH, 'bounded-mesh-density-inspector')
        index = MeshIndex.load(scope)
    }

    def cleanup() {
        MeshIndex.invalidate()
    }

    private File card(String uuid, String slug) {
        File file = new File(scope, "CARD-${uuid}-${slug}.card.md")
        file.text = "[[card:${uuid}]]\n# ${slug}\n"
        return file
    }

    def "a relationship between entries already in the map reuses their nodes"() {
        given:
        def map = newTree()
        def expansionNode = child(map.root, expansion)
        def automaticNode = child(map.root, automatic)

        when:
        def drawing = MeshDrawing.draw(expansionNode, index, [
                new MeshEdge(source: EXPAND, target: AUTO, marker: 'depends-on',
                        label: 'depends on', family: MeshVocabulary.DEPENDENCY, declared: true)])

        then:
        drawing.drawn == 1
        drawing.created == 0
        expansionNode.connectorsOut.size() == 1
        def connector = expansionNode.connectorsOut[0]
        connector.target.is(automaticNode)
        connector.middleLabel == 'depends on'
        connector.color == MeshVocabulary.color(MeshVocabulary.DEPENDENCY)
        connector.width == 2
        connector.endArrow
    }

    def "an entry missing from the map is added under a mesh container"() {
        given:
        def map = newTree()
        def expansionNode = child(map.root, expansion)

        when:
        def drawing = MeshDrawing.draw(expansionNode, index, [
                new MeshEdge(source: EXPAND, target: DOOR, marker: 'blocks',
                        label: 'blocks', family: MeshVocabulary.BLOCKING, declared: true)])

        then:
        drawing.created == 1
        def container = map.root.children.find { it.tags.contains(MeshDrawing.CONTAINER_TAG) }
        container != null
        container.children.size() == 1
        def added = container.children[0]
        added.text == 'Lazy door navigation'
        added.link.file == door
        added.tags.contains(MeshDrawing.ENTRY_TAG)
    }

    def "the same relationship is not drawn twice"() {
        given:
        def map = newTree()
        def expansionNode = child(map.root, expansion)
        child(map.root, automatic)

        when:
        def first = MeshDrawing.draw(expansionNode, index, [dependsOnAutomatic()])
        def second = MeshDrawing.draw(expansionNode, index, [dependsOnAutomatic()])

        then:
        first.drawn == 1
        second.drawn == 0
        second.summary().contains('already in the map')
        expansionNode.connectorsOut.size() == 1
    }

    def "the seed entry is never added twice when it is reached by another path"() {
        given:
        def map = newTree()
        File copy = new File(scope, "copies/CARD-${EXPAND}-bounded-relationship-expansion.card.md")
        copy.parentFile.mkdirs()
        copy.text = '[[card:0f65c7c1-64de-4d96-a0ef-87b89f3a6cfe]]\n# copy\n'
        def seedNode = new FakeNode(text: 'seed', map: map, link: new FakeLink(file: copy))
        map.root.children << seedNode

        when:
        def drawing = MeshDrawing.draw(seedNode, index, [
                new MeshEdge(source: EXPAND, target: EXPAND, marker: 'depends-on',
                        label: 'depends on', family: MeshVocabulary.DEPENDENCY, declared: true)])

        then:
        drawing.created == 0
        map.root.children.size() == 1
    }

    def "a contradiction is drawn dashed in its own colour"() {
        given:
        def map = newTree()
        def expansionNode = child(map.root, expansion)
        def automaticNode = child(map.root, automatic)

        when:
        def drawing = MeshDrawing.draw(expansionNode, index, [
                new MeshEdge(source: EXPAND, target: AUTO, marker: 'contradicts',
                        label: 'contradicts', family: MeshVocabulary.CONTRADICTION,
                        declared: true)])

        then:
        drawing.drawn == 1
        def connector = expansionNode.connectorsOut[0]
        connector.color == MeshVocabulary.color(MeshVocabulary.CONTRADICTION)
        connector.dashArray == ([6, 4] as int[])
        connector.middleLabel == 'contradicts'
    }

    def "a provisional proposal is drawn dashed and grey, not in family colour"() {
        given:
        def map = newTree()
        def expansionNode = child(map.root, expansion)
        child(map.root, automatic)

        when:
        def drawing = MeshDrawing.draw(expansionNode, index, [
                new MeshEdge(source: EXPAND, target: AUTO, marker: 'depends-on',
                        label: 'proposed: depends on', family: MeshVocabulary.DEPENDENCY,
                        declared: true, proposed: true)])

        then:
        drawing.drawn == 1
        def connector = expansionNode.connectorsOut[0]
        connector.color == new java.awt.Color(0x88, 0x88, 0x88)
        connector.dashArray == ([4, 6] as int[])
        connector.width == 1
        connector.middleLabel.startsWith('proposed:')
    }

    def "a link outside the scope is counted, never invented"() {
        given:
        def map = newTree()
        def expansionNode = child(map.root, expansion)

        when:
        def drawing = MeshDrawing.draw(expansionNode, index, [
                new MeshEdge(source: EXPAND, target: 'missing-entry', marker: 'references',
                        label: 'references', family: MeshVocabulary.REFERENCE, declared: true)])

        then:
        drawing.unresolved == 1
        drawing.drawn == 0
        drawing.created == 0
        drawing.summary().contains('outside this scope')
        drawing.missing == ['missing-entry'] as Set
    }

    def "growth is bounded so one entry cannot flood the map"() {
        given:
        def map = newTree()
        def expansionNode = child(map.root, expansion)
        (1..20).each { card(String.format('%08d-0000-0000-0000-000000000000', it), "entry-${it}") }
        MeshIndex.invalidate()
        def freshIndex = MeshIndex.load(scope)
        List<MeshEdge> edges = (1..20).collect { number ->
            new MeshEdge(source: EXPAND,
                    target: String.format('%08d-0000-0000-0000-000000000000', number),
                    marker: 'references', label: 'references',
                    family: MeshVocabulary.REFERENCE, declared: true)
        }

        when:
        def drawing = MeshDrawing.draw(expansionNode, freshIndex, edges)

        then:
        drawing.created == MeshDrawing.MAX_NEW_ENTRIES
        drawing.drawn == MeshDrawing.MAX_NEW_ENTRIES
    }

    def "the summary counts what happened in words a reader can follow"() {
        given:
        def map = newTree()
        def expansionNode = child(map.root, expansion)

        when:
        def drawing = MeshDrawing.draw(expansionNode, index, [
                dependsOnAutomatic(),
                new MeshEdge(source: EXPAND, target: DOOR, marker: 'blocks',
                        label: 'blocks', family: MeshVocabulary.BLOCKING, declared: true)])

        then:
        drawing.summary() == '2 relationships drawn, 2 related entries added.'
    }

    private MeshEdge dependsOnAutomatic() {
        return new MeshEdge(source: EXPAND, target: AUTO, marker: 'depends-on',
                label: 'depends on', family: MeshVocabulary.DEPENDENCY, declared: true)
    }

    private static FakeNode child(FakeNode parent, File file) {
        FakeNode node = new FakeNode(text: file.name, map: parent.map)
        node.link = new FakeLink(file: file)
        parent.children << node
        return node
    }

    private static FakeMap newTree() {
        FakeNode root = new FakeNode(text: 'map')
        FakeMap map = new FakeMap(root: root)
        root.map = map
        return map
    }

    /** Freeplane's node proxy exposes map.root; the fake mirrors that shape. */
    static class FakeMap {
        FakeNode root
    }

    /** The slice of Freeplane's node proxy that MeshDrawing relies on. */
    static class FakeNode {
        String text
        String note
        FakeLink link
        String id = UUID.randomUUID().toString()
        List<FakeNode> children = []
        List<FakeConnector> connectorsOut = []
        Set<String> tags = [] as Set
        FakeMap map

        FakeNode createChild(String title) {
            // Freeplane returns a link proxy for every node, so assigning
            // node.link.file on a fresh node must work here too.
            FakeNode node = new FakeNode(text: title, map: map, link: new FakeLink())
            children << node
            return node
        }

        FakeConnector addConnectorTo(FakeNode target) {
            FakeConnector connector = new FakeConnector(target: target)
            connectorsOut << connector
            return connector
        }
    }

    static class FakeLink {
        File file
    }

    static class FakeConnector {
        FakeNode target
        FakeNode source
        String middleLabel
        Color color
        int width
        int[] dashArray
        boolean endArrow
    }
}