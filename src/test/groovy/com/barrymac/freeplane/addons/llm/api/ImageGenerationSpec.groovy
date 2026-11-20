package com.barrymac.freeplane.addons.llm.api

import com.barrymac.freeplane.addons.llm.ConfigKeys
import com.barrymac.freeplane.addons.llm.Keys
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Subject

@Subject(ImageGeneration)
class ImageGenerationSpec extends Specification {

    @TempDir
    File directory

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

    def "env key for openrouter, file key for novita"() {
        given:
        ConfigKeys.OVERRIDE_DIR = directory
        ConfigKeys.write('novita.key', 'nov-test-key')

        expect:
        // OpenRouter: env wins (this machine has OPENROUTER_API_KEY set)
        Keys.environment(Keys.OPENROUTER) == null ||
                ImageGeneration.keyFor('openrouter') == Keys.environment(Keys.OPENROUTER).trim()
        // Novita: reads from the settings file
        ImageGeneration.keyFor('novita') == 'nov-test-key'
        ImageGeneration.isReady('openrouter')

        cleanup:
        ConfigKeys.OVERRIDE_DIR = null
    }

    def "a provider with no key anywhere is not ready"() {
        given:
        ConfigKeys.OVERRIDE_DIR = directory
        // Clear any settings left by a previous test in the same temp dir
        new File(directory, ConfigKeys.SETTINGS_FILE).delete()

        when:
        String novitaKey = ImageGeneration.keyFor('novita')

        then:
        novitaKey == '' || novitaKey == null
        !ImageGeneration.isReady('novita')

        cleanup:
        ConfigKeys.OVERRIDE_DIR = null
    }
}