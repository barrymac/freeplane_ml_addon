package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(EnmeshCandidates)
class EnmeshCandidatesDataSpec extends Specification {

    @Unroll
    def "candidate '#title' ranks? #expected"() {
        given:
        String seedText = 'Draw relationships between cards and diagrams, bounded by a lens.'
        List<Map> candidates = [[id: title, title: title, text: text]]

        when:
        List<Map> ranked = EnmeshCandidates.rank(seedText, candidates, 8, 0.02)

        then:
        (ranked.size() > 0) == expected

        where:
        title      | text                                                          | expected
        'related'  | 'The relationship lens bounds how many diagrams a card draws.'   | true
        'unrelated'| 'A filing notice about court dates and registry stamps.'         | false
        'empty'    | ''                                                              | false
        'stopwords'| 'the and for that this with from into when then than'            | false
    }

    @Unroll
    def "a seed with #description yields no candidates"() {
        expect:
        EnmeshCandidates.rank(seedText, candidates, 8, 0.02).isEmpty()

        where:
        description | seedText | candidates
        'no text'   | ''       | [[id: 'a', title: 'A', text: 'cards and diagrams']]
        'no candidates'| 'cards and diagrams'| []
        'below floor'  | 'lens cards diagrams' | [[id: 'x', title: 'X', text: 'entirely different subject matter here']]
    }
}