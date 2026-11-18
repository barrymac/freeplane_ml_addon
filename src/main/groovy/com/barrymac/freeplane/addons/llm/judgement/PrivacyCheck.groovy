package com.barrymac.freeplane.addons.llm.judgement

import com.barrymac.freeplane.addons.llm.Keys

/**
 * Judges whether a preview image is safe to publish, by asking about concrete
 * things that can be seen rather than a vague "is it safe".
 *
 * Each check is its own yes/no proposition, because several can be true at once:
 * an image may hold a UUID and a path. Code owns the policy — the worst single
 * probability decides — and the uncertain band stays uncertain rather than being
 * rounded to "safe".
 */
class PrivacyCheck {

    static final double PRIVATE = 0.75
    static final double CLEAR = 0.40

    static final String UNSAFE = 'unsafe'
    static final String REVIEW = 'review'
    static final String SAFE = 'safe'

    /** check name -> the proposition Jev is asked. */
    static final Map<String, String> CHECKS = [
            has_uuid      : 'Does `shot.image` show a UUID or a long hexadecimal identifier?',
            has_hostname  : 'Does `shot.image` show a machine hostname or an internal host name?',
            has_person    : 'Does `shot.image` show a username, email address, or a person\'s full name?',
            has_private_path: 'Does `shot.image` show a filesystem path from a private machine, ' +
                    'such as a home directory?',
            has_secret    : 'Does `shot.image` show an API key, token, password, or other secret?'
    ] as LinkedHashMap

    static class Result {
        Map<String, Double> probabilities = [:]
        String worstCheck
        double worst
        String status

        boolean isPublishable() { return status == SAFE }

        String summary() {
            String number = String.format('%.2f', worst)
            String named = worstCheck?.replace('has_', '')?.replace('_', ' ')
            switch (status) {
                case UNSAFE:
                    return "Likely visible: ${named} (probability ${number}). Crop or regenerate before publishing."
                case REVIEW:
                    return "Unclear whether ${named} is visible (probability ${number}). " +
                            'Check the image by eye before publishing.'
                default:
                    return "Nothing private is likely visible (highest signal ${number})."
            }
        }
    }

    /** Ask Jev about one image and classify the answers. */
    static Result check(File image, String apiKey = null) {
        String key = apiKey != null ? apiKey.trim() : Keys.jev()
        if (!key) {
            throw new IllegalStateException(
                    'This needs Jev. Set JEV_API_KEY in the environment, then try again.')
        }
        def state = [shot: [caption: 'A preview image from a mind-map tool', image: 'image']]
        Map<String, Map> questions = CHECKS.collectEntries { String name, String proposition ->
            [(name): [type: 'noul', instructions: proposition]]
        }
        List<Judgement.Answer> answers = Judgement.ask(state, questions, key,
                Judgement.imageAsset(image))
        return classify(answers)
    }

    /** Pure classification, so the policy is testable without a network. */
    static Result classify(List<Judgement.Answer> answers) {
        Map<String, Double> probabilities = [:]
        answers.each { answer ->
            if (answer.type == 'noul' && CHECKS.containsKey(answer.question)) {
                probabilities[answer.question] = answer.probability ?: 0d
            }
        }
        if (probabilities.isEmpty()) {
            throw new IllegalStateException('Jev returned no answer about private material.')
        }
        String worstCheck = probabilities.max { it.value }.key
        double worst = probabilities[worstCheck]
        String status = worst >= PRIVATE ? UNSAFE : worst >= CLEAR ? REVIEW : SAFE
        return new Result(probabilities: probabilities, worstCheck: worstCheck,
                worst: worst, status: status)
    }
}