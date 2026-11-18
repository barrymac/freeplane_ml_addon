package com.barrymac.freeplane.addons.llm.neuros

import com.barrymac.freeplane.addons.llm.Keys
import com.barrymac.freeplane.addons.llm.judgement.PrivacyCheck
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.ui.UiHelper

/**
 * Checks a preview image for private material before it is published.
 *
 * This is the eyeball pass made explicit: the same cached preview the map shows
 * is sent to Jev, and the answer is a probability, not an opinion. Nothing is
 * written to the knowledge source — the verdict is recorded as a tag on the node.
 */
class NeurosPublishCheckAction {

    static final String TAG_SAFE = 'publish check: safe'
    static final String TAG_REVIEW = 'publish check: review'
    static final String TAG_UNSAFE = 'publish check: not safe'

    static void run(def c, def ui) {
        LogUtils.info('NeurosPublishCheck action started.')
        try {
            def node = c.selected
            if (node == null) {
                UiHelper.showInformationMessage(ui, 'Please select a node first.')
                return
            }
            File image = previewImage(node)
            if (image == null) {
                UiHelper.showInformationMessage(ui,
                        'No preview image on this node yet. Preview the entry first, then check it.')
                return
            }
            String key = Keys.jev()
            if (!key) {
                UiHelper.showInformationMessage(ui,
                        'This needs Jev. Set JEV_API_KEY in the environment, then try again.')
                return
            }

            PrivacyCheck.Result result = PrivacyCheck.check(image, key)
            LogUtils.info("NeurosPublishCheck: ${result.status} " +
                    "(private=${result.privateDetails}, verdict=${result.verdict})")
            tag(node, result.status)
            UiHelper.showInformationMessage(ui,
                    result.summary() + '\n\nImage: ' + image.name)
        } catch (Exception problem) {
            LogUtils.severe("Error in NeurosPublishCheck action: ${problem.message}", problem)
            UiHelper.showErrorMessage(ui, "Check Preview Error: ${problem.message}")
        }
    }

    /** The image the map already shows: an external object, or a note image. */
    static File previewImage(def node) {
        try {
            File external = node.externalObject?.file
            if (external != null && external.isFile()) return external
        } catch (Exception ignored) {
            // No external object is a normal state, not an error.
        }
        String note = (node.note ?: '').toString()
        def matcher = (note =~ /!\[[^\]]*\]\((file:[^)]+)\)/)
        if (matcher.find()) {
            try {
                File fromNote = new File(java.net.URI.create(matcher.group(1)))
                if (fromNote.isFile()) return fromNote
            } catch (Exception ignored) {
                return null
            }
        }
        return null
    }

    private static void tag(def node, String status) {
        [TAG_SAFE, TAG_REVIEW, TAG_UNSAFE].each { String existing ->
            if (node.tags.contains(existing)) node.tags.remove(existing)
        }
        node.tags.add(status == PrivacyCheck.UNSAFE ? TAG_UNSAFE :
                status == PrivacyCheck.REVIEW ? TAG_REVIEW : TAG_SAFE)
    }
}