// Top-menu entry for lazy neurOS browsing.
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.neuros.NeurosBrowseAction

LogUtils.info("NeurosBrowse menu item invoked.")
NeurosBrowseAction.run(c, ui)
