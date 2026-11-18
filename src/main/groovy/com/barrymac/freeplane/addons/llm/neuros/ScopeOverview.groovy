package com.barrymac.freeplane.addons.llm.neuros

import org.freeplane.core.util.LogUtils

/**
 * A bounded overview of a knowledge scope: what it holds, and a sample of how it
 * connects.
 *
 * Big scopes are never read whole. Counts come from the listing surfaces the
 * mount already exposes, and the graph is a sample of a fixed size whose edges
 * are kept only when both ends are in the sample — so the picture is honest
 * about being a sample rather than pretending to be the scope.
 */
class ScopeOverview {

    static final int SAMPLE_SIZE = 12
    static final int MAX_SOURCE_BYTES = 1_000_000

    static class Overview {
        Map<String, Integer> surfaces = [:]
        Map<String, Integer> types = [:]
        List<MeshNode> sample = []
        List<MeshEdge> edges = []
        int totalEntries
        boolean truncated
    }

    /** Counts per surface and type, then a sample of cards and the edges inside it. */
    static Overview plan(File scopeRoot, int sampleSize = SAMPLE_SIZE) {
        Overview overview = new Overview()
        if (scopeRoot == null || !scopeRoot.isDirectory()) return overview

        ['tasks', 'diagrams', 'repos', 'commits', 'sops', 'tenets', 'violations'].each { name ->
            Integer count = countEntries(new File(scopeRoot, name))
            if (count != null) overview.surfaces[name] = count
        }
        DirectoryListing.list(new File(scopeRoot, 'by-tag'), 'by-tag').sort { it.name }.each { type ->
            Integer count = countEntries(type)
            if (count != null && count > 0) overview.types[type.name] = count
        }

        MeshIndex index = MeshIndex.load(scopeRoot)
        overview.totalEntries = index.entries.size()
        overview.truncated = index.entries.size() >= MeshIndex.MAX_ENTRIES

        List<MeshNode> cards = index.entries.findAll { it.file.name.endsWith('.card.md') }
        overview.sample = cards.take(Math.max(sampleSize, 1))
        Set<String> sampleFiles = overview.sample.collect { it.file.name } as Set

        overview.sample.each { MeshNode entry ->
            String text = read(entry.file)
            MeshMarkers.read(text, entry.id).each { MeshEdge edge ->
                MeshNode target = index.find(edge.target)
                if (target != null && sampleFiles.contains(target.file.name)) {
                    overview.edges << edge
                }
            }
        }
        return overview
    }

    /** Reader-facing summary: counts first, then how much of the graph was seen. */
    static String summary(Overview overview) {
        if (overview == null) return 'This scope could not be read.'
        List<String> parts = []
        parts << "${overview.totalEntries} entries indexed"
        if (overview.truncated) parts << 'index capped, counts are partial'
        parts << "${overview.sample.size()} sampled"
        parts << "${overview.edges.size()} relationships inside the sample"
        return parts.join(', ') + '.'
    }

    /** A directory's entry count, without walking a huge listing twice. */
    static Integer countEntries(File directory) {
        try {
            def status = new File(directory, '.listing-status')
            if (status.isFile()) {
                def matcher = (status.getText('UTF-8') =~ /(?m)^total_available:\s*(\d+)\s*$/)
                if (matcher.find()) return matcher.group(1) as Integer
            }
            File[] files = DirectoryListing.list(directory, directory.name)
            return files.findAll { it.name != '.listing-status' }.size()
        } catch (Exception problem) {
            LogUtils.warn("Scope overview could not count ${directory.name}: ${problem.message}")
            return null
        }
    }

    private static String read(File file) {
        try {
            byte[] data = new byte[MAX_SOURCE_BYTES + 1]
            int count
            file.withInputStream { input -> count = input.read(data, 0, data.length) }
            int length = Math.min(Math.max(count, 0), MAX_SOURCE_BYTES)
            return new String(data, 0, length, 'UTF-8')
        } catch (Exception problem) {
            LogUtils.warn("Scope overview could not read ${file.name}: ${problem.message}")
            return ''
        }
    }
}