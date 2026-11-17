// On-demand Markdown / PlantUML preview. PlantUML execution is explicitly
// permitted only for this action, never for search/browse/save.
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.neuros.NeurosPreviewAction

LogUtils.info("NeurosPreview menu item invoked.")
NeurosPreviewAction.run(c, ui, config)
