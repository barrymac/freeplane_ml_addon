// Resolve candidate entities: confirm / merge / split / none, with dry-run diff.
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.neuros.NeurosConfirmAction

LogUtils.info('NeurosConfirm menu item invoked.')
NeurosConfirmAction.run(c, ui, config)
