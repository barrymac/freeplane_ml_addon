package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject

@Subject(NeurosPreviewAction)
class NeurosPreviewActionSpec extends Specification {

    def "Markdown uses Freeplane's own renderer"() {
        when:
        String html = NeurosPreviewAction.markdownToHtml('# Heading\n\n**bold**')

        then:
        html.contains('<h1 id="heading">Heading</h1>')
        html.contains('<strong>bold</strong>')
    }

    def "Markdown can be rendered to an embedded image"() {
        when:
        def html = NeurosPreviewAction.markdownToHtml('# Heading\n\n**bold**')
        def image = MarkdownImageRenderer.renderHtml(html, 600, 1200)

        then:
        image.width == 600
        image.height >= 160
        image.height <= 1200
    }

    def "a longer document produces a taller image"() {
        when:
        def shortImage = MarkdownImageRenderer.renderHtml(
                NeurosPreviewAction.markdownToHtml('# One line'), 600, 2400)
        def longImage = MarkdownImageRenderer.renderHtml(
                NeurosPreviewAction.markdownToHtml(('A full paragraph.\n\n' * 20)), 600, 2400)

        then:
        longImage.height > shortImage.height + 100
    }

    def "an unchanged preview reuses the same cached file"() {
        given:
        def config = [freeplaneUserDirectory: '/tmp/opencode/preview-cache-spec']

        when:
        File first = NeurosPreviewAction.previewCacheFile(config, '# Card\n\nbody', 'markdown')
        File again = NeurosPreviewAction.previewCacheFile(config, '# Card\n\nbody', 'markdown')
        File changed = NeurosPreviewAction.previewCacheFile(config, '# Card\n\nother', 'markdown')

        then:
        first == again
        first.name != changed.name
        first.name.startsWith('markdown-')
    }

    def "PlantUML detection is content based, not filename based"() {
        expect:
        NeurosPreviewAction.isPlantUml('@startuml\nAlice -> Bob\n@enduml')
        !NeurosPreviewAction.isPlantUml('# Markdown only')
    }

    def "a missing PlantUML command fails clearly"() {
        when:
        PlantumlRenderer.renderPng('@startuml\nAlice -> Bob\n@enduml',
                '/definitely/not/plantuml', 1)

        then:
        def problem = thrown(IOException)
        problem.message.contains('not available')
    }
}
