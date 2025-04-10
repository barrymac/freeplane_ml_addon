package com.barrymac.freeplane.addons.llm.ui

import com.barrymac.freeplane.addons.llm.ConfigManager
import com.barrymac.freeplane.addons.llm.services.ResourceLoaderService
import groovy.swing.SwingBuilder
import org.freeplane.core.util.LogUtils
import javax.swing.*
import java.awt.*
import javax.swing.KeyStroke
import java.awt.event.KeyEvent
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent

class ImagePromptEditor {
    static def showPromptEditor(ui, String initialPrompt, Map initialParams, def config) {
        def modifiedPrompt = null
        def params = initialParams.clone()
        // Declare dialog variable outside the closure
        // Declare dialog and builder variables
        def dialog
        def swingBuilder = new SwingBuilder()
        def promptArea
        def generateButton
        def stepsField, widthField, heightField, imageNumField // Declare fields for validation access

        // Check if we're using a saved template
        def savedTemplate = ConfigManager.getUserProperty(config, 'savedImagePromptTemplate', '')
        boolean isUsingSavedTemplate = savedTemplate && !savedTemplate.trim().isEmpty() // Check if it exists and is not empty

        dialog = swingBuilder.dialog(
            title: 'Edit Image Generation Parameters',
            modal: true,
            owner: ui.currentFrame,
            defaultCloseOperation: WindowConstants.DISPOSE_ON_CLOSE
        ) {
            swingBuilder.borderLayout()
            swingBuilder.panel(constraints: BorderLayout.CENTER) {
                swingBuilder.layout = new GridBagLayout() // Set layout for THIS panel
                def gbc = new GridBagConstraints(
                    fill: GridBagConstraints.BOTH,
                    anchor: GridBagConstraints.NORTHWEST,
                    insets: [2, 5, 2, 5] // Top, left, bottom, right padding
                )

                // 1. Header - Use constraints: argument
                gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0; gbc.weighty = 0.0
                // --- FIX START ---
                swingBuilder.label(
                    constraints: gbc, // Pass constraints directly
                    text: '<html><b style="font-size:14px">Edit Image Generation Prompt</b><br>'
                        + '<small style="font-size:11px">Template source: '
                        + (isUsingSavedTemplate ? 'User-saved' : 'System default / Generated') // Updated text
                        + '</small></html>',
                    border: BorderFactory.createEmptyBorder(2, 5, 2, 5)
                )
                // --- FIX END ---

                // 2. Text Area - Use constraints: argument
                gbc.gridy++; gbc.weighty = 1.0; gbc.ipady = 10
                // --- FIX START ---
                swingBuilder.scrollPane(constraints: gbc) { // Pass constraints directly
                    promptArea = swingBuilder.textArea(
                        text: initialPrompt,
                        lineWrap: true,
                        wrapStyleWord: true,
                        font: new Font(Font.MONOSPACED, Font.PLAIN, 12)
                    )
                }
                // --- FIX END ---

                // 3. Variables Panel - Use constraints: argument
                gbc.gridy++; gbc.weighty = 0.0; gbc.ipady = 0
                // --- FIX START ---
                swingBuilder.scrollPane(constraints: gbc) { // Pass constraints directly
                    swingBuilder.panel(border: BorderFactory.createTitledBorder("Available Variables")) {
                        swingBuilder.gridLayout(rows: 8, columns: 2, hgap: 10, vgap: 5)
                        swingBuilder.label(text: '$generatedPrompt'); swingBuilder.label(text: 'AI-generated base prompt')
                        swingBuilder.label(text: '$nodeContent'); swingBuilder.label(text: 'Current node text')
                        swingBuilder.label(text: '$ancestorContents'); swingBuilder.label(text: 'All ancestor texts')
                        swingBuilder.label(text: '$siblingContents'); swingBuilder.label(text: 'Sibling node texts')
                        swingBuilder.label(text: '$rootText'); swingBuilder.label(text: 'Root node text')
                        swingBuilder.label(text: '$style'); swingBuilder.label(text: 'Art style (e.g. digital art)')
                        swingBuilder.label(text: '$details'); swingBuilder.label(text: 'Detail level (e.g. high)')
                        swingBuilder.label(text: '$colors'); swingBuilder.label(text: 'Color scheme')
                        swingBuilder.label(text: '$lighting'); swingBuilder.label(text: 'Lighting (e.g. dramatic)') // Added example
                    }
                }
                // --- FIX END ---

                // 4. Parameters Panel - Use constraints: argument
                gbc.gridy++
                // --- FIX START ---
                swingBuilder.panel(constraints: gbc, border: BorderFactory.createTitledBorder("Generation Parameters")) { // Pass constraints directly
                    swingBuilder.gridLayout(rows: 4, columns: 2, hgap: 10, vgap: 5)
                    swingBuilder.label(text: 'Steps (4-50):')
                    stepsField = swingBuilder.textField(text: params.steps.toString(), id: 'stepsField') // Assign to variable
                    swingBuilder.label(text: 'Width (256-1024):')
                    widthField = swingBuilder.textField(text: params.width.toString(), id: 'widthField') // Assign to variable
                    swingBuilder.label(text: 'Height (256-1024):')
                    heightField = swingBuilder.textField(text: params.height.toString(), id: 'heightField') // Assign to variable
                    swingBuilder.label(text: 'Number of Images (1-4):') // Updated label text
                    imageNumField = swingBuilder.textField(text: params.imageNum.toString(), id: 'imageNumField') // Assign to variable
                }
                // --- FIX END ---
            } // End central panel

            swingBuilder.panel(constraints: BorderLayout.SOUTH) {
                generateButton = swingBuilder.button(text: 'Generate', actionPerformed: {
                    try {
                        // Validate numerical parameters using the assigned field variables
                        params.steps = validateNumberField(stepsField, 4, 50, "Steps")
                        params.width = validateNumberField(widthField, 256, 1024, "Width")
                        params.height = validateNumberField(heightField, 256, 1024, "Height")
                        params.imageNum = validateNumberField(imageNumField, 1, 4, "Image Count")

                        modifiedPrompt = promptArea.text.trim()
                        if(!modifiedPrompt) {
                            JOptionPane.showMessageDialog(dialog,
                                "Prompt cannot be empty",
                                "Input Error",
                                JOptionPane.ERROR_MESSAGE)
                            return
                        }
                        dialog.dispose()
                    } catch(IllegalArgumentException e) {
                        JOptionPane.showMessageDialog(dialog,
                            e.message,
                            "Validation Error",
                            JOptionPane.ERROR_MESSAGE)
                    }
                })
                swingBuilder.button(text: 'Save Template', actionPerformed: {
                    try {
                        def templateToSave = promptArea.text.trim()
                        if (!templateToSave) {
                            showError(dialog, "Cannot save empty template")
                            return
                        }
                        ConfigManager.setUserProperty(config, 'savedImagePromptTemplate', templateToSave)
                        JOptionPane.showMessageDialog(dialog,
                            "Template saved for future use",
                            "Template Saved",
                            JOptionPane.INFORMATION_MESSAGE)
                        // Update header label immediately after saving
                        dialog.getRootPane().findComponentAt(0,0).findComponentAt(0,0).text = '<html><b style="font-size:14px">Edit Image Generation Prompt</b><br>' +
                                '<small style="font-size:11px">Template source: User-saved</small></html>'
                    } catch (Exception e) {
                        LogUtils.severe("Error saving template: ${e.message}")
                        showError(dialog, "Failed to save template: ${e.message}")
                    }
                })
                swingBuilder.button(text: 'Reset to Default', actionPerformed: {
                    int confirm = JOptionPane.showConfirmDialog(dialog,
                        "Reset to default template? This will overwrite any saved template.",
                        "Confirm Reset",
                        JOptionPane.YES_NO_OPTION)

                    if (confirm == JOptionPane.YES_OPTION) {
                        try {
                            // Load default template from JAR resources
                            def defaultTemplate = ResourceLoaderService.loadTextResource('/imageUserPrompt.txt')

                            // Save the default template to config (effectively deleting the user one if it exists)
                            ConfigManager.setUserProperty(config, 'savedImagePromptTemplate', defaultTemplate)

                            // Update UI with default template
                            promptArea.text = defaultTemplate

                            JOptionPane.showMessageDialog(dialog,
                                "Successfully reset to default template!",
                                "Template Reset",
                                JOptionPane.INFORMATION_MESSAGE)
                            // Update header label immediately after resetting
                            dialog.getRootPane().findComponentAt(0,0).findComponentAt(0,0).text = '<html><b style="font-size:14px">Edit Image Generation Prompt</b><br>' +
                                    '<small style="font-size:11px">Template source: System default / Generated</small></html>'
                        } catch(Exception e) {
                            LogUtils.severe("Reset failed: ${e.message}")
                            showError(dialog, "Reset failed: ${e.message}")
                        }
                    }
                })
                swingBuilder.button(text: 'Cancel', actionPerformed: {
                    modifiedPrompt = null
                    dialog.dispose()
                })
            }
        }

        // Add keyboard bindings AFTER dialog is fully constructed
        dialog.rootPane.with {
            // Escape to close
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeAction")
            getActionMap().put("closeAction", new AbstractAction() {
                void actionPerformed(ActionEvent e) {
                    modifiedPrompt = null
                    dialog.dispose()
                }
            })
        }

        // Add Ctrl+Enter to textArea
        promptArea.with {
            getInputMap().put(KeyStroke.getKeyStroke("ctrl ENTER"), "generateAction")
            getActionMap().put("generateAction", new AbstractAction() {
                void actionPerformed(ActionEvent e) {
                    // Simulate Generate button click
                    generateButton.doClick()
                }
            })
        }

        dialog.preferredSize = new Dimension(1000, 800)
        dialog.pack()
        dialog.setLocationRelativeTo(ui.currentFrame)
        dialog.visible = true

        return modifiedPrompt ? [modifiedPrompt, params] : null
    }

    private static int validateNumberField(textField, min, max, fieldName) {
        try {
            def value = textField.text.toInteger()
            if(value < min || value > max) {
                throw new IllegalArgumentException("${fieldName} must be between ${min}-${max}")
            }
            return value
        } catch(NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number for ${fieldName}")
        }
    }

    private static void showError(dialog, message) {
        JOptionPane.showMessageDialog(dialog, message, "Invalid Input", JOptionPane.ERROR_MESSAGE)
    }
}
