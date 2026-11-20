package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(ScopeOverview)
class ScopeOverviewSpec extends Specification {

    def "the summary always names entries, sample and edges"() {
        given:
        ScopeOverview.Overview overview = new ScopeOverview.Overview(
                totalEntries: 21, truncated: false, sample: [], edges: [])
        overview.sample << new MeshNode(title: 'A', file: new File('/tmp/a.card.md'))
        overview.edges << new MeshEdge()

        when:
        String summary = ScopeOverview.summary(overview)

        then:
        summary.contains('21 entries indexed')
        summary.contains('1 sampled')
        summary.contains('1 relationships')
    }

    def "a truncated index says so"() {
        given:
        ScopeOverview.Overview overview = new ScopeOverview.Overview(
                totalEntries: 400, truncated: true)

        expect:
        ScopeOverview.summary(overview).contains('index capped')
    }

    def "a null or unreadable scope yields an empty overview"() {
        when:
        ScopeOverview.Overview overview = ScopeOverview.plan(null)

        then:
        overview.totalEntries == 0
        overview.sample.isEmpty()
        overview.edges.isEmpty()
        ScopeOverview.summary(overview).contains('0 entries')
    }
}