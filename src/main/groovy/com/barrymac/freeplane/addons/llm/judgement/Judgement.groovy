package com.barrymac.freeplane.addons.llm.judgement

import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.freeplane.core.util.LogUtils

import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.net.HttpURLConnection
import java.net.URL

/**
 * Typed judgements from Jev (TypeSafe System One), not prose.
 *
 * One call sends a state object and named questions and returns typed answers:
 * a choice with its confidence and distribution, a probability, or a score.
 * Code owns thresholds and consequences; the model supplies the judgement.
 */
class Judgement {

    static final String ENDPOINT = 'https://api.typesafe.ai/v1/systemone'
    static final String MODEL = 'jev-latest'
    static final int TIMEOUT_SECONDS = 60
    /** Images are a danger zone upstream; the header must say so explicitly. */
    static final String IMAGE_HEADER = 'TypeSafe-Danger-Zone-Images'
    static final int MAX_IMAGE_BYTES = 8_000_000
    /** Vision endpoints reject frames that are too large or too small. */
    static final int MAX_IMAGE_DIMENSION = 1400
    static final int MIN_IMAGE_DIMENSION = 512

    /** One answer: a choice (with confidence), a probability, or a score. */
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
            if (type == 'score') return confidence != null && confidence >= threshold
            return choice != null && !escape && (confidence ?: 0d) >= threshold
        }

        String toString() {
            return type == 'noul' ? "${question}=${probability}" :
                    type == 'score' ? "${question}=${probability} (${confidence})" :
                            "${question}=${choice} (${confidence})"
        }
    }

    /** Ask every question in one call. The key comes from the environment. */
    static List<Answer> ask(Object state, Map<String, Map> questions, String apiKey) {
        return ask(state, questions, apiKey, [:], null)
    }

    /** Ask with image assets, for a vision-capable model. */
    static List<Answer> ask(Object state, Map<String, Map> questions, String apiKey,
                            Map assets, String model = null) {
        if (!questions) throw new IllegalArgumentException('Ask at least one question.')
        String key = apiKey?.trim()
        if (!key) {
            throw new LlmAddonException(
                    'Jev needs a key. Set JEV_API_KEY (or TYPESAFE_API_KEY) in the environment.')
        }
        Map payload = [model: model?.trim() ?: MODEL, state: state, questions: questions]
        if (assets) payload.assets = assets
        byte[] body = new JsonBuilder(payload).toString().getBytes('UTF-8')
        return parse(post(body, key, assets != null && !assets.isEmpty()))
    }

    static List<Answer> parse(String responseText) {
        if (!responseText?.trim()) {
            throw new LlmAddonException('Jev returned an empty response.')
        }
        def parsed = new JsonSlurper().parseText(responseText)
        def answers = parsed.answers
        if (!answers) {
            throw new LlmAddonException('Jev returned no answers.')
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

    /**
     * One image as an asset, keyed by the placeholder the questions refer to.
     * The placeholder appears literally in state or instructions and is replaced
     * by the image upstream.
     */
    static Map imageAsset(File image, String placeholder = 'image') {
        if (image == null || !image.isFile()) {
            throw new IllegalArgumentException('There is no image to send.')
        }
        if (image.length() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException('That image is too large to send.')
        }
        BufferedImage buffered = javax.imageio.ImageIO.read(image)
        if (buffered == null) {
            throw new IllegalArgumentException('That file is not an image Jev can read.')
        }
        BufferedImage scaled = prepared(buffered, MAX_IMAGE_DIMENSION, MIN_IMAGE_DIMENSION)
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(scaled, 'png', out)
        return [(placeholder): [type: 'image', format: 'png',
                                data_base64: out.toByteArray().encodeBase64().toString()]]
    }

    /**
     * Fit an image for the vision endpoint: downscale if it is too large, then
     * pad with white margins if it is too small. Never upscale — that would
     * trade one rejected dimension for another.
     */
    static BufferedImage prepared(BufferedImage image, int maxDimension, int minDimension) {
        BufferedImage scaled = scaledTo(image, maxDimension)
        int width = Math.max(scaled.width, minDimension)
        int height = Math.max(scaled.height, minDimension)
        if (width == scaled.width && height == scaled.height) return scaled
        BufferedImage padded = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        Graphics2D graphics = padded.createGraphics()
        graphics.color = java.awt.Color.WHITE
        graphics.fillRect(0, 0, width, height)
        int offsetX = (width - scaled.width).intdiv(2)
        int offsetY = (height - scaled.height).intdiv(2)
        graphics.drawImage(scaled, offsetX, offsetY, null)
        graphics.dispose()
        return padded
    }

    /** Downscale to fit a square bound, keeping the aspect ratio. */
    static BufferedImage scaledTo(BufferedImage image, int maxDimension, int minDimension = 1) {
        int width = image.width
        int height = image.height
        int longest = Math.max(width, height)
        int shortest = Math.min(width, height)
        double ratio = 1d
        if (longest > maxDimension) {
            ratio = maxDimension / (double) longest
        }
        int targetWidth = Math.max(1, (int) Math.round(width * ratio))
        int targetHeight = Math.max(1, (int) Math.round(height * ratio))
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        Graphics2D graphics = scaled.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null)
        graphics.dispose()
        return scaled
    }

    /** Headers for a call, including the image gate when assets are present. */
    static Map<String, String> headersFor(String apiKey, boolean withImages) {
        Map<String, String> headers = [Authorization: "Bearer ${apiKey}".toString()]
        if (withImages) headers[IMAGE_HEADER] = 'true'
        return headers
    }

    private static String post(byte[] body, String apiKey, boolean withImages = false) {
        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection()
        try {
            connection.requestMethod = 'POST'
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_SECONDS * 1000
            connection.readTimeout = TIMEOUT_SECONDS * 1000
            connection.setRequestProperty('Content-Type', 'application/json')
            headersFor(apiKey, withImages).each { String name, String value ->
                connection.setRequestProperty(name, value)
            }
            connection.outputStream.withStream { it.write(body) }

            int status = connection.responseCode
            InputStream stream = status >= 400 ? connection.errorStream : connection.inputStream
            String text = stream?.withStream { it.getText('UTF-8') } ?: ''
            if (status >= 400) {
                LogUtils.warn("Jev endpoint ${status}: ${text.take(300)}")
                throw new LlmAddonException("Jev refused the request (${status}).")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }
}