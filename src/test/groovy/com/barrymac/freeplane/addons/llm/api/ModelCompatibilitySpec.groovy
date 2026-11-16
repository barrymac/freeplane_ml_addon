package com.barrymac.freeplane.addons.llm.api

import spock.lang.Specification
import spock.lang.Subject

@Subject(ModelCompatibility)
class ModelCompatibilitySpec extends Specification {

    def "modern OpenAI model families are detected"() {
        expect:
        ModelCompatibility.isModernOpenAiModel(model) == expected

        where:
        model            || expected
        'gpt-5.2'        || true
        'gpt-5.6-sol'    || true
        'gpt-6-astra'    || true
        'gpt-6.1-terra'  || true
        'o3-mini'        || true
        'o4-mini'        || true
        'gpt-oss-120b'   || true
        'GPT-6-Astra'    || true
        'gpt-4'          || false
        'gpt-4o'         || false
        'gpt-3.5-turbo'  || false
        'deepseek/deepseek-v4.1-flash-20260910' || false
        ''               || false
        null             || false
    }

    def "openai modern models get max_completion_tokens and no temperature"() {
        when:
        def params = ModelCompatibility.chatParams('openai', 'gpt-6-astra', 0.7d, 2000)

        then:
        params['max_completion_tokens'] == 2000
        !params.containsKey('temperature')
        !params.containsKey('max_tokens')
    }

    def "openai classic models keep the legacy parameter pair"() {
        when:
        def params = ModelCompatibility.chatParams('openai', 'gpt-4o', 0.7d, 2000)

        then:
        params['temperature'] == 0.7d
        params['max_tokens'] == 2000
        !params.containsKey('max_completion_tokens')
    }

    def "openrouter always gets the legacy pair regardless of model"() {
        when:
        def params = ModelCompatibility.chatParams('openrouter', 'gpt-6-astra', 0.7d, 2000)

        then:
        params['temperature'] == 0.7d
        params['max_tokens'] == 2000
        !params.containsKey('max_completion_tokens')
    }

    def "provider matching is case-insensitive"() {
        when:
        def params = ModelCompatibility.chatParams('OpenAI', 'gpt-5.4-mini', 0.7d, 1500)

        then:
        params['max_completion_tokens'] == 1500
        !params.containsKey('temperature')
    }
}
