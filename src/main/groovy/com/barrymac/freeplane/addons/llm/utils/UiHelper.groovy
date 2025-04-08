package com.barrymac.freeplane.addons.llm.utils

import org.freeplane.core.util.LogUtils
import javax.swing.*

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
}
