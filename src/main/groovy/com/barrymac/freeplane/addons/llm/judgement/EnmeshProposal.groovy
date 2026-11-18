package com.barrymac.freeplane.addons.llm.judgement

import com.barrymac.freeplane.addons.llm.neuros.MeshEdge
import com.barrymac.freeplane.addons.llm.neuros.MeshVocabulary

/**
 * Proposes relationships that are not written down yet: an enmeshment mission.
 *
 * Each candidate is asked one question — which relationship, if any, holds
 * between it and the selected entry. Proposals are deliberately provisional:
 * they are drawn dashed and grey, and never written to the source material.
 */
class EnmeshProposal {

    static final int MAX_CANDIDATES = 8
    static final int MAX_CHARS = 700
    static final double DECISIVE = 0.75

    /**
     * Choice -> [marker, family, label, direction]. `REVERSE` means the candidate
     * is the source and the selected entry the target.
     */
    static final String FORWARD = 'forward'
    static final String REVERSE = 'reverse'

    static final Map<String, List<String>> RELATIONS = [
            selected_depends_on_candidate: ['depends-on', MeshVocabulary.DEPENDENCY, 'depends on', FORWARD],
            candidate_depends_on_selected: ['depends-on', MeshVocabulary.DEPENDENCY, 'depends on', REVERSE],
            selected_blocks_candidate   : ['blocks', MeshVocabulary.BLOCKING, 'blocks', FORWARD],
            candidate_blocks_selected   : ['blocks', MeshVocabulary.BLOCKING, 'blocks', REVERSE],
            references                  : ['references', MeshVocabulary.REFERENCE, 'references', FORWARD],
            tracks                      : ['tracks', MeshVocabulary.TRACKING, 'tracks', FORWARD],
            contains                    : ['contains', MeshVocabulary.STRUCTURE, 'contains', FORWARD]
    ] as LinkedHashMap

    /**
     * Direction is part of the answer, not an afterthought: "A depends on B" and
     * "B depends on A" are different edges, and a question that cannot say which
     * leaves the model with `none` as its only honest option.
     */
    static final Map<String, String> CRITERIA = [
            selected_depends_on_candidate:
                    'The selected card cannot be completed or be true unless the candidate is ' +
                            'done or true first',
            candidate_depends_on_selected:
                    'The candidate cannot be completed or be true unless the selected card is ' +
                            'done or true first',
            selected_blocks_candidate:
                    'The selected card prevents the candidate, or must happen before it',
            candidate_blocks_selected:
                    'The candidate prevents the selected card, or must happen before it',
            references:
                    'Either text names a file, class, diagram or artefact that the other describes ' +
                            'or produces',
            tracks:
                    'One is work tracked against the other outcome, such as an implementation card ' +
                            'against a design card',
            contains:
                    'One is a part of the other scope, or the other is a part of it',
            none:
                    'The two texts describe unrelated work: nothing in one is needed, named or ' +
                            'contained by the other',
            cannot_tell:
                    'One or both texts are too thin, or too ambiguous, to judge'
    ] as LinkedHashMap

    /**
     * Stage one: should an edge exist at all? One probability per candidate, so
     * code can rank and take the strongest few instead of spending a naming
     * question on entries that deserve nothing. `none` is what a low probability
     * means here; nothing is forced.
     */
    static Map shortlistBattery(List<Map> candidates, String seedTitle) {
        Map state = [selected: [title: seedTitle]]
        Map questions = [:]
        candidates.eachWithIndex { Map candidate, int index ->
            String key = "c${index}".toString()
            state[key] = [title: candidate.title ?: key, text: trim(candidate.text)]
            questions["w${index}".toString()] = [
                    type        : 'noul',
                    instructions: "The selected card is \"${seedTitle}\"; the other entry is " +
                            "\"${candidate.title}\", shown as `${key}`. Both are work cards from one " +
                            'project. Would a reader be right to record a relationship from the ' +
                            'selected card to this entry — because one is needed before the other, ' +
                            'one names an artefact the other produces, one tracks the other, or one ' +
                            'is part of the other? Judge only what the two texts support, not what ' +
                            'would be sensible in general.'
            ]
        }
        return [state: state, questions: questions]
    }

    /** Candidate indexes worth naming, strongest first, bounded. */
    static List<Integer> shortlisted(List<Judgement.Answer> answers, int candidates,
                                     int top = 10, double floor = 0.5) {
        Map<String, Judgement.Answer> byQuestion = [:]
        answers.each { byQuestion[it.question] = it }
        return (0..<candidates)
                .collect { index -> [index: index, p: byQuestion["w${index}".toString()]?.probability ?: 0d] }
                .findAll { it.p >= floor }
                .sort { -it.p }
                .take(Math.max(top, 1))
                .collect { it.index as Integer }
    }

    static Map<String, Map> battery(List<Map> candidates, String seedTitle) {
        Map state = [selected: [title: seedTitle]]
        Map questions = [:]
        candidates.eachWithIndex { Map candidate, int index ->
            String key = "c${index}".toString()
            String title = candidate.title ?: key
            state[key] = [title: title, text: trim(candidate.text)]
            questions["r${index}".toString()] = [
                    type        : 'choice',
                    instructions: "The selected card is \"${seedTitle}\". The other entry is " +
                            "\"${title}\", shown as `${key}`. These are work cards from one project. " +
                            'Which single relationship between them is supported by what the two ' +
                            'texts describe? Answer `none` when the texts do not support one, and ' +
                            '`cannot_tell` when either text is too thin to judge. Do not infer a ' +
                            'relationship from what would be sensible in general.',
                    criteria    : CRITERIA
            ]
        }
        return [state: state, questions: questions]
    }

    /** Turn answers into provisional edges; nothing below threshold is proposed. */
    static List<MeshEdge> proposals(List<Map> candidates, String seedId,
                                    List<Judgement.Answer> answers) {
        Map<String, Judgement.Answer> byQuestion = [:]
        answers.each { byQuestion[it.question] = it }
        List<MeshEdge> edges = []
        candidates.eachWithIndex { Map candidate, int index ->
            def answer = byQuestion["r${index}".toString()]
            if (answer == null || answer.escape || !answer.isDecisive(DECISIVE)) return
            List<String> relation = RELATIONS[answer.choice]
            if (relation == null) return
            boolean reversed = relation[3] == REVERSE
            edges << new MeshEdge(
                    source: reversed ? candidate.id : seedId,
                    target: reversed ? seedId : candidate.id,
                    marker: relation[0],
                    label: "proposed: ${relation[2]}".toString(),
                    family: relation[1],
                    declared: MeshVocabulary.isDeclared(relation[0]),
                    proposed: true)
        }
        return edges
    }

    static String trim(String text) {
        String clean = (text ?: '').replaceAll(/\s+/, ' ').trim()
        return clean.length() <= MAX_CHARS ? clean : clean.substring(0, MAX_CHARS) + '…'
    }
}