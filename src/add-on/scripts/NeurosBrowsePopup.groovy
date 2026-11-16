// Node context-menu entry for lazy neurOS browsing.
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.neuros.NeurosBrowseAction

LogUtils.info("NeurosBrowse node context-menu item invoked.")
NeurosBrowseAction.run(c, ui)
