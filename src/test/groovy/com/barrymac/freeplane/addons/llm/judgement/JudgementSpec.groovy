package com.barrymac.freeplane.addons.llm.judgement

import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException
import spock.lang.Specification
import spock.lang.Subject

@Subject(Judgement)
class JudgementSpec extends Specification {

    def "a choice answer reads with its confidence and distribution"() {
        given:
        String response = '''{"answers":{"q0":{"type":"choice","choice":"contradicts",
            "confidence":0.91,"probabilities":{"contradicts":0.91,"not_contradiction":0.06,"cannot_tell":0.03}}}}'''

        when:
        List<Judgement.Answer> answers = Judgement.parse(response)

        then:
        answers.size() == 1
        def answer = answers[0]
        answer.choice == 'contradicts'
        answer.confidence == 0.91
        answer.probabilities['cannot_tell'] == 0.03
        answer.decisive
    }

    def "a low-confidence answer is not actionable"() {
        given:
        String response = '{"answers":{"q0":{"type":"choice","choice":"contradicts","confidence":0.41}}}'

        expect:
        !Judgement.parse(response)[0].decisive
    }

    def "an escape is never turned into a verdict"() {
        given:
        String response = '{"answers":{"q0":{"type":"choice","choice":"cannot_tell","confidence":0.88}}}'

        when:
        def answer = Judgement.parse(response)[0]

        then:
        answer.escape
        !answer.decisive
    }

    def "a probability answer is actionable above the threshold"() {
        given:
        String response = '{"answers":{"n0":{"type":"noul","noul":0.83}}}'

        when:
        def answer = Judgement.parse(response)[0]

        then:
        answer.type == 'noul'
        answer.probability == 0.83
        answer.decisive
    }

    def "a score answer keeps its expected value"() {
        given:
        String response = '{"answers":{"s0":{"type":"score","score":1.7,"confidence":0.55,"legend":{},"probabilities":{}}}}'

        when:
        def answer = Judgement.parse(response)[0]

        then:
        answer.probability == 1.7
        answer.confidence == 0.55
        !answer.decisive
    }

    def "no answers is an error, not an empty verdict list"() {
        when:
        Judgement.parse('{"usage":{}}')

        then:
        thrown(LlmAddonException)
    }

    def "an empty response is rejected"() {
        when:
        Judgement.parse('')

        then:
        thrown(LlmAddonException)
    }

    def "asking without a key fails loudly with what to set"() {
        when:
        Judgement.ask([a: 1], [q0: [type: 'noul', instructions: 'x']], '')

        then:
        def problem = thrown(LlmAddonException)
        problem.message.contains('JEV_API_KEY')
    }

    def "images are fitted inside the accepted bounds"() {
        given:
        def wide = new java.awt.image.BufferedImage(3000, 1000, java.awt.image.BufferedImage.TYPE_INT_RGB)
        def small = new java.awt.image.BufferedImage(900, 220, java.awt.image.BufferedImage.TYPE_INT_RGB)

        when:
        def fittedWide = Judgement.prepared(wide, Judgement.MAX_IMAGE_DIMENSION, Judgement.MIN_IMAGE_DIMENSION)
        def fittedSmall = Judgement.prepared(small, Judgement.MAX_IMAGE_DIMENSION, Judgement.MIN_IMAGE_DIMENSION)

        then:
        fittedWide.width <= Judgement.MAX_IMAGE_DIMENSION
        fittedWide.height <= Judgement.MAX_IMAGE_DIMENSION
        Math.min(fittedSmall.width, fittedSmall.height) >= Judgement.MIN_IMAGE_DIMENSION
        Math.max(fittedSmall.width, fittedSmall.height) <= Judgement.MAX_IMAGE_DIMENSION
    }

    private static File imageFile(String suffix, int width = 800, int height = 800) {
        File file = File.createTempFile('test', suffix)
        file.deleteOnExit()
        def image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        javax.imageio.ImageIO.write(image, suffix.replace('.', ''), file)
        return file
    }

    def "an image becomes an asset keyed by its placeholder"() {
        given:
        File png = imageFile('.png')

        when:
        Map assets = Judgement.imageAsset(png, 'shot')

        then:
        assets.keySet() == ['shot'] as Set
        assets.shot.type == 'image'
        assets.shot.format == 'png'
        assets.shot.data_base64
    }

    def "every image is re-encoded as png at an accepted size"() {
        given:
        File jpg = imageFile('.jpg', 300, 300)

        when:
        def asset = Judgement.imageAsset(jpg).values().first()

        then:
        asset.format == 'png'
        asset.data_base64
    }

    def "a missing image fails loudly"() {
        when:
        Judgement.imageAsset(new File('/definitely/not/here.png'))

        then:
        thrown(IllegalArgumentException)
    }

    def "the image gate header is sent only when assets are present"() {
        expect:
        Judgement.headersFor('k', false) == [Authorization: 'Bearer k']
        Judgement.headersFor('k', true)[Judgement.IMAGE_HEADER] == 'true'
    }

    def "the endpoint and model are the documented Jev ones"() {
        expect:
        Judgement.ENDPOINT == 'https://api.typesafe.ai/v1/systemone'
        Judgement.MODEL == 'jev-latest'
    }
}