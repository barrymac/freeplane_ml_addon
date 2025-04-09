package com.barrymac.freeplane.addons.llm.ui

import com.barrymac.freeplane.addons.llm.models.MessageArea
import com.barrymac.freeplane.addons.llm.models.MessageItem
import groovy.swing.SwingBuilder

// Remove - No longer used here

import org.freeplane.core.ui.components.UITools
import org.freeplane.core.util.LogUtils

// Keep for potential internal UI messages if needed

import org.freeplane.plugin.script.FreeplaneScriptBaseClass.ConfigProperties

import javax.swing.*
import java.awt.*

// Keep for ui.currentFrame, setDialogLocationRelativeTo

import java.util.List

// Keep explicit import for method signature and internal list copies

/**
 * Helper class for creating and managing UI dialogs
 */
class DialogHelper {
    /**
     * Creates a progress dialog for long-running operations
     *
     * @param ui The Freeplane UI object
     * @param title The dialog title
     * @param message The message to display
     * @return The created dialog (not yet visible)
     */
    static JDialog createProgressDialog(UITools ui, String title, String message) {
        try {
            LogUtils.info("Creating progress dialog: ${title}")
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
            LogUtils.severe("Error creating progress dialog: ${e.message}")
            // Return a minimal dialog in case of error
            return new JDialog(ui.currentFrame, title, false)
        }
    }

    /**
     * Helper method to create a message section (ComboBox + TextArea + Buttons)
     */
    static MessageArea createMessageSection(def swingBuilder, def messages, def title, int initialIndex, def constraints, def weighty) {
        def comboBoxModel = new DefaultComboBoxModel()
        messages.each { comboBoxModel.addElement(new MessageItem(it)) }
        def messageComboBox, messageText
        def selectedIndex = initialIndex

        constraints.gridy++
        swingBuilder.label("${title}:", constraints: constraints)
        constraints.gridy++
        messageComboBox = swingBuilder.comboBox(model: comboBoxModel, constraints: constraints)
        // Ensure initial index is valid
        if (initialIndex >= 0 && initialIndex < messages.size()) {
            messageComboBox.selectedIndex = initialIndex
        } else if (!messages.isEmpty()) {
            messageComboBox.selectedIndex = 0 // Default to first if initial is invalid
            selectedIndex = 0
        } else {
            messageComboBox.selectedIndex = -1 // No selection if empty
            selectedIndex = -1
        }


        constraints.gridy++
        constraints.weighty = 1.0 * weighty
        swingBuilder.scrollPane(constraints: constraints) {
            // Initialize text area with the selected message or empty if none
            def initialText = (selectedIndex != -1) ? messages[selectedIndex] : ""
            messageText = swingBuilder.textArea(rows: 5 * weighty, columns: 80, tabSize: 3, lineWrap: true, wrapStyleWord: true, text: initialText, caretPosition: 0) {}
        }
        messageComboBox.addActionListener { actionEvent ->
            def newlySelectedIndex = messageComboBox.selectedIndex
            // Check if selection actually changed and is valid
            if (newlySelectedIndex != selectedIndex && newlySelectedIndex != -1) {
                // Save the text from the text area to the *previous* index in the underlying list
                if (selectedIndex != -1 && selectedIndex < messages.size()) {
                    messages[selectedIndex] = messageText.text
                    // Update the corresponding MessageItem in the ComboBox model
                    def oldItem = comboBoxModel.getElementAt(selectedIndex)
                    // Check if the item exists and its value differs from the text area
                    if (oldItem instanceof MessageItem && oldItem.value != messageText.text) {
                        // Create a new MessageItem with the updated text for display
                        def updatedDisplayItem = new MessageItem(messageText.text)
                        comboBoxModel.removeElementAt(selectedIndex)
                        comboBoxModel.insertElementAt(updatedDisplayItem, selectedIndex)
                        // Re-select the item after update
                        messageComboBox.setSelectedIndex(selectedIndex)
                    }
                }

                // Update the text area with the content of the *newly* selected item
                messageText.text = messages[newlySelectedIndex]
                messageText.caretPosition = 0
                selectedIndex = newlySelectedIndex // Update the tracked selected index
            } else if (newlySelectedIndex == -1) {
                // Handle deselection (e.g., when deleting the last item)
                messageText.text = ""
                selectedIndex = -1
            }
        }
        constraints.gridy++
        constraints.weighty = 0.0
        swingBuilder.panel(layout: new FlowLayout(), constraints: constraints) {
            button(action: swingBuilder.action(name: "Reset ${title}".toString()) {
                if (selectedIndex != -1 && selectedIndex < messages.size()) {
                    messageText.text = messages[selectedIndex]
                    messageText.caretPosition = 0
                }
            })
            button(action: swingBuilder.action(name: "Duplicate ${title}".toString()) {
                def textToDuplicate = messageText.text // Duplicate current text area content
                messages.add(textToDuplicate)
                def newItem = new MessageItem(textToDuplicate)
                comboBoxModel.addElement(newItem)
                // Select the newly added item
                messageComboBox.selectedIndex = comboBoxModel.getSize() - 1
                // selectedIndex will be updated by the action listener
            })
            button(action: swingBuilder.action(name: "Delete ${title}".toString()) {
                if (selectedIndex != -1) {
                    def indexToRemove = selectedIndex
                    messages.remove(indexToRemove)
                    comboBoxModel.removeElementAt(indexToRemove)
                    // Adjust selection if needed (select previous or first, or none if empty)
                    if (comboBoxModel.getSize() > 0) {
                        messageComboBox.selectedIndex = Math.max(0, indexToRemove - 1)
                    } else {
                        messageComboBox.selectedIndex = -1
                    }
                    // selectedIndex will be updated by the action listener based on the new selection
                }
            })
        }
        return new MessageArea(textArea: messageText, comboBox: messageComboBox)
    }

}
