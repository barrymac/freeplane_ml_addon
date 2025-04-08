package com.barrymac.freeplane.addons.llm.utils

import javax.swing.SwingUtilities

class UiHelper {
    static void safeEdt(Closure action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.call()
        } else {
            SwingUtilities.invokeLater(action)
        }
    }
}
