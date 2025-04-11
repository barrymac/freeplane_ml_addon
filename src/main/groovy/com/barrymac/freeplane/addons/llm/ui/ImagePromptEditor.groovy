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

    /**
     * Shows the image prompt editor dialog.
     * Returns a Map indicating the user's action and necessary data.
     * Possible actions: 'Generate', 'Save', 'Reset', 'Cancel', 'Error'.
     */
    static Map showPromptEditor(ui, String initialPrompt, Map initialParams, def config) {
        Map resultMap = [action: 'Cancel'] // Default action if closed unexpectedly
        Map params = initialParams.clone()
        def dialog
        def swingBuilder = new SwingBuilder()
        def promptArea
        def generateButton // Keep reference for Ctrl+Enter
        def stepsField, widthField, heightField, imageNumField // Declare fields for validation access
        JLabel headerLabel // Declare header label for updating

        // Check if we're using a saved template (for display purposes only here)
        def savedTemplate = ConfigManager.getUserProperty(config, 'savedImagePromptTemplate', '')
        boolean isUsingSavedTemplate = savedTemplate && !savedTemplate.trim().isEmpty()

        try {
            swingBuilder.edt { // Ensure GUI runs on Event Dispatch Thread
                dialog = swingBuilder.dialog(
                    title: 'Edit Image Generation Parameters',
                    modal: true,
                    owner: ui.currentFrame,
                    defaultCloseOperation: WindowConstants.DISPOSE_ON_CLOSE
                ) {
                    swingBuilder.borderLayout() // Main dialog uses BorderLayout

                    swingBuilder.panel(constraints: BorderLayout.CENTER, layout: new BorderLayout()) { // Outer panel with BorderLayout

                        // 1. Header - Place in NORTH
                        headerLabel = swingBuilder.label( // Assign to variable
                            constraints: BorderLayout.NORTH,
                            text: '<html><b style="font-size:14px">Edit Image Generation Prompt</b><br>'
                                + '<small style="font-size:11px">Template source: '
                                + (isUsingSavedTemplate ? 'User-saved' : 'System default / Generated')
                                + '</small></html>',
                            border: BorderFactory.createEmptyBorder(5, 5, 5, 5)
                        )

                        // Inner panel for GridBagLayout content
                        swingBuilder.panel(constraints: BorderLayout.CENTER) {
                            swingBuilder.layout = new GridBagLayout()
                            def gbc = new GridBagConstraints(
                                fill: GridBagConstraints.BOTH,
                                anchor: GridBagConstraints.NORTHWEST,
                                insets: [2, 5, 2, 5]
                            )

                            // 2. Text Area
                            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0; gbc.weighty = 1.0;
                            swingBuilder.scrollPane(constraints: gbc) {
                                promptArea = swingBuilder.textArea(
                                    rows: 15, columns: 80, text: initialPrompt,
                                    lineWrap: true, wrapStyleWord: true,
                                    font: new Font(Font.MONOSPACED, Font.PLAIN, 12)
                                )
                            }

                            // 3. Variables Panel
                            gbc.gridy++; gbc.weighty = 0.0; gbc.ipady = 0
                            swingBuilder.scrollPane(constraints: gbc) {
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
                                    swingBuilder.label(text: '$lighting'); swingBuilder.label(text: 'Lighting (e.g. dramatic)')
                                }
                            }

                            // 4. Parameters Panel
                            gbc.gridy++; gbc.weighty = 0.0;
                            swingBuilder.panel(constraints: gbc, border: BorderFactory.createTitledBorder("Generation Parameters")) {
                                swingBuilder.gridLayout(rows: 4, columns: 2, hgap: 10, vgap: 5)
                                swingBuilder.label(text: 'Steps (4-50):')
                                stepsField = swingBuilder.textField(text: params.steps.toString(), id: 'stepsField')
                                swingBuilder.label(text: 'Width (256-1024):')
                                widthField = swingBuilder.textField(text: params.width.toString(), id: 'widthField')
                                swingBuilder.label(text: 'Height (256-1024):')
                                heightField = swingBuilder.textField(text: params.height.toString(), id: 'heightField')
                                swingBuilder.label(text: 'Number of Images (1-4):')
                                imageNumField = swingBuilder.textField(text: params.imageNum.toString(), id: 'imageNumField')
                            }
                        } // End inner GridBagLayout panel
                    } // End outer BorderLayout panel

                    // Button Panel
                    swingBuilder.panel(constraints: BorderLayout.SOUTH, layout: new FlowLayout(FlowLayout.RIGHT)) {
                        // --- Generate Button ---
                        generateButton = swingBuilder.button(text: 'Generate', actionPerformed: {
                            try {
                                params.steps = validateNumberField(stepsField, 4, 50, "Steps")
                                params.width = validateNumberField(widthField, 256, 1024, "Width")
                                params.height = validateNumberField(heightField, 256, 1024, "Height")
                                params.imageNum = validateNumberField(imageNumField, 1, 4, "Image Count")

                                def currentPrompt = promptArea.text.trim()
                                if (!currentPrompt) {
                                    showError(dialog, "Prompt cannot be empty")
                                    return // Stay in dialog
                                }
                                resultMap = [action: 'Generate', prompt: currentPrompt, params: params]
                                dialog.dispose()
                            } catch (IllegalArgumentException e) {
                                showError(dialog, e.message) // Stay in dialog
                            }
                        })

                        // --- Save Template Button ---
                        swingBuilder.button(text: 'Save Template', actionPerformed: {
                            def templateToSave = promptArea.text.trim()
                            if (!templateToSave) {
                                showError(dialog, "Cannot save empty template")
                                return // Stay in dialog
                            }
                            resultMap = [action: 'Save', prompt: templateToSave]
                            // Confirmation will be shown by the caller
                            // Update header label immediately
                            headerLabel.text = '<html><b style="font-size:14px">Edit Image Generation Prompt</b><br>' +
                                        '<small style="font-size:11px">Template source: User-saved</small></html>'
                            dialog.dispose() // Close dialog
                        })

                        // --- Reset to Default Button ---
                        swingBuilder.button(text: 'Reset to Default', actionPerformed: {
                            int confirm = JOptionPane.showConfirmDialog(dialog,
                                "Reset prompt to default template?\nThis will prepare the action but NOT save automatically.",
                                "Confirm Reset",
                                JOptionPane.YES_NO_OPTION)

                            if (confirm == JOptionPane.YES_OPTION) {
                                resultMap = [action: 'Reset']
                                // Update UI immediately to show the default (caller will handle saving)
                                try {
                                    def defaultTemplate = ResourceLoaderService.loadTextResource('/imageUserPrompt.txt')
                                    promptArea.text = defaultTemplate
                                    headerLabel.text = '<html><b style="font-size:14px">Edit Image Generation Prompt</b><br>' +
                                            '<small style="font-size:11px">Template source: System default / Generated</small></html>'
                                } catch (Exception e) {
                                    LogUtils.warn("Could not load default template for preview on reset: ${e.message}")
                                }
                                dialog.dispose() // Close dialog
                            }
                        })

                        // --- Cancel Button ---
                        swingBuilder.button(text: 'Cancel', actionPerformed: {
                            resultMap = [action: 'Cancel']
                            dialog.dispose()
                        })
                    }
                } // End dialog definition

                // Add keyboard bindings AFTER dialog is fully constructed
                dialog.rootPane.with {
                    getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelAction")
                    getActionMap().put("cancelAction", new AbstractAction() {
                        void actionPerformed(ActionEvent e) {
                            resultMap = [action: 'Cancel']
                            dialog.dispose()
                        }
                    })
                }
                promptArea.with {
                    getInputMap().put(KeyStroke.getKeyStroke("ctrl ENTER"), "generateAction")
                    getActionMap().put("generateAction", new AbstractAction() {
                        void actionPerformed(ActionEvent e) {
                            generateButton.doClick()
                        }
                    })
                }

                dialog.rootPane.defaultButton = generateButton
                dialog.preferredSize = new Dimension(1000, 800) // Keep preferred size
                dialog.pack()
                dialog.setLocationRelativeTo(ui.currentFrame)
                dialog.visible = true // Show the modal dialog
            } // End edt block
        } catch (Exception e) {
            LogUtils.severe("Error showing image prompt editor dialog: ${e.message}", e)
            return [action: 'Error', message: "Dialog creation failed: ${e.message.split('\n').head()}"]
        }

        LogUtils.info("ImagePromptEditor returning result: ${resultMap.action}")
        return resultMap
    }

    private static int validateNumberField(textField, min, max, fieldName) {
        try {
            def value = textField.text.toInteger()
            if (value < min || value > max) {
                throw new IllegalArgumentException("${fieldName} must be between ${min}-${max}")
            }
            return value
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number for ${fieldName}: '${textField.text}'")
        }
    }

    private static void showError(dialog, message) {
        JOptionPane.showMessageDialog(dialog, message, "Input Error", JOptionPane.ERROR_MESSAGE)
    }
}
