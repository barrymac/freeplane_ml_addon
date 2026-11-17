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
