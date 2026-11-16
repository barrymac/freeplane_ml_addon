package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject

@Subject(NeurosLimits)
class NeurosLimitsSpec extends Specification {

    def "an unconfigured map starts with conservative limits"() {
        when:
        def limits = NeurosLimits.load([:])

        then:
        limits.resultsLimit == NeurosLimits.DEFAULT_RESULTS_LIMIT
        limits.writeWaitSeconds == NeurosLimits.DEFAULT_WRITE_WAIT_SECONDS
        limits.useContext
        limits.cardFolder == NeurosLimits.DEFAULT_CARD_FOLDER
    }

    def "optional limits are stored per map and are never inherited by another map"() {
        given:
        def firstMap = [:]
        firstMap[NeurosLimits.RESULTS_LIMIT_KEY] = '7'

        when:
        def secondMap = NeurosLimits.load([:])

        then:
        NeurosLimits.load(firstMap).resultsLimit == 7
        secondMap.resultsLimit == NeurosLimits.DEFAULT_RESULTS_LIMIT
    }

    def "resource limits are bounded so one action cannot overwhelm the source"() {
        when:
        NeurosLimits.validate(new NeurosLimits(
                resultsLimit: NeurosLimits.MAX_RESULTS_LIMIT + 1))

        then:
        thrown(IllegalArgumentException)

        when:
        NeurosLimits.validate(new NeurosLimits(contextLimit: 100))

        then:
        thrown(IllegalArgumentException)
    }

    def "invalid stored numbers fail loudly instead of falling back silently"() {
        given:
        def storage = [:]
        storage[NeurosLimits.RESULTS_LIMIT_KEY] = 'many'

        when:
        NeurosLimits.load(storage)

        then:
        thrown(IllegalArgumentException)
    }

    def "card folders must stay inside the write destination"() {
        when:
        NeurosLimits.validate(new NeurosLimits(cardFolder: '/outside'))

        then:
        thrown(IllegalArgumentException)

        when:
        NeurosLimits.validate(new NeurosLimits(cardFolder: 'nested/../escape'))

        then:
        thrown(IllegalArgumentException)
    }

    def "contextOptions exposes the bounded query budget"() {
        given:
        def limits = new NeurosLimits(ancestorLimit: 3, childLimit: 4,
                noteLimit: 50, contextLimit: 500)

        expect:
        limits.contextOptions() == [
                ancestorLimit: 3,
                childLimit: 4,
                noteLimit: 50,
                totalLimit: 500
        ]
    }
}
