// Draw the dependency closure of one card as a labelled DAG, walking declared
// dependency edges (depends-on / blocked-by / blocks) transitively. Cycles are
// rendered as red defects, never hidden. Depth and fan-out are bounded.
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.neuros.NeurosDependencyAction

LogUtils.info('NeurosDependency menu item invoked.')
NeurosDependencyAction.run(c, ui)
