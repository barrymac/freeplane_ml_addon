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

class PromptEditor {
    static def showPromptEditor(ui, String initialPrompt, Map initialParams, def config) {
        def modifiedPrompt = null
        def params = initialParams.clone()
        // Declare dialog variable outside the closure
        // Declare dialog and builder variables
        def dialog
        def swingBuilder = new SwingBuilder()
        def promptArea
        def generateButton
        
        // Check if we're using a saved template
        def savedTemplate = ConfigManager.getUserProperty(config, 'savedImagePromptTemplate', '')
        
        dialog = swingBuilder.dialog(
            title: 'Edit Image Generation Parameters',
            modal: true,
            owner: ui.currentFrame,
            defaultCloseOperation: WindowConstants.DISPOSE_ON_CLOSE
        ) {
            borderLayout()
            panel(constraints: BorderLayout.CENTER) {
                layout = new GridBagLayout()
                def gbc = new GridBagConstraints(
                    fill: GridBagConstraints.BOTH,
                    anchor: GridBagConstraints.NORTHWEST,
                    insets: [2,5,2,5] // Top, left, bottom, right padding
                )

                // 1. Header - Minimal height
                gbc.gridx = 0
                gbc.gridy = 0
                gbc.weightx = 1.0
                gbc.weighty = 0.0 // No vertical expansion
                add(label(
                    text: '<html><b style="font-size:14px">Edit Image Generation Prompt</b><br>'
                        + '<small style="font-size:11px">Template source: ' 
                        + (savedTemplate ? 'User-saved' : 'System default') 
                        + '</small></html>',
                    border: BorderFactory.createEmptyBorder(2,5,2,5)
                ), gbc)

                // 2. Text Area - Takes majority of space
                gbc.gridy++
                gbc.weighty = 1.0 // Allocates 100% of extra vertical space
                gbc.ipady = 10 // Internal padding for text area
                add(scrollPane {
                    promptArea = textArea(
                        text: initialPrompt, 
                        lineWrap: true,
                        wrapStyleWord: true,
                        font: new Font(Font.MONOSPACED, Font.PLAIN, 12)
                    )
                }, gbc)

                // 3. Variables Panel - Fixed height
                gbc.gridy++
                gbc.weighty = 0.0
                gbc.ipady = 0
                add(scrollPane {
                    panel(border: BorderFactory.createTitledBorder("Available Variables")) {
                        gridLayout(rows: 8, columns: 2, hgap: 10, vgap: 5)
                        label(text: '$generatedPrompt'); label(text: 'AI-generated base prompt') 
                        label(text: '$nodeContent'); label(text: 'Current node text')
                        label(text: '$ancestorContents'); label(text: 'All ancestor texts') 
                        label(text: '$siblingContents'); label(text: 'Sibling node texts')
                        label(text: '$rootText'); label(text: 'Root node text')
                        label(text: '$style'); label(text: 'Art style (e.g. digital art)')
                        label(text: '$details'); label(text: 'Detail level (e.g. high)')
                        label(text: '$colors'); label(text: 'Color scheme')
                    }
                }, gbc)

                // 4. Parameters Panel - Fixed height
                gbc.gridy++
                add(panel(border: BorderFactory.createTitledBorder("Generation Parameters")) {
                    gridLayout(rows: 4, columns: 2, hgap: 10, vgap: 5)
                    label(text: 'Steps (4-50):')
                    textField(text: params.steps.toString(), id: 'stepsField')
                    label(text: 'Width (256-1024):')
                    textField(text: params.width.toString(), id: 'widthField')
                    label(text: 'Height (256-1024):')
                    textField(text: params.height.toString(), id: 'heightField')
                    label(text: 'Number of Images:')
                    textField(text: params.imageNum.toString(), id: 'imageNumField')
                }, gbc)
            }
            
            panel(constraints: BorderLayout.SOUTH) {
                generateButton = button(text: 'Generate', actionPerformed: {
                    try {
                        // Validate numerical parameters
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
                button(text: 'Save Template', actionPerformed: {
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
                    } catch (Exception e) {
                        LogUtils.severe("Error saving template: ${e.message}")
                        showError(dialog, "Failed to save template: ${e.message}")
                    }
                })
                button(text: 'Reset to Default', actionPerformed: {
                    int confirm = JOptionPane.showConfirmDialog(dialog,
                        "Reset to default template? This will overwrite any saved template.",
                        "Confirm Reset",
                        JOptionPane.YES_NO_OPTION)
                        
                    if (confirm == JOptionPane.YES_OPTION) {
                        try {
                            // Load default template from JAR resources
                            def defaultTemplate = ResourceLoaderService.loadTextResource('/imageUserPrompt.txt')
                            
                            // Save the default template to config
                            ConfigManager.setUserProperty(config, 'savedImagePromptTemplate', defaultTemplate)
                            
                            // Update UI with default template
                            promptArea.text = defaultTemplate
                            
                            JOptionPane.showMessageDialog(dialog,
                                "Successfully reset to default template!",
                                "Template Reset",
                                JOptionPane.INFORMATION_MESSAGE)
                        } catch(Exception e) {
                            LogUtils.severe("Reset failed: ${e.message}")
                            showError(dialog, "Reset failed: ${e.message}")
                        }
                    }
                })
                button(text: 'Cancel', actionPerformed: { 
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
