package com.barrymac.freeplane.addons.llm.api

import com.barrymac.freeplane.addons.llm.ConfigKeys

/**
 * Image generation through a chat provider.
 *
 * OpenRouter serves image-output models on the same chat-completions endpoint
 * the add-on already uses, so one key and one transport cover text and images.
 * Novita stays available for its cheaper diffusion models.
 */
class ImageGeneration {

    static final String DEFAULT_OPENROUTER_IMAGE_MODEL = 'google/gemini-3.1-flash-image'
    static final String OPENROUTER = 'openrouter'
    static final String NOVITA = 'novita'

    /** Chat-completions payload for an image-output model. */
    static Map openRouterPayload(String model, String prompt, int count = 1) {
        if (!model?.trim()) throw new IllegalArgumentException('Choose an image model first.')
        if (!prompt?.trim()) throw new IllegalArgumentException('The image prompt is empty.')
        int images = Math.min(Math.max(count, 1), 4)
        return [
                model     : model.trim(),
                messages  : [[role: 'user', content: prompt.trim()]],
                modalities: ['image', 'text'],
                // One request per image keeps the reply shape predictable.
                n         : images
        ]
    }

    /** Provider key to use, given what the map has configured. */
    static String keyFor(String provider, def config) {
        if (provider == NOVITA) {
            return config.getProperty('novita.key', '')?.trim()
        }
        return ConfigKeys.read(config, ConfigKeys.API_KEY, '')?.trim()
    }

    /** True when this provider has everything it needs to run. */
    static boolean isReady(String provider, def config) {
        return keyFor(provider, config)
    }
}