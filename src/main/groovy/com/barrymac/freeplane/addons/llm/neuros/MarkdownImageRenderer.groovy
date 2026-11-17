package com.barrymac.freeplane.addons.llm.neuros

import javax.swing.*
import java.awt.*
import java.awt.image.BufferedImage

/**
 * Renders the same HTML used by the Markdown preview dialog into a cached PNG.
 * This gives Markdown the same embedded map preview as PlantUML without
 * introducing another external renderer.
 */
class MarkdownImageRenderer {

    static final int DEFAULT_WIDTH = 900
    static final int MAX_HEIGHT = 2400

    static BufferedImage renderHtml(String html, int width = DEFAULT_WIDTH,
                                    int maxHeight = MAX_HEIGHT) {
        if (!html?.trim()) throw new IllegalArgumentException('There is no Markdown to render.')
        int targetWidth = Math.max(320, width)
        JEditorPane pane = new JEditorPane('text/html', html)
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE)
        pane.background = Color.WHITE
        pane.border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
        pane.setSize(targetWidth, Integer.MAX_VALUE)
        Dimension preferred = pane.preferredSize
        int height = Math.min(Math.max(preferred.height, 160), maxHeight)
        pane.setSize(targetWidth, height)
        pane.doLayout()

        BufferedImage image = new BufferedImage(targetWidth, height,
                BufferedImage.TYPE_INT_RGB)
        Graphics2D graphics = image.createGraphics()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, targetWidth, height)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        pane.paint(graphics)
        graphics.dispose()
        return image
    }
}