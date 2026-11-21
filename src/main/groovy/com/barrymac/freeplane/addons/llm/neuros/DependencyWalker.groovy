package com.barrymac.freeplane.addons.llm.neuros

/**
 * Walks declared dependency edges (the DEPENDENCY family: depends-on,
 * blocked-by, blocks) transitively from one seed entry.
 *
 * The walk is honest and bounded: it follows only what cards declare, never
 * invents an edge, stops at the depth cap, and reports truncation rather than
 * pretending completeness. A target that comes back onto the current path is
 * a dependency CYCLE — marked on the edge, so the map can flag the cycle as a
 * defect instead of hiding it.
 */
class DependencyWalker {

    static final int MAX_DEPTH = 3
    static final int MAX_EDGES = 60
    static final int MAX_FANOUT = 12

    static class Result {
        List<MeshEdge> edges = []
        Set<String> cycleTargets = [] as Set
        boolean truncated = false
    }

    /**
     * Walk from the seed entry's identity.
     *
     * @param seedId     the seed entry's marker identity (its card uuid when
     *                   it has one, else its file name)
     * @param index      the scope index, for resolving each target
     * @param reader     closure: MeshNode -> List<MeshEdge>, returning the
     *                   entry's declared dependency edges (caller reads the
     *                   card body once per entry and filters by family)
     */
    static Result walk(String seedId, MeshIndex index, Closure<List<MeshEdge>> reader) {
        Result result = new Result()
        if (!seedId?.trim()) return result
        Deque<List> frontier = new ArrayDeque<>()
        frontier.push([seedId, 0, [seedId] as Set] as List)
        Set<String> seenPairs = [] as Set
        while (!frontier.isEmpty()) {
            def (currentId, depth, path) = frontier.pop()
            if (depth >= MAX_DEPTH) {
                result.truncated = true
                continue
            }
            MeshNode current = index.find(currentId)
            if (current == null) continue
            List<MeshEdge> declared = reader.call(current)
            if (declared.size() > MAX_FANOUT) {
                result.truncated = true
                declared = declared.take(MAX_FANOUT)
            }
            declared.each { edge ->
                if (result.edges.size() >= MAX_EDGES) {
                    result.truncated = true
                    return
                }
                String target = edge.target
                if (!target) return
                String pair = "${currentId}|${target}|${edge.marker}"
                if (!seenPairs.add(pair)) return
                boolean cycle = path.contains(target)
                if (cycle) result.cycleTargets << target
                result.edges << new MeshEdge(
                        source: currentId, target: target, marker: edge.marker,
                        label: edge.label, family: edge.family,
                        declared: edge.declared, cycle: cycle)
                if (!cycle) {
                    frontier.push([target, depth + 1, path + target] as List)
                }
            }
        }
        return result
    }
}
