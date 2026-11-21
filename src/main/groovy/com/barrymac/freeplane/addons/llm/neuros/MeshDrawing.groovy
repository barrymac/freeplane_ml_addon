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
    private int ambiguous
    private final Set<String> missing = new LinkedHashSet<>()
    private final List<Object[]> missingEdges = []
    private final Set<String> resolvedNames = [] as Set

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
        drawing.renderGaps()
        return drawing
    }

    void add(MeshEdge edge) {
        MeshNode sourceEntry = edge.source ? (index.find(edge.source) ?: index.forFile(seedNode.link?.file)) : null
        MeshNode targetEntry = index.find(edge.target)
        // A marker edge without its own source states the seed's relationship
        // (a diagram's [[references:...]] lines): the seed node is the source.
        def source = sourceEntry != null ? nodeFor(sourceEntry) :
                (edge.source == null ? seedNode : null)
        // The scope index only holds documents (.md/.puml). A code target —
        // carried by an atlas node as its declared source file — is found in
        // the map by identity instead of invented or dropped.
        def target = nodeFor(targetEntry)
        if (target == null && edge.target) {
            target = findExisting(seedNode.map.root, null, edge.target)
        }
        if (target == null && edge.target) {
            // The substrate's by-label surface is the last authority: a single
            // entry resolves the target even outside the indexed surfaces;
            // several entries mean the label is shared — an ambiguity, never
            // an absence and never a silent pick.
            List<String> labelled = index.labelLookup(edge.target)
            if (labelled.size() == 1) {
                File labelFile = new File(index.scopeRoot, "by-label/${edge.target}")
                target = findExisting(seedNode.map.root, null, edge.target) ?:
                        containerNode().createChild(edge.target)
                target.link.file = labelFile
                target.tags.add(ENTRY_TAG)
                if (!target.note) target.note = "Resolved by label: ${labelled[0]}"
                resolvedNames << edge.target
                removeStaleGap(edge.target)
            } else if (labelled.size() > 1) {
                renderAmbiguous(source ?: seedNode, edge, labelled)
                ambiguous++
                return
            }
        }
        if (target == null) {
            unresolved++
            missing << edge.target
            missingEdges <<([edge, source] as Object[])
            return
        }
        if (source == null || source.is(target)) {
            skipped++
            return
        }
        resolvedNames << nameOf(target)
        removeStaleGap(edge.target)
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

    /**
     * Unresolved declared targets render as gap markers: hollow nodes saying
     * what is missing and who declares it. A gap is a worklist entry — the
     * map shows the linking that has not happened yet — and it disappears on
     * the next run once the target resolves.
     */
    private void renderGaps() {
        if (missingEdges.isEmpty()) {
            removeOrphanGaps()
            return
        }
        List<String> remaining = []
        missingEdges.each { pair ->
            MeshEdge edge = pair[0]
            def from = pair[1] ?: seedNode
            if (resolvedNames.contains(edge.target)) return
            if (remaining.size() >= MAX_NEW_ENTRIES) {
                remaining << edge.target
                return
            }
            def gap = findGapNode(edge.target)
            if (gap == null) {
                gap = containerNode().createChild("${edge.target} [missing \u2014 not yet declared anywhere]")
                gap.tags.add('neurOS mesh gap')
                gap.note = "Declared as [[${edge.marker}:${edge.target}]] but no entry carries this " +
                        'identity yet. Close the gap by linking the declaring artifact to the real ' +
                        'target, or ingesting the target so it becomes visible.'
                remaining << edge.target
            }
            connectGap(from, gap, edge)
        }
    }

    /** Gap nodes whose targets resolved this run are removed, not just orphaned. */
    private void removeStaleGap(String target) {
        def gap = findGapNode(target)
        if (gap != null) {
            try {
                gap.connectorsIn?.each { it.remove() }
            } catch (Exception ignored) { }
            try {
                gap.connectorsOut?.each { it.remove() }
            } catch (Exception ignored) { }
            gap.delete()
        }
    }

    /** With nothing missing, drop gap nodes left over from an earlier run. */
    private void removeOrphanGaps() {
        def container = findTagged(seedNode.map.root, CONTAINER_TAG)
        if (container == null) return
        new ArrayList(container.children).each { child ->
            if (child.tags?.contains('neurOS mesh gap')) child.delete()
        }
    }

    private def findGapNode(String target) {
        def container = findTagged(seedNode.map.root, CONTAINER_TAG)
        if (container == null) return null
        return container.children.find { child ->
            child.tags?.contains('neurOS mesh gap') &&
                    child.text?.toString()?.startsWith(target + ' ')
        }
    }

    /**
     * Several entries share one label: the map says so instead of pretending
     * absence or guessing a winner. Candidates hang off the ambiguity marker,
     * each linked where the substrate points, so a person chooses the target.
     */
    private void renderAmbiguous(def from, MeshEdge edge, List<String> labelled) {
        def marker = findOrBuildAmbiguous(edge.target, labelled)
        removeStaleGap(edge.target)
        boolean exists = from.connectorsOut.any { connector ->
            connector.target?.id == marker.id && connector.middleLabel == edge.label
        }
        if (!exists) {
            def connector = from.addConnectorTo(marker)
            connector.middleLabel = edge.label
            connector.color = new java.awt.Color(0x88, 0x88, 0x88)
            connector.width = 1
            connector.endArrow = true
            connector.dashArray = [4, 6] as int[]
        }
    }

    private def findOrBuildAmbiguous(String target, List<String> labelled) {
        int count = labelled.size()
        def existing = containerNode().children.find { child ->
            child.tags?.contains('neurOS mesh ambiguous') &&
                    child.text?.toString()?.startsWith(target + ' ')
        }
        if (existing != null) return existing
        def marker = containerNode().createChild(
                "${target} [ambiguous \u2014 ${count} entries share this label]")
        marker.tags.add('neurOS mesh ambiguous')
        marker.tags.add('neurOS mesh ambiguous')
        marker.note = "This name is shared by ${count} entries. It is not missing — " +
                'it is ambiguous. Pick the intended target from the candidates below; ' +
                'nothing is wired until you choose.'
        labelled.each { uuid ->
            def candidate = marker.createChild(uuid)
            candidate.note = "Candidate entry ${uuid}"
            try {
                candidate.link.file = new File(index.scopeRoot, "by-id/${uuid.substring(0, 2)}/${uuid}")
            } catch (Exception ignored) {
                // A candidate link that cannot be resolved leaves the note truthful.
            }
        }
        return marker
    }

    private void connectGap(def from, def gap, MeshEdge edge) {
        boolean exists = from.connectorsOut.any { connector ->
            connector.target?.id == gap.id && connector.middleLabel == edge.label
        }
        if (exists) return
        def connector = from.addConnectorTo(gap)
        connector.middleLabel = edge.label
        // A gap reads as pencilled-in: grey and dashed, like provisional
        // proposals, so nobody mistakes it for accepted structure.
        connector.color = new java.awt.Color(0x88, 0x88, 0x88)
        connector.width = 1
        connector.endArrow = true
        connector.dashArray = [4, 6] as int[]
    }

    private static String nameOf(def node) {
        def file = node?.link?.file
        return file?.name ?: node?.text?.toString()
    }

    /** Shared tag search: the code atlas reuses the same idempotency rule. */
    static def findTagged(def root, String tag) {
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
            // A generated node may declare its source file as an attribute —
            // code-atlas file nodes do, since their link points at the
            // rendition, not the source. The declared source is identity.
            // Attribute proxies vary in shape and some nodes carry none at
            // all; a miss is an unresolved edge, never a drawing failure.
            if (name) {
                String declared = sourceFileOf(current)
                if (name == declared) return current
            }
            queue.addAll(current.children)
        }
        return null
    }

    String summary() {
        List<String> parts = ["${drawn} relationship${drawn == 1 ? '' : 's'} drawn"]
        if (alreadyThere) parts << "${alreadyThere} already in the map"
        if (created) parts << "${created} related entr${created == 1 ? 'y' : 'ies'} added"
        if (ambiguous) parts << "${ambiguous} name${ambiguous == 1 ? '' : 's'} shared by several entries"
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

    /** The node's declared source file, whatever shape the proxy exposes. */
    private static String sourceFileOf(def node) {
        try {
            Object value = node.attributes?.get('source-file')
            if (value != null) return String.valueOf(value)
        } catch (Exception ignored) { }
        try {
            Object alt = node['source-file']
            if (alt != null) return String.valueOf(alt)
        } catch (Exception ignored) { }
        return null
    }

    private static String pathOf(File file) {
        return file == null ? null : file.absolutePath
    }
}