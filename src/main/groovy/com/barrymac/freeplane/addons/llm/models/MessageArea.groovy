package com.barrymac.freeplane.addons.llm.models

import javax.swing.*

class MessageArea {
    JTextArea textArea
    JComboBox comboBox

    void updateSelectedItemFromTextArea() {
        int selectedIndex = comboBox.selectedIndex
        if (selectedIndex == -1) return // Nothing selected

        def text = textArea.text
        def currentItem = comboBox.getItemAt(selectedIndex)

        // Only update if the text actually changed
        if (currentItem instanceof MessageItem && currentItem.value != text) {
            // Create a new com.barrymac.freeplane.addons.llm.models.MessageItem to reflect the change
            def newItem = new MessageItem(text)
            // Update the model directly
            def model = comboBox.getModel()
            if (model instanceof DefaultComboBoxModel) {
                model.removeElementAt(selectedIndex)
                model.insertElementAt(newItem, selectedIndex)
                comboBox.setSelectedIndex(selectedIndex) // Re-select the updated item
            }
        }
    }
}
