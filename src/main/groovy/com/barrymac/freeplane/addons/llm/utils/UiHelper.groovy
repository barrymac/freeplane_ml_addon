package com.barrymac.freeplane.addons.llm.utils

import org.freeplane.core.util.LogUtils
import javax.swing.*
import java.awt.Component
import java.awt.Container

class UiHelper {
    static void safeEdt(Closure action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.call()
        } else {
            SwingUtilities.invokeLater(action)
        }
    }

    static void showInformationMessage(def ui, String message) {
        safeEdt {
            ui.informationMessage(message)
        }
    }

    static void showErrorMessage(def ui, String message) {
        safeEdt {
            ui.errorMessage(message)
        }
    }

    static void updateDialogMessage(JDialog dialog, String message) {
        safeEdt {
            def textArea = dialog.contentPane.find { it instanceof JScrollPane }?.viewport?.view
            if (textArea instanceof JTextArea) {
                textArea.text = message
            } else {
                LogUtils.warn("Could not find JTextArea in dialog to update message.")
            }
        }
    }

    static void disposeDialog(JDialog dialog) {
        safeEdt {
            dialog.dispose()
        }
    }

    // Thread-safe method to update dialog message
    static void updateDialogMessageThreadSafe(JDialog dialog, String message) {
        if (dialog == null || !dialog.isDisplayable()) return // Check if dialog is valid

        SwingUtilities.invokeLater {
            // Find the text area within the dialog's content pane
            def contentPane = dialog.getContentPane()
            // Use the helper method to find the component
            def textArea = findComponent(contentPane, JTextArea.class)

            if (textArea != null) {
                textArea.text = message
                LogUtils.info("Progress dialog message updated (thread-safe): ${message}")
            } else {
                LogUtils.warn("Could not find JTextArea in progress dialog to update message.")
            }
        }
    }

    // Helper method to find a component of a specific type recursively
    private static <T extends Component> T findComponent(Container container, Class<T> componentClass) {
        for (Component comp : container.getComponents()) {
            if (componentClass.isInstance(comp)) {
                return (T) comp
            } else if (comp instanceof Container) {
                T found = findComponent((Container) comp, componentClass)
                if (found != null) {
                    return found
                }
            }
        }
        return null // Not found
    }
}
