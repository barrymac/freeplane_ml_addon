package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification

/**
 * The shipped probe pair must stay contradictory.
 *
 * This is the only known-positive fixture for disagreement detection, so the guard
 * is deliberately mechanical: the two claims, the dates they name, and the mutual
 * reference are asserted as literal facts. If someone edits the pair so they agree,
 * the fixture stops testing anything and this spec fails instead of the judgement
 * quietly reporting "no contradiction" forever.
 */
class DemoProbeSpec extends Specification {

    private static final String PROBE = 'demo/probe'
    private static final String FIRST = 'CARD-56fa7e49-4186-46c9-8f1f-654d5a801ff1-probe-notice-filed-30-july.card.md'
    private static final String SECOND = 'CARD-caa012e7-7dd4-41f8-825b-97eec07883f1-probe-nothing-filed-before-28-august.card.md'

    def "both probe cards ship, carry their identity, and reference each other"() {
        given:
        File first = new File(PROBE, FIRST)
        File second = new File(PROBE, SECOND)

        expect:
        first.isFile()
        second.isFile()
        first.text.contains('filed with the court on 30 July 2026')
        second.text.contains('No filing was made before 28 August 2026')
        first.text.contains('[[card:56fa7e49-4186-46c9-8f1f-654d5a801ff1]]')
        second.text.contains('[[card:caa012e7-7dd4-41f8-825b-97eec07883f1]]')
        first.text.contains('[[references:caa012e7-7dd4-41f8-825b-97eec07883f1]]')
        second.text.contains('[[references:56fa7e49-4186-46c9-8f1f-654d5a801ff1]]')
    }

    def "the reference resolves inside the probe folder"() {
        given:
        File first = new File(PROBE, FIRST)
        MeshIndex index = MeshIndex.load(new File(PROBE))
        MeshNode seed = index.forFile(first)

        when:
        List<MeshEdge> edges = NeurosMeshAction.readEdges(first, seed)

        then:
        edges.size() == 1
        edges[0].marker == 'references'
        index.find(edges[0].target) != null
    }

    def "the two claims name dates that cannot both hold"() {
        given:
        int filedOn = dayOfYear(new File(PROBE, FIRST).text)
        int nothingBefore = dayOfYear(new File(PROBE, SECOND).text)

        expect:
        // The notice is claimed before the other card says anything was filed.
        filedOn < nothingBefore
    }

    private static int dayOfYear(String text) {
        def matcher = (text =~ /(\d{1,2}) (July|August) 2026/)
        assert matcher.find()
        int day = matcher.group(1) as int
        int monthOffset = matcher.group(2) == 'July' ? 181 : 212
        return monthOffset + day
    }
}