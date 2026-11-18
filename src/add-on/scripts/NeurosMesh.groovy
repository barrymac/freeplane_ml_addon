// Draw the typed relationships a knowledge entry declares as map connectors.
// Reads only; the map is the thing that changes, not the knowledge source.
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.neuros.NeurosMeshAction

LogUtils.info('NeurosMesh menu item invoked.')
NeurosMeshAction.run(c, ui)