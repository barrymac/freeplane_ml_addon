package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject

/**
 * The rendition parser, checked against the exact body shape the substrate's
 * sitter-structure-v1 surface serves (fixture taken verbatim from the live
 * brockenhurst scope on 2026-11-21).
 */
@Subject(CodeRendition)
class CodeRenditionSpec extends Specification {

    private static final String LIVE_BODY = '''kind: transduced-rendition
source_sha256: ada19f4a742dea9bdb203da96e10265b793ea7845a3010641a1f960c2900911
source_evidence: 3b8a9e50-0a51-45a6-b6e1-10fb593118e1
transducer: sitter-structure-v1
transducer_fingerprint: sitter-structure-v1:1a0071591cce59b965e8acd2442c2d76
method: sitter
partial: false
---
[[structure-of:3b8a9e50-0a51-45a6-b6e1-10fb593118e1]]

# structure: index.ts
lang=typescript lane=tree-sitter errors=0 decls=1 imports=5 resolved=0

## imports
- ../../../tool-types.js [RegisteredTool] (unresolved)
- ./google_search_console_list_sites.js [googleSearchConsoleListSites] (unresolved)
- ./google_search_console_inspect_url.js [googleSearchConsoleInspectUrl] (unresolved)

## function createGoogleSearchConsoleTools 12:8-19:2
sig: function createGoogleSearchConsoleTools(): RegisteredTool[]
'''

    def "the live rendition body parses completely"() {
        when:
        CodeRendition rendition = CodeRendition.parse('469d7f21-898c-4286-9632-cd280e8cb79c', LIVE_BODY)

        then:
        rendition.uuid == '469d7f21-898c-4286-9632-cd280e8cb79c'
        rendition.file == 'index.ts'
        rendition.title() == 'index.ts'
        rendition.lang == 'typescript'
        rendition.lane == 'tree-sitter'
        rendition.errors == 0
        !rendition.partial
        !rendition.degraded
        rendition.resolvedImports == 0
        rendition.imports.size() == 3
        rendition.imports.every { !it.resolved }
        rendition.imports[0].specifier == '../../../tool-types.js'
        rendition.imports[0].names == 'RegisteredTool'
        rendition.declarations.size() == 1
        rendition.declarations[0].kind == 'function'
        rendition.declarations[0].name == 'createGoogleSearchConsoleTools'
        rendition.declarations[0].range == '12:8-19:2'
        rendition.declarations[0].signature == 'function createGoogleSearchConsoleTools(): RegisteredTool[]'
        rendition.declarations[0].title() == 'function createGoogleSearchConsoleTools'
    }

    def "a degraded rendition still parses and says so"() {
        given:
        String body = '''partial: true
---
# structure: broken.rs
lang=rust lane=tree-sitter errors=3 decls=0 imports=0 resolved=0
'''

        when:
        CodeRendition rendition = CodeRendition.parse('f821042c', body)

        then:
        rendition.partial
        rendition.errors == 3
        rendition.degraded
        rendition.declarations.isEmpty()
    }

    def "an empty or marker body parses to an honest empty rendition"() {
        when:
        CodeRendition rendition = CodeRendition.parse('x', '# structure: lone.py\nlang=python lane=tree-sitter errors=0 decls=0 imports=0 resolved=0\n')

        then:
        !rendition.degraded
        rendition.imports.isEmpty()
        rendition.declarations.isEmpty()
        rendition.title() == 'lone.py'
    }

    def "resolved imports keep their flag and names"() {
        when:
        CodeRendition.ImportRef ref = CodeRendition.ImportRef.parse('./util.js [helper, maker]')

        then:
        ref.resolved
        ref.specifier == './util.js'
        ref.names == 'helper, maker'
    }

    def "a null body or missing uuid is refused loudly"() {
        when:
        CodeRendition.parse('uuid', null)

        then:
        thrown(IllegalArgumentException)

        when:
        CodeRendition.parse('  ', 'body')

        then:
        thrown(IllegalArgumentException)
    }
}
