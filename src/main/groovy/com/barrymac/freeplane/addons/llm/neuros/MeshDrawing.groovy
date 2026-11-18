package com.barrymac.freeplane.addons.llm.neuros

/**
 * Draws a mesh into a map: reuses any node already pointing at an entry, adds
 * bounded new nodes for entries the map does not show yet, and connects
 * source to target with the relationship written in words.
 *
 * Kept separate from the menu action so the drawing can be exercised from a
 * script without a dialog, and so its decisions are testable without a UI.
 */
class MeshDrawing {

    static final int MAX_NEW_ENTRIES = 12
    static final int MAX_WALK = 3_000
    static final String CONTAINER_TITLE = 'Related knowledge'
    static final String CONTAINER_TAG = 'neurOS mesh'
    static final String ENTRY_TAG = 'neurOS mesh entry'

    private final def seedNode
    private final MeshIndex index
    private final Map<String, Object> byPath = [:]
    private final Map<String, Object> byName = [:]
    private def container
    private int created
    final List<Object> createdNodes = []
    Object firstTarget
    private int drawn
    private int alreadyThere
    private int unresolved
    private int skipped
    private final Set<String> missing = new LinkedHashSet<>()

    MeshDrawing(def seedNode, MeshIndex index) {
        this.seedNode = seedNode
        this.index = index
        def seedFile = seedNode.link?.file
        if (seedFile != null) {
            byPath[pathOf(seedFile)] = seedNode
            byName[seedFile.name] = seedNode
        }
    }

    static MeshDrawing draw(def seedNode, MeshIndex index, List<MeshEdge> edges) {
        MeshDrawing drawing = new MeshDrawing(seedNode, index)
        edges.each { edge -> drawing.add(edge) }
        return drawing
    }

    void add(MeshEdge edge) {
        MeshNode sourceEntry = edge.source ? (index.find(edge.source) ?: index.forFile(seedNode.link?.file)) : null
        MeshNode targetEntry = index.find(edge.target)
        if (targetEntry == null) {
            unresolved++
            missing << edge.target
            return
        }
        def source = nodeFor(sourceEntry)
        def target = nodeFor(targetEntry)
        if (source == null || target == null || source.is(target)) {
            skipped++
            return
        }
        if (firstTarget == null) firstTarget = target
        if (connect(source, target, edge)) {
            drawn++
        } else {
            alreadyThere++
        }
    }

    private boolean connect(def source, def target, MeshEdge edge) {
        boolean exists = source.connectorsOut.any { connector ->
            connector.target?.id == target.id && connector.middleLabel == edge.label
        }
        if (exists) return false
        def connector = source.addConnectorTo(target)
        connector.middleLabel = edge.label
        // Provisional proposals read as pencilled-in: grey and dashed, whatever
        // family they belong to, so nobody mistakes them for accepted structure.
        connector.color = edge.proposed ?
                new java.awt.Color(0x88, 0x88, 0x88) : MeshVocabulary.color(edge.family)
        connector.width = edge.proposed ? 1 : 2
        connector.endArrow = true
        int[] dash = edge.proposed ? ([4, 6] as int[]) : MeshVocabulary.dashFor(edge.family)
        if (dash != null) connector.dashArray = dash
        return true
    }

    private def nodeFor(MeshNode entry) {
        if (entry == null) return null
        String path = pathOf(entry.file)
        String name = entry.file?.name
        // Identity first: the same entry reached by a different path spelling
        // must reuse the node the map already has, never become a second copy.
        if (name && byName.containsKey(name)) return byName[name]
        if (path && byPath.containsKey(path)) return byPath[path]
        def existing = findExisting(seedNode.map.root, path, name)
        if (existing != null) {
            if (path) byPath[path] = existing
            if (name) byName[name] = existing
            return existing
        }
        if (created >= MAX_NEW_ENTRIES) return null
        def createdNode = containerNode().createChild(entry.title)
        createdNode.link.file = entry.file
        createdNode.tags.add(ENTRY_TAG)
        created++
        createdNodes << createdNode
        if (path) byPath[path] = createdNode
        if (name) byName[name] = createdNode
        return createdNode
    }

    private def containerNode() {
        if (container != null) return container
        container = findTagged(seedNode.map.root, CONTAINER_TAG)
        if (container == null) {
            container = seedNode.map.root.createChild(CONTAINER_TITLE)
            container.tags.add(CONTAINER_TAG)
            container.note = 'Entries related to this map, linked to their knowledge source.'
        }
        return container
    }

    private static def findTagged(def root, String tag) {
        Deque<Object> queue = new ArrayDeque<>()
        queue << root
        int visited = 0
        while (!queue.isEmpty() && visited < MAX_WALK) {
            def current = queue.poll()
            visited++
            if (current.tags?.contains(tag)) return current
            queue.addAll(current.children)
        }
        return null
    }

    private static def findExisting(def root, String path, String name) {
        if (!path && !name) return null
        Deque<Object> queue = new ArrayDeque<>()
        queue << root
        int visited = 0
        while (!queue.isEmpty() && visited < MAX_WALK) {
            def current = queue.poll()
            visited++
            def file = current.link?.file
            if (path && pathOf(file) == path) return current
            if (name && file?.name == name) return current
            queue.addAll(current.children)
        }
        return null
    }

    String summary() {
        List<String> parts = ["${drawn} relationship${drawn == 1 ? '' : 's'} drawn"]
        if (alreadyThere) parts << "${alreadyThere} already in the map"
        if (created) parts << "${created} related entr${created == 1 ? 'y' : 'ies'} added"
        if (unresolved) parts << "${unresolved} link${unresolved == 1 ? '' : 's'} " +
                "point${unresolved == 1 ? 's' : ''} outside this scope"
        if (skipped) parts << "${skipped} could not be placed"
        return parts.join(', ') + '.'
    }

    /** Where the reader should be looking after a drawing run. */
    Object getRevealTarget() {
        return createdNodes ? createdNodes[0] : firstTarget
    }

    Object getContainer() { container }

    int getDrawn() { drawn }

    int getCreated() { created }

    int getUnresolved() { unresolved }

    Set<String> getMissing() { missing }

    private static String pathOf(File file) {
        return file == null ? null : file.absolutePath
    }
}