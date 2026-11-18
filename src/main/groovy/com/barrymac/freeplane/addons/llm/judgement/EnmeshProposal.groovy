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

    /** Choice -> marker/family, for the relationships worth proposing. */
    static final Map<String, List<String>> RELATIONS = [
            depends_on: ['depends-on', MeshVocabulary.DEPENDENCY, 'depends on'],
            blocks    : ['blocks', MeshVocabulary.BLOCKING, 'blocks'],
            references: ['references', MeshVocabulary.REFERENCE, 'references'],
            tracks    : ['tracks', MeshVocabulary.TRACKING, 'tracks'],
            contains  : ['contains', MeshVocabulary.STRUCTURE, 'contains'],
            reconciles: ['reconciles-under-condition', MeshVocabulary.TRACKING,
                         'reconciles under a condition']
    ] as LinkedHashMap

    static Map<String, Map> battery(List<Map> candidates, String seedTitle) {
        Map state = [seed: [title: seedTitle]]
        Map questions = [:]
        candidates.eachWithIndex { Map candidate, int index ->
            state["c${index}".toString()] = [title: candidate.title, text: trim(candidate.text)]
            questions["r${index}".toString()] = [
                    type        : 'choice',
                    instructions: "Which relationship, if any, does `${"c${index}".toString()}` have to " +
                            '`seed`? Judge only what the text supports.',
                    criteria    : [
                            depends_on: 'The entry needs the other to work or to be true',
                            blocks    : 'The entry prevents or must precede the other',
                            references: 'The entry cites, depicts or documents the other',
                            tracks    : 'The entry is work tracked against the other',
                            contains  : 'One contains or is part of the other',
                            reconciles: 'They differ but hold under a condition',
                            none      : 'No relationship is supported',
                            cannot_tell: 'Not enough information to judge'
                    ]
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
            edges << new MeshEdge(
                    source: seedId,
                    target: candidate.id,
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