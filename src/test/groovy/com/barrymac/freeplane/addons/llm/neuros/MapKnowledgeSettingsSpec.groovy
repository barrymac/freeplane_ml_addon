package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

@Subject(MapKnowledgeSettings)
class MapKnowledgeSettingsSpec extends Specification {

    @TempDir
    File root

    @TempDir
    File destination

    def "an unconnected map starts with safe defaults and refuses read features"() {
        when:
        def settings = MapKnowledgeSettings.load([:])

        then:
        settings.knowledgeLocation == ''
        settings.scope == ''
        settings.writeDestination == ''
        settings.resultsLimit == MapKnowledgeSettings.DEFAULT_RESULTS_LIMIT
        settings.useContext
        settings.missingForRead()?.contains('knowledge location')
    }

    def "settings are stored per map and are never inherited by another map"() {
        given:
        def firstMap = [:]
        def settings = new MapKnowledgeSettings(
                knowledgeLocation: root.path,
                scope: 'first-scope',
                writeDestination: destination.path,
                resultsLimit: 7)
        MapKnowledgeSettings.validate(settings)

        when:
        MapKnowledgeSettings.save(firstMap, settings)
        def secondMap = MapKnowledgeSettings.load([:])

        then:
        MapKnowledgeSettings.load(firstMap).scope == 'first-scope'
        secondMap.scope == ''
        secondMap.missingForRead() != null
    }

    def "valid settings round-trip through map storage"() {
        given:
        def storage = [:]
        def settings = new MapKnowledgeSettings(
                knowledgeLocation: root.path,
                scope: 'chosen',
                writeDestination: destination.path,
                cardFolder: 'saved-branches',
                resultsLimit: 9,
                useContext: false)

        when:
        MapKnowledgeSettings.validate(settings)
        MapKnowledgeSettings.save(storage, settings)
        def loaded = MapKnowledgeSettings.load(storage)

        then:
        loaded.knowledgeLocation == root.path
        loaded.scope == 'chosen'
        loaded.writeDestination == destination.path
        loaded.cardFolder == 'saved-branches'
        loaded.resultsLimit == 9
        !loaded.useContext
        loaded.missingForRead() == null
        loaded.missingForWrite() == null
    }

    def "a map can be read-only: write features refuse loudly without a destination"() {
        given:
        def settings = new MapKnowledgeSettings(
                knowledgeLocation: root.path,
                scope: 'chosen')

        expect:
        settings.missingForWrite() != null

        when:
        MapKnowledgeSettings.validate(settings)

        then:
        notThrown(Exception)
    }

    def "write destinations must already exist and be writable"() {
        given:
        def settings = new MapKnowledgeSettings(
                knowledgeLocation: root.path,
                scope: 'chosen',
                writeDestination: '/definitely/not/a/writable/place')

        when:
        MapKnowledgeSettings.validate(settings)

        then:
        thrown(IOException)
    }

    def "resource limits are bounded so one action cannot overwhelm the source"() {
        when:
        MapKnowledgeSettings.validate(new MapKnowledgeSettings(
                knowledgeLocation: root.path,
                scope: 'chosen',
                resultsLimit: MapKnowledgeSettings.MAX_RESULTS_LIMIT + 1))

        then:
        thrown(IllegalArgumentException)

        when:
        MapKnowledgeSettings.validate(new MapKnowledgeSettings(
                knowledgeLocation: root.path,
                scope: 'chosen',
                contextLimit: 100))

        then:
        thrown(IllegalArgumentException)
    }

    def "invalid stored numbers fail loudly instead of falling back silently"() {
        given:
        def storage = [:]
        storage[MapKnowledgeSettings.LOCATION_KEY] = root.path
        storage[MapKnowledgeSettings.SCOPE_KEY] = 'chosen'
        storage[MapKnowledgeSettings.RESULTS_LIMIT_KEY] = 'many'

        when:
        MapKnowledgeSettings.load(storage)

        then:
        thrown(IllegalArgumentException)
    }

    def "card folders must stay inside the write destination"() {
        when:
        MapKnowledgeSettings.validate(new MapKnowledgeSettings(
                knowledgeLocation: root.path, scope: 'chosen',
                writeDestination: destination.path, cardFolder: '/outside'))

        then:
        thrown(IllegalArgumentException)

        when:
        MapKnowledgeSettings.validate(new MapKnowledgeSettings(
                knowledgeLocation: root.path, scope: 'chosen',
                writeDestination: destination.path, cardFolder: 'nested/../escape'))

        then:
        thrown(IllegalArgumentException)
    }

    def "openSource validates the live source and keeps the map's explicit scope"() {
        given:
        new File(root, 'by-query').mkdirs()
        def settings = new MapKnowledgeSettings(
                knowledgeLocation: root.path, scope: 'chosen')

        when:
        def source = settings.openSource()

        then:
        source.scope == 'chosen'
    }
}
