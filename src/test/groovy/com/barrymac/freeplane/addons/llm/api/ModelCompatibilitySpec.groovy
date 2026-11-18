package com.barrymac.freeplane.addons.llm.api

import spock.lang.Specification
import spock.lang.Subject

@Subject(ModelCompatibility)
class ModelCompatibilitySpec extends Specification {

    def "every provider gets the same chat parameters"() {
        when:
        Map params = ModelCompatibility.chatParams(provider, model, 0.3, 512)

        then:
        params == [temperature: 0.3, max_tokens: 512]

        where:
        provider     | model
        'openrouter' | 'qwen/qwen4-max'
        'openrouter' | 'deepseek/deepseek-v4.1-flash-1017'
        'novita'     | 'flux-1-schnell'
        'https://example.test/v1/chat/completions' | 'any-model'
        'openrouter' | null
    }

    def "the add-on no longer emits per-vendor parameter spellings"() {
        when:
        Map params = ModelCompatibility.chatParams('openrouter', 'gpt-6-astra', 1.0, 100)

        then:
        !params.containsKey('max_completion_tokens')
        params.temperature == 1.0
        params.max_tokens == 100
    }
}