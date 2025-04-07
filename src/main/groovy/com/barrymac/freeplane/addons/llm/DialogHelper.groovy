package com.barrymac.freeplane.addons.llm

import groovy.swing.SwingBuilder
import groovy.util.logging.Slf4j

import javax.swing.*
import java.awt.*

/**
 * Helper class for creating and managing UI dialogs
 */
@Slf4j
class DialogHelper {
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
    static String showComparisonDialog(ui, config, contextNode, String message,
                                       java.util.List<String> defaultTypes,
                                       String configKey = "promptLlmAddOn.comparisonTypes") {
        try {
            // Load previously saved custom types
            def savedTypesString = config.getProperty(configKey, '')
            def customTypes = savedTypesString ? savedTypesString.split('\\|').toList() : []
            customTypes = customTypes.findAll { !it.trim().isEmpty() } // Remove empty entries

            // Combine defaults and custom, ensuring defaults come first and no duplicates
            def allTypes = (defaultTypes + customTypes).unique()
            log.debug("Showing comparison dialog with {} types ({} defaults, {} custom)", 
                     allTypes.size(), defaultTypes.size(), customTypes.size())

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
                                    log.info("Added new comparison type: {}", selectedType)
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
                log.info("User selected comparison type: {}", selectedType)
            } else {
                log.info("User cancelled comparison type selection")
            }

            // Return the selected type (or null if cancelled)
            return selectedType
        } catch (Exception e) {
            log.error("Error showing comparison dialog", e)
            ui.errorMessage("Error showing dialog: ${e.message}")
            return null
        }
    }

    /**
     * Creates a progress dialog for long-running operations
     *
     * @param ui The Freeplane UI object
     * @param title The dialog title
     * @param message The message to display
     * @return The created dialog (not yet visible)
     */
    static JDialog createProgressDialog(ui, String title, String message) {
        try {
            log.debug("Creating progress dialog: {}", title)
            def swingBuilder = new SwingBuilder()
            def dialog = swingBuilder.dialog(
                    title: title,
                    owner: ui.currentFrame,
                    modal: false, // Non-modal
                    resizable: true, // Allow resizing for longer messages
                    defaultCloseOperation: WindowConstants.DO_NOTHING_ON_CLOSE) { // Prevent manual closing
                swingBuilder.panel(layout: new BorderLayout(10, 10), border: BorderFactory.createEmptyBorder(10, 10, 10, 10)) {
                    // Use text area instead of label for multi-line support
                    scrollPane(constraints: BorderLayout.CENTER) {
                        textArea(
                                text: message,
                                lineWrap: true,
                                wrapStyleWord: true,
                                editable: false,
                                margin: new Insets(5, 5, 5, 5),
                                font: new Font(Font.SANS_SERIF, Font.PLAIN, 12)
                        )
                    }
                }
            }
            dialog.pack()
            // Set minimum size to prevent overly narrow dialogs
            dialog.minimumSize = new Dimension(300, 150)
            ui.setDialogLocationRelativeTo(dialog, ui.currentFrame) // Center on frame

            return dialog
        } catch (Exception e) {
            log.error("Error creating progress dialog", e)
            // Return a minimal dialog in case of error
            return new JDialog(ui.currentFrame, title, false)
        }
    }
}
