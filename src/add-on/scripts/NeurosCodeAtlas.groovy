// Generate a code atlas branch from the substrate's sitter-structure-v1
// renditions: language containers, file nodes stamped with rendition uuids,
// declarations with signature and source range. Reads only.
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.neuros.CodeAtlasAction

LogUtils.info('NeurosCodeAtlas menu item invoked.')
CodeAtlasAction.run(c, ui)
