package com.barrymac.freeplane.addons.llm

import groovy.transform.Canonical

/**
 * Container for all dependencies used by the add-on
 */
@Canonical
class Dependencies {
    def apiCaller
    def branchGeneratorFactory
    def messageExpander
    def messageFileHandler
    def nodeTagger
    def responseParser
    def dialogHelper
    def nodeHelperUtils
    def nodeOperations
    def configManager
    def messageLoader
    def comparisonService
    def validationHelper
    def uiHelper
    def apiPayloadBuilder
    def responseProcessor
}
