package com.barrymac.freeplane.addons.llm

import spock.lang.Specification
import spock.lang.Subject

@Subject(ResponseParser)
class ImageResponseSpec extends Specification {

    def "chat-completions image output is read from the message"() {
        given:
        String response = '''{"choices":[{"message":{"role":"assistant",
            "images":[{"type":"image_url","image_url":{"url":"data:image/png;base64,AAA"}},
                      {"type":"image_url","image_url":{"url":"data:image/png;base64,BBB"}}]}}]}'''

        expect:
        ResponseParser.parseImageResponse(response, 'openrouter') ==
                ['data:image/png;base64,AAA', 'data:image/png;base64,BBB']
    }

    def "responses-api image items are read from the output array"() {
        given:
        String response = '''{"output":[
            {"type":"openrouter:image_generation","status":"completed","result":"data:image/png;base64,CCC"},
            {"type":"message","content":[]}]}'''

        expect:
        ResponseParser.parseImageResponse(response, 'openrouter') == ['data:image/png;base64,CCC']
    }

    def "a response with no images fails with a readable message"() {
        when:
        ResponseParser.parseImageResponse('{"choices":[{"message":{"content":"sorry"}}]}', 'openrouter')

        then:
        def problem = thrown(Exception)
        problem.message.contains('No images')
    }

    def "an empty body is rejected rather than silently accepted"() {
        when:
        ResponseParser.parseImageResponse('', 'openrouter')

        then:
        thrown(Exception)
    }

    def "Novita responses still parse through the same entry point"() {
        given:
        String response = '{"images":[{"image_url":"data:image/png;base64,DDD"}]}'

        expect:
        ResponseParser.parseImageResponse(response, 'novita') == ['data:image/png;base64,DDD']
    }
}