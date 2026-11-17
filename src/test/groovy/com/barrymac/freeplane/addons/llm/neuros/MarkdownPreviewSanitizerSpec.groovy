package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject

@Subject(MarkdownPreviewSanitizer)
class MarkdownPreviewSanitizerSpec extends Specification {

    def "card markers, metadata, DAG and diagram references are removed from preview text"() {
        given:
        String card = '''[[card:11111111-1111-1111-1111-111111111111]]
# Add a useful capability

list: Backlog
priority: P1
type: feature
source: freeplane-addon:test
node: next-wave

---

Do the useful thing.

Evidence: `src/main/groovy/Example.groovy`.

Acceptance:
- first
- second

DAG:
[[depends-on:22222222-2222-2222-2222-222222222222]]
[[blocks:33333333-3333-3333-3333-333333333333]]

## Diagram References

- `docs/diagrams/example.puml`

[[references:44444444-4444-4444-4444-444444444444]]
'''

        when:
        String clean = MarkdownPreviewSanitizer.clean(card)

        then:
        !clean.contains('[[')
        !clean.contains('list:')
        !clean.contains('priority:')
        !clean.contains('DAG:')
        !clean.contains('Diagram References')
        clean.contains('# Add a useful capability')
        clean.contains('Do the useful thing.')
        clean.contains('- first')
        clean.contains('Evidence: `src/main/groovy/Example.groovy`.')
    }

    def "plain Markdown without work metadata is preserved"() {
        given:
        String markdown = '# Heading\n\nA paragraph.\n\n- one\n- two\n'

        expect:
        MarkdownPreviewSanitizer.clean(markdown) ==
                '# Heading\n\nA paragraph.\n\n- one\n- two'
    }
}