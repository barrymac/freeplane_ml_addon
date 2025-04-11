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
// --- ADDED IMPORT ---
import static com.barrymac.freeplane.addons.llm.ui.UiHelper.showInformationMessage // For confirmation messages

class ImagePromptEditor {

    // --- ADDED CONSTANTS ---
    private static final String KEY_SAVED_TEMPLATE = 'savedImagePromptTemplate'
    private static final String KEY_STEPS = 'imagegen.steps'
    private static final String KEY_WIDTH = 'imagegen.width'
    private static final String KEY_HEIGHT = 'imagegen.height'
    private static final String KEY_IMG_NUM = 'imagegen.imageNum'

    private static final Map DEFAULT_PARAMS = [steps: 4, width: 256, height: 256, imageNum: 4]
    // --- END ADDED CONSTANTS ---

    /**
     * Shows the image prompt editor dialog.
     * Returns a Map indicating the user's action and necessary data.
     * Possible actions: 'Generate', 'Cancel', 'Error'.
     * 'Save' and 'Reset' are now handled internally without closing.
     */
    static Map showPromptEditor(ui, String initialPrompt, Map initialParamsIgnored, def config) {
        Map resultMap = [action: 'Cancel'] // Default action if closed unexpectedly
        def dialog
        def swingBuilder = new SwingBuilder()
        def promptArea
        def generateButton // Keep reference for Ctrl+Enter
        def stepsField, widthField, heightField, imageNumField // Declare fields for validation access
        JLabel headerLabel // Declare header label for updating

        // --- MODIFIED PARAMETER LOADING ---
        // Load saved parameters or use defaults
        Map params = [
            steps   : config.getProperty(KEY_STEPS, DEFAULT_PARAMS.steps.toString()).toInteger(),
            width   : config.getProperty(KEY_WIDTH, DEFAULT_PARAMS.width.toString()).toInteger(),
            height  : config.getProperty(KEY_HEIGHT, DEFAULT_PARAMS.height.toString()).toInteger(),
            imageNum: config.getProperty(KEY_IMG_NUM, DEFAULT_PARAMS.imageNum.toString()).toInteger()
        ]
        LogUtils.info("Loaded initial parameters: ${params}")
        // Seed is always random, not saved
        params.seed = new Random().nextInt(Integer.MAX_VALUE)
        // --- END MODIFIED PARAMETER LOADING ---

        try {
            swingBuilder.edt { // Ensure GUI runs on Event Dispatch Thread

                // --- MODIFIED HEADER LABEL LOGIC ---
                // Determine initial state for header
                def savedTemplate = ConfigManager.getUserProperty(config, KEY_SAVED_TEMPLATE, '')
                boolean usingSavedTemplate = savedTemplate && !savedTemplate.trim().isEmpty()
                boolean usingSavedParams = config.containsKey(KEY_STEPS) // Check if any param key exists

                String initialHeaderStatus
                if (usingSavedTemplate && usingSavedParams) {
                    initialHeaderStatus = 'User-saved Template & Parameters'
                } else if (usingSavedTemplate) {
                    initialHeaderStatus = 'User-saved Template / Default Parameters'
                } else if (usingSavedParams) {
                    initialHeaderStatus = 'System Template / User-saved Parameters'
                } else {
                    initialHeaderStatus = 'System Template & Default Parameters'
                }
                // --- END MODIFIED HEADER LABEL LOGIC ---

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
                            // --- UPDATED HEADER TEXT ---
                            text: "<html><b style=\"font-size:14px\">Edit Image Generation Prompt</b><br>" +
                                  "<small style=\"font-size:11px\">Source: ${initialHeaderStatus}</small></html>",
                            // --- END UPDATED HEADER TEXT ---
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
                                    rows: 15, columns: 80, text: initialPrompt, // Still uses initialPrompt passed in
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
                                // --- MODIFIED PARAMETER FIELDS ---
                                stepsField = swingBuilder.textField(text: params.steps.toString(), id: 'stepsField')
                                swingBuilder.label(text: 'Width (256-1024):')
                                widthField = swingBuilder.textField(text: params.width.toString(), id: 'widthField')
                                swingBuilder.label(text: 'Height (256-1024):')
                                heightField = swingBuilder.textField(text: params.height.toString(), id: 'heightField')
                                swingBuilder.label(text: 'Number of Images (1-4):')
                                imageNumField = swingBuilder.textField(text: params.imageNum.toString(), id: 'imageNumField')
                                // --- END MODIFIED PARAMETER FIELDS ---
                            }
                        } // End inner GridBagLayout panel
                    } // End outer BorderLayout panel

                    // Button Panel
                    swingBuilder.panel(constraints: BorderLayout.SOUTH, layout: new FlowLayout(FlowLayout.RIGHT)) {
                        // --- Generate Button ---
                        generateButton = swingBuilder.button(text: 'Generate', actionPerformed: {
                            try {
                                // Validate and update params map from fields before returning
                                params.steps = validateNumberField(stepsField, 4, 50, "Steps")
                                params.width = validateNumberField(widthField, 256, 1024, "Width")
                                params.height = validateNumberField(heightField, 256, 1024, "Height")
                                params.imageNum = validateNumberField(imageNumField, 1, 4, "Image Count")

                                def currentPrompt = promptArea.text.trim()
                                if (!currentPrompt) {
                                    showError(dialog, "Prompt cannot be empty")
                                    return // Stay in dialog
                                }
                                // Add the random seed just before generating
                                params.seed = new Random().nextInt(Integer.MAX_VALUE)
                                resultMap = [action: 'Generate', prompt: currentPrompt, params: params]
                                dialog.dispose()
                            } catch (IllegalArgumentException e) {
                                showError(dialog, e.message) // Stay in dialog
                            }
                        })

                        // --- MODIFIED Save Template Button ---
                        swingBuilder.button(text: 'Save Template', actionPerformed: {
                            try {
                                // 1. Validate parameters first
                                def currentSteps = validateNumberField(stepsField, 4, 50, "Steps")
                                def currentWidth = validateNumberField(widthField, 256, 1024, "Width")
                                def currentHeight = validateNumberField(heightField, 256, 1024, "Height")
                                def currentImageNum = validateNumberField(imageNumField, 1, 4, "Image Count")

                                // 2. Validate prompt
                                def templateToSave = promptArea.text.trim()
                                if (!templateToSave) {
                                    showError(dialog, "Cannot save empty template")
                                    return // Stay in dialog
                                }

                                // 3. Save template and parameters
                                ConfigManager.setUserProperty(config, KEY_SAVED_TEMPLATE, templateToSave)
                                config.setProperty(KEY_STEPS, currentSteps.toString())
                                config.setProperty(KEY_WIDTH, currentWidth.toString())
                                config.setProperty(KEY_HEIGHT, currentHeight.toString())
                                config.setProperty(KEY_IMG_NUM, currentImageNum.toString())
                                // No need to call config.save() - Freeplane handles it

                                LogUtils.info("Saved template and parameters.")

                                // 4. Update header
                                headerLabel.text = '<html><b style="font-size:14px">Edit Image Generation Prompt</b><br>' +
                                            '<small style="font-size:11px">Source: User-saved Template & Parameters</small></html>'

                                // --- REMOVED CONFIRMATION DIALOG ---

                                // --- ADDED UI FIELD UPDATE ---
                                // 6. Update UI fields with saved values
                                stepsField.text = currentSteps.toString()
                                widthField.text = currentWidth.toString()
                                heightField.text = currentHeight.toString()
                                imageNumField.text = currentImageNum.toString()
                                LogUtils.info("Updated UI fields after saving.")
                                // --- END ADDED UI FIELD UPDATE ---

                                // --- DO NOT DISPOSE DIALOG ---
                                // --- DO NOT SET resultMap ---
                            } catch (IllegalArgumentException e) {
                                showError(dialog, e.message) // Show validation error
                            } catch (Exception e) {
                                LogUtils.severe("Error saving template/params: ${e.message}", e)
                                showError(dialog, "Failed to save: ${e.message}")
                            }
                        })
                        // --- END MODIFIED Save Template Button ---

                        // --- MODIFIED Reset to Default Button ---
                        swingBuilder.button(text: 'Reset to Default', actionPerformed: {
                            int confirm = JOptionPane.showConfirmDialog(dialog,
                                "Reset prompt and parameters to default values?\nThis will save the defaults immediately.",
                                "Confirm Reset",
                                JOptionPane.YES_NO_OPTION)

                            if (confirm == JOptionPane.YES_OPTION) {
                                try {
                                    // 1. Load and save default template
                                    def defaultTemplate = ResourceLoaderService.loadTextResource('/imageUserPrompt.txt')
                                    ConfigManager.setUserProperty(config, KEY_SAVED_TEMPLATE, defaultTemplate)

                                    // 2. Save default parameters
                                    config.setProperty(KEY_STEPS, DEFAULT_PARAMS.steps.toString())
                                    config.setProperty(KEY_WIDTH, DEFAULT_PARAMS.width.toString())
                                    config.setProperty(KEY_HEIGHT, DEFAULT_PARAMS.height.toString())
                                    config.setProperty(KEY_IMG_NUM, DEFAULT_PARAMS.imageNum.toString())

                                    LogUtils.info("Reset template and parameters to default.")

                                    // 3. Update UI elements
                                    promptArea.text = defaultTemplate
                                    stepsField.text = DEFAULT_PARAMS.steps.toString()
                                    widthField.text = DEFAULT_PARAMS.width.toString()
                                    heightField.text = DEFAULT_PARAMS.height.toString()
                                    imageNumField.text = DEFAULT_PARAMS.imageNum.toString()

                                    // 4. Update header
                                    headerLabel.text = '<html><b style="font-size:14px">Edit Image Generation Prompt</b><br>' +
                                                '<small style="font-size:11px">Source: System Template & Default Parameters</small></html>'

                                    // --- REMOVED CONFIRMATION DIALOG ---

                                    // --- DO NOT DISPOSE DIALOG ---
                                    // --- DO NOT SET resultMap ---
                                } catch (Exception e) {
                                    LogUtils.severe("Reset failed: ${e.message}", e)
                                    showError(dialog, "Failed to reset: ${e.message}")
                                }
                            }
                        })
                        // --- END MODIFIED Reset to Default Button ---

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
                // --- MODIFIED DIALOG SIZE ---
                dialog.preferredSize = new Dimension(1000, 900) // Increased height
                // --- END MODIFIED DIALOG SIZE ---
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
