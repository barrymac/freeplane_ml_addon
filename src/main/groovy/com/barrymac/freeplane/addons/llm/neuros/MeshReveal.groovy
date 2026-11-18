package com.barrymac.freeplane.addons.llm.neuros

import org.freeplane.core.util.LogUtils

/**
 * Puts the result of a drawing run in front of the reader.
 *
 * A run can add a branch below the map and draw connectors far from the current
 * viewport, which reads as "nothing happened". Expanding the container, selecting
 * the first new entry and centring on it is the difference between a result and a
 * silent one.
 */
class MeshReveal {

    static void reveal(def c, def drawing) {
        if (drawing == null) return
        try {
            def container = drawing.container
            if (container != null) {
                container.folded = false
            }
        } catch (Exception problem) {
            LogUtils.warn("Could not expand the container: ${problem.message}")
        }
        def target = drawing.revealTarget
        if (target == null) return
        try {
            c.select(target)
            c.centerOnNode(target)
        } catch (Exception problem) {
            LogUtils.warn("Could not centre on the result: ${problem.message}")
        }
    }
}
