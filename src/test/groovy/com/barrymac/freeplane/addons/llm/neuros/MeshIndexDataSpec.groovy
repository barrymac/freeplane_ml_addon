package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(MeshIndex)
class MeshIndexDataSpec extends Specification {

    @Unroll
    def "filename '#filename' reads as '#title'"() {
        expect:
        MeshIndex.titleOf(filename) == title

        where:
        filename                                                                 | title
        'CARD-0f65c7c1-64de-4d96-a0ef-87b89f3a6cfe-bounded-expansion.card.md'     | 'Bounded expansion'
        '5036a147-543e-41f2-8d52-daf6f1e19030-lazy-door.card.md'                 | 'Lazy door'
        'neuros-relationship-wave.puml'                                           | 'Neuros relationship wave'
        'antonio-carlos-da-silveira-simaro.judge.md'                             | 'Antonio carlos da silveira simaro'
        '8004600-02.matter.md'                                                    | '8004600 02'
        'CARD-aaaaaaaa-1111-1111-1111-111111111111.card.md'                       | 'Untitled entry'
    }

    @Unroll
    def "filename '#filename' has uuid #uuid"() {
        expect:
        MeshIndex.uuidOf(filename) == uuid

        where:
        filename                                                                 | uuid
        'CARD-0f65c7c1-64de-4d96-a0ef-87b89f3a6cfe-bounded.card.md'              | '0f65c7c1-64de-4d96-a0ef-87b89f3a6cfe'
        '5036a147-543e-41f2-8d52-daf6f1e19030-lazy-door.card.md'                 | '5036a147-543e-41f2-8d52-daf6f1e19030'
        'no-uuid-here.md'                                                        | null
        'short-uuid.md'                                                          | null
    }
}