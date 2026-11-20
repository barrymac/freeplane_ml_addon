// neurOS settings page: dry-run, attested-by, candidates file, list timeout.
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.neuros.NeurosSettingsAction

LogUtils.info('NeurosSettings menu item invoked.')
NeurosSettingsAction.run(c, ui, config)
