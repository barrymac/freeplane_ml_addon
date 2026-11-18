package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject

@Subject(EnmeshCandidates)
class EnmeshCandidatesSpec extends Specification {

    def "shared vocabulary ranks a candidate above an unrelated one"() {
        given:
        String seed = 'Draw relationships between cards and diagrams, bounded by a lens.'
        List<Map> candidates = [
                [id: 'unrelated', title: 'Unrelated', text: 'A filing notice about court dates and registry stamps.'],
                [id: 'related', title: 'Related', text: 'The relationship lens bounds how many diagrams a card draws.'],
                [id: 'mild', title: 'Mild', text: 'Cards hold text.']
        ]

        when:
        List<Map> ranked = EnmeshCandidates.rank(seed, candidates, 3, 0.05)

        then:
        ranked.first().id == 'related'
        ranked*.id.contains('unrelated') == false
    }

    def "entries below the floor are not asked about"() {
        given:
        List<Map> candidates = [[id: 'x', title: 'X', text: 'entirely different subject matter here']]

        expect:
        EnmeshCandidates.rank('lens cards diagrams relationships', candidates, 8, 0.2).isEmpty()
    }

    def "ranking is bounded and stable"() {
        given:
        String seed = 'lens cards diagrams relationships bounded'
        List<Map> candidates = (1..20).collect { n ->
                [id: "c${n}".toString(), title: "C${n}".toString(),
                 text: 'cards diagrams relationships bounded lens'.toString()]
        }

        when:
        List<Map> ranked = EnmeshCandidates.rank(seed, candidates, 5, 0.0)

        then:
        ranked.size() == 5
        ranked*.id == ['c1', 'c2', 'c3', 'c4', 'c5']
    }

    def "no text means no candidates rather than an exception"() {
        expect:
        EnmeshCandidates.rank('', [[id: 'a', title: 'A', text: 'x']]).isEmpty()
        EnmeshCandidates.rank('words here', []).isEmpty()
    }
}
