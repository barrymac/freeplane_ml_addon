// Check the preview image on this node for private material before publishing.
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.neuros.NeurosPublishCheckAction

LogUtils.info('NeurosPublishCheck menu item invoked.')
NeurosPublishCheckAction.run(c, ui)