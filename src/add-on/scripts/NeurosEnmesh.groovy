// Enmesh this node: propose missing relationships, drawn provisionally.
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.neuros.NeurosEnmeshAction

LogUtils.info('NeurosEnmesh menu item invoked.')
NeurosEnmeshAction.run(c, ui)