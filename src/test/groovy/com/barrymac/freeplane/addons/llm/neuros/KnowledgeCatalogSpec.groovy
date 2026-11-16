package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

@Subject(KnowledgeCatalog)
class KnowledgeCatalogSpec extends Specification {

    @TempDir
    File root

    private KnowledgeSource openSource() {
        new File(root, 'by-query').mkdirs()
        return KnowledgeSource.open(root.path, 'map-scope')
    }

    def "catalog is generated entirely from live cartridge metadata"() {
        given:
        new File(root, 'README.cartridge').text = '''# Example cartridge
## Surface roots for this mount
  observations/                      -> primitiveType observation
  influences/                        -> primitiveType influence
## Automatic MIME routing
application/x-invented             ->  observation
'''
        new File(root, 'cartridge-contract').text = '''{
  "primitiveSubtypes": {
    "allSubtypes": ["observation", "influence", "door-like-thing"]
  },
  "mimeRouting": {
    "mimeToSubtype": {
      "application/x-invented": "observation",
      "text/x-door": "door-like-thing"
    },
    "fileExtensions": {
      "obs": "application/x-invented",
      "door": "text/x-door"
    }
  }
}'''
        new File(root, 'handshake').text = '''{
  "capabilities": ["external-ref-v0", "cartridge-contract-v0", "future-door-v9"]
}'''
        def source = openSource()

        when:
        def catalog = KnowledgeCatalog.read(source)

        then:
        catalog.surfaces*.name == ['influences', 'observations']
        catalog.knowledgeTypes == ['door-like-thing', 'influence', 'observation']
        catalog.contentTypes*.subtype == ['observation', 'door-like-thing']
        catalog.contentTypes.find { it.mime == 'application/x-invented' }.extensions == ['obs']
        catalog.doorCapabilities == ['external-ref-v0', 'future-door-v9']
    }

    def "missing contract falls back to the live by-tag type listing"() {
        given:
        new File(root, 'README.cartridge').text = '# Cartridge\n'
        new File(root, 'by-tag').mkdirs()
        ['observation', 'influence'].each { new File(root, "by-tag/${it}").mkdirs() }
        def source = openSource()

        when:
        def catalog = KnowledgeCatalog.read(source)

        then:
        catalog.knowledgeTypes == ['influence', 'observation']
        catalog.warnings.any { it.contains('detailed cartridge contract') }
    }

    def "no available metadata fails honestly instead of inventing a catalog"() {
        given:
        def source = openSource()

        when:
        KnowledgeCatalog.read(source)

        then:
        thrown(IOException)
    }

    def "surface root parsing ignores non-surface sections"() {
        when:
        def surfaces = KnowledgeCatalog.parseSurfaceRoots('''## Surface roots for this mount
  cards/ -> primitiveType card
  commits/ -> primitiveType commit
## Other section
  should-not-appear/ -> primitiveType other
''')

        then:
        surfaces*.name == ['cards', 'commits']
    }
}
