package com.barrymac.freeplane.addons.llm.api

/**
 * Chat-completions parameters, written once for every provider this add-on
 * speaks to.
 *
 * OpenRouter normalises the parameter names across the catalogue it serves, so
 * the add-on no longer carries per-vendor spelling or sampling rules.
 */
class ModelCompatibility {

    static Map chatParams(String provider, String model, double temperature, int maxTokens) {
        return [temperature: temperature, max_tokens: maxTokens]
    }
}
