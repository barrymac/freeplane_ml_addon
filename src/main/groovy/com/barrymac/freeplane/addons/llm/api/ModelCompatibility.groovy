package com.barrymac.freeplane.addons.llm.api

/**
 * Parameter compatibility for the OpenAI chat-completions API across model
 * generations.
 *
 * Two breaks are encoded here (both live in the wild since the GPT-5 line):
 *   1. `max_tokens` is rejected by newer OpenAI models in favour of
 *      `max_completion_tokens`.
 *   2. reasoning models (gpt-5 and up, gpt-6, and the o-series) reject any
 *      `temperature` other than the default, so the parameter is omitted
 *      entirely for them.
 *
 * OpenRouter normalises both parameter names for every model it serves, so
 * the legacy pair keeps working there and for classic OpenAI models
 * (gpt-4/gpt-3.5-turbo).
 */
class ModelCompatibility {

    /** True for OpenAI models that only accept the modern parameter set. */
    static boolean isModernOpenAiModel(String model) {
        if (!model) return false
        // gpt-5*, gpt-6*, o1/o3/o4… reasoning series, gpt-oss open weights
        model ==~ /(?i)^(gpt-[56](\.|\b)|o\d|gpt-oss).*/
    }

    /**
     * The generation-limit and sampling parameters for one model, using
     * whichever spelling that model accepts.
     *
     * @param provider   'openai' | 'openrouter' | 'novita'
     * @param model      the model id
     * @param temperature requested sampling temperature
     * @param maxTokens   requested generation limit
     * @return map to merge into the request payload (never null values)
     */
    static Map<String, Object> chatParams(String provider, String model,
                                          double temperature, int maxTokens) {
        boolean openAiModern = (provider?.toLowerCase() == 'openai') && isModernOpenAiModel(model)
        if (openAiModern) {
            return ['max_completion_tokens': maxTokens]
        }
        return ['temperature': temperature, 'max_tokens': maxTokens]
    }
}
