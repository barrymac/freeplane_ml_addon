package com.barrymac.freeplane.addons.llm.api

import spock.lang.Specification
import spock.lang.Subject

@Subject(ImageGeneration)
class ImageGenerationSpec extends Specification {

    def "the default provider is OpenRouter's image model"() {
        expect:
        ImageGeneration.DEFAULT_OPENROUTER_IMAGE_MODEL == 'google/gemini-3.1-flash-image'
        ImageGeneration.OPENROUTER == 'openrouter'
    }

    def "an OpenRouter image request asks for image output on the chat endpoint"() {
        when:
        Map payload = ImageGeneration.openRouterPayload('google/gemini-3.1-flash-image', 'a red fox', 2)

        then:
        payload.model == 'google/gemini-3.1-flash-image'
        payload.modalities == ['image', 'text']
        payload.messages == [[role: 'user', content: 'a red fox']]
        payload.n == 2
    }

    def "image count is bounded to what providers accept"() {
        expect:
        ImageGeneration.openRouterPayload('m', 'p', 99).n == 4
        ImageGeneration.openRouterPayload('m', 'p', 0).n == 1
    }

    def "a blank model or prompt fails loudly"() {
        when:
        ImageGeneration.openRouterPayload('', 'p')

        then:
        def problem = thrown(IllegalArgumentException)
        problem.message.contains('image model')

        when:
        ImageGeneration.openRouterPayload('m', '   ')

        then:
        thrown(IllegalArgumentException)
    }

    def "each provider reads its own key"() {
        given:
        def config = [getProperty: { String key, Object fallback -> [
                (com.barrymac.freeplane.addons.llm.ConfigKeys.API_KEY): 'or-key',
                'novita.key': 'novita-key'
        ][key] ?: fallback }] as Map

        expect:
        ImageGeneration.keyFor('openrouter', config) == 'or-key'
        ImageGeneration.keyFor('novita', config) == 'novita-key'
        ImageGeneration.isReady('openrouter', config)
    }

    def "a provider with no key is not ready"() {
        given:
        def config = [getProperty: { String key, Object fallback -> fallback }] as Map

        expect:
        !ImageGeneration.isReady('openrouter', config)
        !ImageGeneration.isReady('novita', config)
    }
}