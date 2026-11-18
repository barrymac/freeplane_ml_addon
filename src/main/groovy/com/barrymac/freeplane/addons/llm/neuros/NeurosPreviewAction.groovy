package com.barrymac.freeplane.addons.llm.neuros

import io.github.gitbucket.markedj.Marked
import io.github.gitbucket.markedj.Options
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.ConfigManager
import com.barrymac.freeplane.addons.llm.ui.UiHelper

import javax.imageio.ImageIO
import javax.swing.*
import java.awt.image.BufferedImage
import java.security.MessageDigest

/** On-demand Markdown / PlantUML preview plus rollover-note priming. */
class NeurosPreviewAction {

    static final int MAX_SOURCE_BYTES = 2_000_000

    static void run(def c, def ui, def config) {
        LogUtils.info('NeurosPreview action started.')
        try {
            def node = c.selected
            if (node == null) {
                UiHelper.showInformationMessage(ui, 'Please select a node first.')
                return
            }
            String source = readSource(node)
            if (source == null) {
                UiHelper.showInformationMessage(ui,
                        'This is a knowledge surface, not an entry. Expand it and select ' +
                                'an entry to preview.')
                return
            }
            if (!source.trim()) {
                UiHelper.showInformationMessage(ui,
                        'Select a knowledge entry or a node containing Markdown or PlantUML.')
                return
            }
            NeurosLimits limits = NeurosLimits.load(node.map.storage)
            boolean plantUml = isPlantUml(source)
            LogUtils.info("NeurosPreview: source type=${plantUml ? 'PlantUML' : 'Markdown'}")

            if (plantUml) {
                File cached = previewCacheFile(config, source, 'plantuml')
                BufferedImage image
                if (cached.isFile()) {
                    LogUtils.info("NeurosPreview: reusing cached preview ${cached.path}")
                    image = ImageIO.read(cached)
                } else {
                    image = PlantumlRenderer.renderPng(source,
                            limits.plantumlCommand, limits.plantumlTimeoutSeconds)
                    ImageIO.write(image, 'png', cached)
                }
                primePreviewNote(node, cached, 'Diagram preview', 0.5f)
                showImage(ui, image, node.text ?: 'PlantUML preview')
            } else {
                String displayMarkdown = MarkdownPreviewSanitizer.clean(source)
                String html = markdownToHtml(displayMarkdown)
                File cached = previewCacheFile(config, displayMarkdown, 'markdown')
                BufferedImage image
                if (cached.isFile()) {
                    LogUtils.info("NeurosPreview: reusing cached preview ${cached.path}")
                    image = ImageIO.read(cached)
                } else {
                    image = MarkdownImageRenderer.renderHtml(html)
                    ImageIO.write(image, 'png', cached)
                }
                primeMarkdownNote(node, displayMarkdown)
                primePreviewNote(node, cached, 'Markdown preview', 0.35f)
                showHtml(ui, html, node.text ?: 'Markdown preview')
            }
            LogUtils.info('NeurosPreview: dialog displayed.')
        } catch (Exception problem) {
            LogUtils.severe("Error in NeurosPreview action: ${problem.message}", problem)
            UiHelper.showErrorMessage(ui, "Preview neurOS Content Error: ${problem.message}")
        }
    }

    static boolean isPlantUml(String source) {
        return source?.contains('@start') && source.contains('@end')
    }

    static String markdownToHtml(String markdown) {
        Options options = new Options()
        options.setSafelist(null)
        return '<html><body>' + Marked.marked(markdown, options) + '</body></html>'
    }

    private static String readSource(def node) {
        File linked = LinkTargets.fileFor(node)
        if (linked != null) {
            if (linked.isDirectory()) {
                return null
            }
            try {
                return readBounded(linked)
            } catch (IOException problem) {
                if (problem.message?.contains('Is a directory')) {
                    return null
                }
                throw new IOException(
                        'The source material could not be read right now. ' +
                                'The knowledge source may be busy or the entry may have moved.',
                        problem)
            }
        }
        return (node.text ?: '').toString()
    }

    private static String readBounded(File file) {
        byte[] data = new byte[MAX_SOURCE_BYTES + 1]
        int count
        file.withInputStream { input ->
            count = input.read(data, 0, data.length)
        }
        if (count > MAX_SOURCE_BYTES) {
            throw new IOException('The source is too large to preview safely.')
        }
        return new String(data, 0, Math.max(count, 0), 'UTF-8')
    }

    /** Cache path for a rendered preview, next to the add-on's own files. */
    static File previewCacheFile(def config, String source, String prefix) {
        File directory = new File(ConfigManager.getAddonsDir(config), 'previews')
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException('Cannot create the add-on preview folder.')
        }
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        String key = digest.digest(source.getBytes('UTF-8')).encodeHex().toString()
        return new File(directory, "${prefix}-${key}.png")
    }

    /** Prime Freeplane's own Markdown note renderer for subsequent rollovers. */
    private static void primeMarkdownNote(def node, String markdown) {
        String existing = (node.note ?: '').toString()
        boolean generatedNote = existing.contains('Use the link to open') ||
                existing.contains('[[card:')
        if (!existing || generatedNote) {
            node.note = markdown
        } else if (!existing.contains(markdown)) {
            node.note = existing + '\n\n---\n\n' + markdown
        }
        node.noteContentType = 'markdown'
    }

    /** Prime a Markdown image preview for subsequent rollovers. */
    private static void primePreviewNote(def node, File cached, String label, float zoom) {
        String preview = "![${label}](${cached.toURI().toString()})"
        String existing = (node.note ?: '').toString()
        if (!existing.contains(cached.toURI().toString())) {
            node.note = existing ? existing + '\n\n' + preview : preview
        }
        node.noteContentType = 'markdown'
        // Embed the rendered preview in the map as well as in the note.
        node.externalObject.file = cached
        node.externalObject.zoom = zoom
    }

    private static void showHtml(def ui, String html, String title) {
        JEditorPane pane = new JEditorPane('text/html', html)
        pane.editable = false
        JScrollPane scroll = new JScrollPane(pane)
        showDialog(ui, scroll, title)
    }

    private static void showImage(def ui, BufferedImage image, String title) {
        JLabel label = new JLabel(new ImageIcon(image))
        JScrollPane scroll = new JScrollPane(label)
        showDialog(ui, scroll, title)
    }

    private static void showDialog(def ui, JScrollPane content, String title) {
        content.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        content.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        JDialog dialog = new JDialog(ui.currentFrame, title, false)
        dialog.contentPane.add(content)
        dialog.setSize(900, 700)
        dialog.setLocationRelativeTo(ui.currentFrame)
        dialog.visible = true
    }
}
