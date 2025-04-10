package com.barrymac.freeplane.addons.llm.ui

import groovy.swing.SwingBuilder
import org.freeplane.core.util.LogUtils
import javax.swing.*
import java.awt.*

class PromptEditor {
    static def showPromptEditor(ui, String initialPrompt, Map initialParams) {
        def modifiedPrompt = null
        def params = initialParams.clone()
        // Declare dialog variable outside the closure
        def dialog
        dialog = new SwingBuilder().dialog(
            title: 'Edit Image Generation Parameters',
            modal: true,
            owner: ui.currentFrame,
            defaultCloseOperation: WindowConstants.DISPOSE_ON_CLOSE
        ) {
            borderLayout()
            panel(constraints: BorderLayout.CENTER) {
                gridLayout(rows: 4, columns: 1) // Explicit 4 rows for header, prompt, variables, params
                label(text: '<html><b>Edit Image Generation Prompt</b></html>', 
                      border: BorderFactory.createEmptyBorder(5,5,5,5))
                scrollPane {
                    textArea(
                        text: initialPrompt, 
                        rows: 8, 
                        columns: 60, 
                        id: 'promptArea',
                        lineWrap: true,      // Enable line wrapping
                        wrapStyleWord: true  // Wrap at word boundaries
                    )
                }
                panel(border: BorderFactory.createTitledBorder("Available Variables")) {
                    gridLayout(rows: 8, columns: 2, hgap: 10, vgap: 5)  // Increased rows
                    label(text: '$generatedPrompt'); label(text: 'AI-generated base prompt') 
                    label(text: '$nodeContent'); label(text: 'Current node text')
                    label(text: '$ancestorContents'); label(text: 'All ancestor texts') 
                    label(text: '$siblingContents'); label(text: 'Sibling node texts')
                    label(text: '$rootText'); label(text: 'Root node text')
                    label(text: '$style'); label(text: 'Art style (e.g. digital art)')
                    label(text: '$details'); label(text: 'Detail level (e.g. high)')
                    label(text: '$colors'); label(text: 'Color scheme')
                }
                panel(border: BorderFactory.createTitledBorder("Generation Parameters")) {
                    gridLayout(rows: 4, columns: 2, hgap: 10, vgap: 5) // Explicit rows/columns
                    label(text: 'Steps (4-50):')
                    textField(text: params.steps.toString(), id: 'stepsField')
                    label(text: 'Width (256-1024):')
                    textField(text: params.width.toString(), id: 'widthField')
                    label(text: 'Height (256-1024):')
                    textField(text: params.height.toString(), id: 'heightField')
                    label(text: 'Number of Images:')
                    textField(text: params.imageNum.toString(), id: 'imageNumField')
                }
            }
            panel(constraints: BorderLayout.SOUTH) {
                button(text: 'Generate', actionPerformed: {
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
                button(text: 'Cancel', actionPerformed: { 
                    modifiedPrompt = null
                    dialog.dispose()
                })
            }
        }
        
        dialog.pack()
        dialog.setSize(600, 500) // Explicit size to ensure visibility
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
