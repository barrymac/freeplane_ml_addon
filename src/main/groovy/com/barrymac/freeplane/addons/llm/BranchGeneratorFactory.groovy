package com.barrymac.freeplane.addons.llm

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

import javax.swing.*
import java.awt.*

@Slf4j
class BranchGeneratorFactory {
    static def createGenerateBranches(Map closures, Map deps) {
        return { apiKey, systemMessage, userMessage, model, maxTokens, temperature, provider ->
            def c = closures.c
            def ui = closures.ui
            def SwingUtilities = SwingUtilities

            // Get functions/classes from deps map
            def make_api_call = deps.apiCaller.make_api_call
            def addModelTagRecursively = deps.nodeTagger // Get method reference directly
            def DialogHelper = deps.dialogHelper

            try {
                log.info("Starting branch generation with model: {}", model)

                // Validate API key
                if (apiKey.isEmpty()) {
                    if (provider == 'openrouter') {
                        Desktop.desktop.browse(new URI("https://openrouter.ai/keys"))
                    } else {
                        Desktop.desktop.browse(new URI("https://platform.openai.com/account/api-keys"))
                    }
                    ui.errorMessage("Invalid authentication or incorrect API key provided.")
                    return
                }

                def node = c.selected

                // Create progress dialog
                def dialog = DialogHelper.createProgressDialog(
                        ui,
                        'I am asking your question. Wait for the response.',
                        userMessage
                )
                ui.setDialogLocationRelativeTo(dialog, node.delegate)
                dialog.setVisible(true)
                log.info("User message: {}", userMessage)

                // Run API call in background thread
                def workerThread = new Thread({
                    try {
                        def payloadMap = [
                                'model'      : model,
                                'messages'   : [
                                        [role: 'system', content: systemMessage],
                                        [role: 'user', content: userMessage]
                                ],
                                'temperature': temperature,
                                'max_tokens' : maxTokens
                        ]

                        // Use the unified API call function
                        def responseText = make_api_call(provider, apiKey, payloadMap)

                        if (responseText.isEmpty()) {
                            return
                        }

                        def jsonSlurper = new JsonSlurper()
                        def jsonResponse = jsonSlurper.parseText(responseText)
                        def response = jsonResponse.choices[0].message.content

                        log.info("LLM response received, length: {}", response?.length() ?: 0)
                        SwingUtilities.invokeLater {
                            dialog.dispose()
                            // Get the set of children *before* adding
                            def childrenBeforeSet = node.children.toSet()
                            node.appendTextOutlineAsBranch(response) // Add the branch
                            // Get the set of children *after* adding
                            def childrenAfterSet = node.children.toSet()

                            // Find the newly added nodes (difference between the sets)
                            def newlyAddedNodes = childrenAfterSet - childrenBeforeSet

                            if (!newlyAddedNodes.isEmpty()) {
                                // Recursively add the tag
                                newlyAddedNodes.each { newNode -> addModelTagRecursively(newNode, model) }
                            }
                            // Add logging to confirm tagging for Quick Prompt
                            log.info("BranchGenerator: Tag 'LLM:{}' applied to {} newly added top-level node(s).", 
                                     model.replace('/', '_'), newlyAddedNodes.size())
                        }
                    } catch (Exception e) {
                        log.warn("API call failed", e)
                        SwingUtilities.invokeLater {
                            dialog.dispose()
                            ui.errorMessage("API Error: ${e.message}")
                        }
                    }
                })
                // Use the classloader of the factory class itself
                workerThread.setContextClassLoader(BranchGeneratorFactory.class.classLoader)
                workerThread.start()
            } catch (Exception e) {
                log.error("Error in BranchGenerator setup", e)
                ui.errorMessage("Setup Error: ${e.message}")
            }
        }
    }
}
