package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

@Subject(StateMetadataFilter)
class StateMetadataFilterSpec extends Specification {

    @TempDir
    File root

    private KnowledgeSource openSource() {
        new File(root, 'by-query').mkdirs()
        new File(root, 'README.cartridge').text = '''## Surface roots for this mount
  tasks/ -> primitiveType card
  commits/ -> primitiveType commit
'''
        new File(root, 'cartridge-contract').text = '''{
  "primitiveSubtypes": {"allSubtypes": ["card", "commit"]},
  "mimeRouting": {"mimeToSubtype": {}, "fileExtensions": {}},
  "stateMachines": [{
    "name": "card",
    "governedSubtype": "card",
    "states": ["Backlog", "In Progress", "Done"],
    "initialState": "Backlog",
    "transitions": [],
    "inert": false,
    "property": ["list", "status", "state"]
  }]
}'''
        return KnowledgeSource.open(root.path, 'map-scope')
    }

    private KnowledgeEntry entry(File file, String subtype = 'card') {
        return new KnowledgeEntry(displayName: file.name, subtype: subtype, listingFile: file)
    }

    def "stateful surface shows only entries carrying declared state metadata"() {
        given:
        def source = openSource()
        def tasks = new File(root, 'tasks')
        tasks.mkdirs()
        def card = new File(tasks, 'real.card.md')
        card.text = '[[card:111]]\nlist: Backlog\n# Real work\n'
        def doc = new File(tasks, 'README.md')
        doc.text = '# Documentation\n'
        def done = new File(tasks, 'done.card.md')
        done.text = 'state: Done\n'

        when:
        def filtered = StateMetadataFilter.apply(source, 'tasks',
                [entry(card), entry(doc), entry(done)])

        then:
        filtered.size() == 2
        filtered.every { it.state in ['Backlog', 'Done'] }
        filtered.find { it.displayName == 'real.card.md' }.state == 'Backlog'
        filtered.find { it.displayName == 'done.card.md' }.state == 'Done'
    }

    def "the same declared state metadata works through by-tag"() {
        given:
        def source = openSource()
        def cards = new File(root, 'by-tag/card')
        cards.mkdirs()
        def card = new File(cards, 'real.card.md')
        card.text = 'status: In Progress\n'
        def doc = new File(cards, 'not-a-card.md')
        doc.text = '# no state\n'

        when:
        def filtered = StateMetadataFilter.apply(source, 'by-tag/card',
                [entry(card), entry(doc)])

        then:
        filtered.size() == 1
        filtered[0].state == 'In Progress'
    }

    def "surfaces without a state machine are left unchanged"() {
        given:
        def source = openSource()
        def commits = new File(root, 'commits')
        commits.mkdirs()
        def commit = new File(commits, 'change.commit.md')
        commit.text = '# commit\n'
        def entries = [entry(commit, 'commit')]

        expect:
        StateMetadataFilter.apply(source, 'commits', entries) == entries
    }
}
