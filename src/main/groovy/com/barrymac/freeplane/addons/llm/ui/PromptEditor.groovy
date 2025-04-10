package com.barrymac.freeplane.addons.llm.ui

import groovy.swing.SwingBuilder
import org.freeplane.core.util.LogUtils
import javax.swing.*
import java.awt.*

class PromptEditor {
    static def showPromptEditor(ui, String initialPrompt, Map initialParams) {
        def modifiedPrompt = null
        def params = initialParams.clone()
        def dialog = new SwingBuilder().dialog(
            title: 'Edit Image Generation Parameters',
            modal: true,
            owner: ui.currentFrame,
            defaultCloseOperation: WindowConstants.DISPOSE_ON_CLOSE
        ) {
            borderLayout()
            panel(constraints: BorderLayout.CENTER) {
                gridLayout(rows: 0, cols: 1)
                label(text: '<html><b>Edit Image Generation Prompt</b><br>Modify the prompt and parameters:</html>', 
                      border: BorderFactory.createEmptyBorder(5,5,15,5))
                scrollPane {
                    textArea(text: initialPrompt, rows: 8, columns: 60, id: 'promptArea')
                }
                panel(border: BorderFactory.createTitledBorder("Generation Parameters")) {
                    gridLayout(rows: 4, cols: 2, hgap: 10, vgap: 5)
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
                        params.steps = validateRange(stepsField.text.toInteger(), 4, 50, "Steps")
                        params.width = validateRange(widthField.text.toInteger(), 256, 1024, "Width")
                        params.height = validateRange(heightField.text.toInteger(), 256, 1024, "Height") 
                        params.imageNum = validateRange(imageNumField.text.toInteger(), 1, 4, "Image Count")
                        
                        modifiedPrompt = promptArea.text.trim()
                        if(!modifiedPrompt) {
                            throw new IllegalArgumentException("Prompt cannot be empty")
                        }
                        dialog.dispose()
                    } catch(NumberFormatException e) {
                        showError("Invalid number format: ${e.message}")
                    } catch(IllegalArgumentException e) {
                        showError(e.message)
                    }
                })
                button(text: 'Cancel', actionPerformed: { 
                    modifiedPrompt = null
                    dialog.dispose()
                })
            }
        }
        
        dialog.pack()
        dialog.setLocationRelativeTo(ui.currentFrame)
        dialog.visible = true
        
        return modifiedPrompt ? [modifiedPrompt, params] : null
    }
    
    private static int validateRange(value, min, max, name) {
        if(value < min || value > max) {
            throw new IllegalArgumentException("$name must be between $min and $max")
        }
        return value
    }
    
    private static void showError(dialog, message) {
        JOptionPane.showMessageDialog(dialog, message, "Invalid Input", JOptionPane.ERROR_MESSAGE)
    }
}
