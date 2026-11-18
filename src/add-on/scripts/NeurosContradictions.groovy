// Ask a judgement model whether the selected entry disagrees with the entries it
// links to, and draw only what the judgement supports.
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.neuros.NeurosContradictionAction

LogUtils.info('NeurosContradictions menu item invoked.')
NeurosContradictionAction.run(c, ui)