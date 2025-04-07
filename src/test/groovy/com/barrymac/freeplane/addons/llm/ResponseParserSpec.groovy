package com.barrymac.freeplane.addons.llm

import spock.lang.Specification
import spock.lang.Unroll // To get individual reports for each data row

/**
 * Spock test specification for the ResponseParser class.
 */
class ResponseParserSpec extends Specification {

    @Unroll // Report each iteration separately
    def "parseAnalysis should correctly parse #description into a map"() {
        given: "Input text from an LLM response"
        // inputText is provided by the where block

        when: "The parseAnalysis method is called"
        def resultMap = ResponseParser.parseAnalysis(inputText)

        then: "The resulting map should match the expected structure"
        resultMap == expectedMap

        where:
        description              | inputText                                                                      | expectedMap
        "standard categories"    | """
                                   Category 1:
                                   - Point A
                                   - Point B

                                   Category 2:
                                   * Point C
                                   """                                                                            | ["Category 1": ["Point A", "Point B"], "Category 2": ["Point C"]]
        "no bullet points"       | """
                                   Pros:
                                   Good performance
                                   Easy setup
                                   Cons:
                                   Expensive
                                   """                                                                            | ["Pros": ["Good performance", "Easy setup"], "Cons": ["Expensive"]]
        "mixed bullets/no bullets" | """
                                   Mixed Bag:
                                   - Bulleted item
                                   Just a line
                                   * Another bullet
                                   """                                                                            | ["Mixed Bag": ["Bulleted item", "Just a line", "Another bullet"]]
        "empty input"            | ""                                                                             | [:]
        "whitespace input"       | "   \n  \t "                                                                   | [:]
        "no categories"          | """
                                   Just a line of text.
                                   Another line.
                                   """                                                                            | ["Analysis": ["Just a line of text.", "Another line."]]
        "single category"        | """
                                   Findings:
                                   - Result X
                                   - Result Y
                                   """                                                                            | ["Findings": ["Result X", "Result Y"]]
        "category with no points"| """
                                   Category 1:
                                   Category 2:
                                   - Point D
                                   Empty Category:
                                   """                                                                            | ["Category 2": ["Point D"]] // Empty categories are filtered out
        "extra whitespace"       | """
                                     Category Alpha :
                                      -  Item 1
                                        * Item 2
                                   """                                                                            | ["Category Alpha": ["Item 1", "Item 2"]]
        "only category headers"  | """
                                   Header 1:
                                   Header 2:
                                   """                                                                            | [:] // No points, so empty map
        "complex points"         | """
                                   Analysis:
                                   - Point one; contains semicolon
                                   * Point two: contains colon
                                   """                                                                            | ["Analysis": ["Point one; contains semicolon", "Point two: contains colon"]]

    }
}
