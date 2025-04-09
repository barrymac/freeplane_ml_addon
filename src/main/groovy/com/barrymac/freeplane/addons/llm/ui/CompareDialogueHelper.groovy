package com.barrymac.freeplane.addons.llm.ui

import groovy.swing.SwingBuilder
import org.freeplane.core.ui.components.UITools
import org.freeplane.core.util.LogUtils
import org.freeplane.plugin.script.FreeplaneScriptBaseClass

import javax.swing.BorderFactory
import javax.swing.SwingUtilities
import java.awt.BorderLayout
import java.awt.FlowLayout

// Keep for potential internal UI errors if needed

// Remove - No longer used here

// Remove - No longer used here

// Remove - No longer used here

// Remove - No longer used here

// Keep for potential internal UI messages if needed

// Keep for ui.currentFrame, setDialogLocationRelativeTo

// Remove - No longer used here

// Remove - No longer used here

class CompareDialogueHelper {

    /**
     * Shows a dialog for selecting or entering a comparison type
     *
     * @param ui The Freeplane UI object
     * @param config The Freeplane config object
     * @param contextNode The node model for context
     * @param message The message to display
     * @param defaultTypes Default comparison type options
     * @param configKey The config key to store custom types
     * @return The selected comparison type or null if cancelled
     */
    static String showComparisonDialog(UITools ui, FreeplaneScriptBaseClass.ConfigProperties config, contextNode, String message,
                                       List<String> defaultTypes,
                                       String configKey = "promptLlmAddOn.comparisonTypes") {
        try {
            // Load previously saved custom types
            def savedTypesString = config.getProperty(configKey, '')
            def customTypes = savedTypesString ? savedTypesString.split('\\|').toList() : []
            customTypes = customTypes.findAll { !it.trim().isEmpty() } // Remove empty entries

            // Combine defaults and custom, ensuring defaults come first and no duplicates
            def allTypes = (defaultTypes + customTypes).unique()
            LogUtils.info("Showing comparison dialog with ${allTypes.size()} types (${defaultTypes.size()} defaults, ${customTypes.size()} custom)")

            def selectedType = null // Variable to store the result
            def swing = new SwingBuilder()

            // Build the dialog
            def dialog = swing.dialog(
                    title: "Define Comparison Type",
                    modal: true, // Make it modal
                    owner: ui.currentFrame, // Parent frame
                    pack: true, // Size based on content
                    locationRelativeTo: ui.currentFrame // Center on frame
            ) {
                panel(layout: new BorderLayout(5, 5), border: BorderFactory.createEmptyBorder(10, 10, 10, 10)) {
                    // Message Label
                    label(text: "<html>${message.replaceAll('\n', '<br>')}</html>", constraints: BorderLayout.NORTH)
                    // Use HTML for multi-line

                    // Editable ComboBox
                    comboBox(id: 'typeCombo',
                            items: allTypes,
                            editable: true,
                            selectedItem: allTypes.isEmpty() ? "" : allTypes[0], // Select first item or empty
                            constraints: BorderLayout.CENTER)

                    // Button Panel
                    panel(layout: new FlowLayout(FlowLayout.RIGHT), constraints: BorderLayout.SOUTH) {
                        button(text: 'OK', defaultButton: true, actionPerformed: {
                            // Get selected/entered item
                            selectedType = typeCombo.editor.item?.toString()?.trim() ?: ""
                            if (!selectedType.isEmpty()) {
                                // Check if it's a new custom type
                                if (!defaultTypes.contains(selectedType) && !customTypes.contains(selectedType)) {
                                    customTypes.add(selectedType)
                                    // Save updated custom types list
                                    config.setProperty(configKey, customTypes.join('|'))
                                    LogUtils.info("Added new comparison type: ${selectedType}")
                                }
                            }
                            // Get the button source, find its window (the dialog), and dispose it
                            SwingUtilities.getWindowAncestor(it.source).dispose()
                        })
                        button(text: 'Cancel', actionPerformed: {
                            selectedType = null // Indicate cancellation
                            // Get the button source, find its window (the dialog), and dispose it
                            SwingUtilities.getWindowAncestor(it.source).dispose()
                        })
                    }
                }
            }

            // Show the dialog (it's modal, so execution waits here)
            dialog.visible = true

            if (selectedType) {
                LogUtils.info("User selected comparison type: ${selectedType}")
            } else {
                LogUtils.info("User cancelled comparison type selection")
            }

            // Return the selected type (or null if cancelled)
            return selectedType
        } catch (Exception e) {
            LogUtils.severe("Error showing comparison dialog: ${e.message}")
            ui.errorMessage("Error showing dialog: ${e.message}")
            return null
        }
    }

}
