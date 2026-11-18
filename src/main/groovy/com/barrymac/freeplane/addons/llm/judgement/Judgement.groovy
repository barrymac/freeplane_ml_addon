package com.barrymac.freeplane.addons.llm.judgement

import com.barrymac.freeplane.addons.llm.Keys
import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.freeplane.core.util.LogUtils

import java.net.HttpURLConnection
import java.net.URL

/**
 * Typed judgements, not prose.
 *
 * Two transports answer the same shape — a state object and named questions:
 *   - the decision model on OpenRouter's decisions endpoint (choice answers with
 *     confidence, noul answers as probabilities),
 *   - Jev (System One) at TypeSafe, which additionally returns probabilities.
 *
 * The caller supplies the questions; code owns thresholds and consequences.
 */
class Judgement {

    static final String DECISION_ENDPOINT = 'https://openrouter.ai/api/alpha/decisions'
    static final String DECISION_MODEL = 'inception/mercury-decide'
    static final String JEV_ENDPOINT = 'https://api.typesafe.ai/v1/systemone'
    static final String JEV_MODEL = 'jev-latest'
    static final int TIMEOUT_SECONDS = 60

    enum Transport { DECISION, JEV }

    /** One answer: a choice (with confidence) or a probability. */
    static class Answer {
        String question
        String type
        String choice
        Double probability
        Double confidence
        Map<String, Double> probabilities = [:]

        boolean isEscape() {
            return type == 'choice' && (choice == 'none_fits' || choice == 'cannot_tell' || choice == 'none')
        }

        /** Actionable only when confident and not an escape. */
        boolean isDecisive(double threshold = 0.75) {
            if (type == 'noul') return probability != null && probability >= threshold
            return choice != null && !escape && (confidence ?: 0d) >= threshold
        }

        String toString() {
            return type == 'noul' ?
                    "${question}=${probability}" :
                    "${question}=${choice} (${confidence})"
        }
    }

    /** Ask every question in one call; keys come from the environment first. */
    static List<Answer> ask(Transport transport, Object state, Map<String, Map> questions,
                            String apiKey, String model = null) {
        if (!questions) throw new IllegalArgumentException('Ask at least one question.')
        String key = apiKey?.trim()
        if (!key) {
            throw new LlmAddonException(
                    transport == Transport.JEV ?
                            'Jev needs a key. Set JEV_API_KEY (or TYPESAFE_API_KEY) in the environment.' :
                            'The decision model needs a key. Set OPENROUTER_API_KEY in the environment.')
        }
        String endpoint = transport == Transport.JEV ? JEV_ENDPOINT : DECISION_ENDPOINT
        String chosenModel = model ?: (transport == Transport.JEV ? JEV_MODEL : DECISION_MODEL)
        byte[] body = new JsonBuilder(payloadFor(transport, chosenModel, state, questions))
                .toString().getBytes('UTF-8')

        String response = post(endpoint, key, body)
        return parse(transport, response)
    }

    /** Payload shape per transport, kept pure so it can be checked without a network. */
    static Map payloadFor(Transport transport, String model, Object state, Map<String, Map> questions) {
        return [model: model, state: state, questions: questions]
    }

    static List<Answer> parse(Transport transport, String responseText) {
        if (!responseText?.trim()) {
            throw new LlmAddonException('The judgement service returned an empty response.')
        }
        def parsed = new JsonSlurper().parseText(responseText)
        def answers = parsed.answers
        if (!answers) {
            throw new LlmAddonException('The judgement service returned no answers.')
        }
        List<Answer> results = []
        answers.each { String name, def value ->
            def answer = new Answer(question: name, type: value.type ?: 'choice')
            if (answer.type == 'noul') {
                answer.probability = value.noul as Double
            } else if (answer.type == 'score') {
                answer.probability = value.score as Double
                answer.confidence = value.confidence as Double
                answer.choice = name
            } else {
                answer.choice = value.choice
                answer.confidence = value.confidence as Double
                if (value.probabilities) {
                    value.probabilities.each { String k, def v -> answer.probabilities[k] = v as Double }
                }
            }
            results << answer
        }
        return results
    }

    private static String post(String endpoint, String apiKey, byte[] body) {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection()
        try {
            connection.requestMethod = 'POST'
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_SECONDS * 1000
            connection.readTimeout = TIMEOUT_SECONDS * 1000
            connection.setRequestProperty('Content-Type', 'application/json')
            connection.setRequestProperty('Authorization', "Bearer ${apiKey}")
            connection.outputStream.withStream { it.write(body) }

            int status = connection.responseCode
            InputStream stream = status >= 400 ? connection.errorStream : connection.inputStream
            String text = stream?.withStream { it.getText('UTF-8') } ?: ''
            if (status >= 400) {
                LogUtils.warn("Judgement endpoint ${status}: ${text.take(300)}")
                String hint = status == 404 ?
                        ' The model may not be served on this key; Jev is the other transport.' : ''
                throw new LlmAddonException(
                        "The judgement service refused the request (${status}).${hint}")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }
}