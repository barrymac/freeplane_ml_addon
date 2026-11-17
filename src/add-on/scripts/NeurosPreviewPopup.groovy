// Node context-menu entry for on-demand Markdown / PlantUML preview.
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.neuros.NeurosPreviewAction

LogUtils.info("NeurosPreview node context-menu item invoked.")
NeurosPreviewAction.run(c, ui, config)
