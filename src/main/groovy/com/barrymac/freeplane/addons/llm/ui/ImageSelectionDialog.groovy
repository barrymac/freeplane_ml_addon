package com.barrymac.freeplane.addons.llm.ui

import groovy.swing.SwingBuilder
import org.freeplane.core.ui.components.UITools
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.services.ImageAttachmentHandler
import com.barrymac.freeplane.addons.llm.services.ResourceLoaderService

import javax.swing.*
import java.awt.*
import java.util.List
import javax.swing.SwingUtilities

class ImageSelectionDialog {

    /**
     * Shows a dialog for selecting one image from a list of image URLs
     *
     * @param ui The Freeplane UI object
     * @param imageUrls List of image URLs to display
     * @param imageLoader Closure that loads image bytes from a URL
     * @return The selected image URL or null if cancelled
     */
    static String showImageSelectionDialog(UITools ui, List<String> imageUrls, Closure<byte[]> imageLoader) {
        try {
            LogUtils.info("Showing image selection dialog with ${imageUrls.size()} images")
            String selectedUrl = null

            // Declare dialog variable outside the closure
            def dialog

            def swingBuilder = new SwingBuilder()
            dialog = swingBuilder.dialog(
                    title: 'Select Generated Image',
                    modal: true,
                    owner: ui.currentFrame,
                    defaultCloseOperation: WindowConstants.DISPOSE_ON_CLOSE
            ) {
                borderLayout()

                panel(constraints: BorderLayout.NORTH) {
                    label(text: '<html><b>Click on an image to select it</b></html>',
                            horizontalAlignment: JLabel.CENTER,
                            border: BorderFactory.createEmptyBorder(10, 10, 10, 10))
                }

                panel(constraints: BorderLayout.CENTER, layout: new GridLayout(2, 2, 10, 10),
                        border: BorderFactory.createEmptyBorder(10, 10, 10, 10)) {

                    // Add image panels
                    imageUrls.each { url ->
                        def imagePanel = panel(layout: new BorderLayout(),
                                border: BorderFactory.createEmptyBorder(5, 5, 5, 5)) {

                            def imageLabel = label(text: "Loading...",
                                    horizontalAlignment: JLabel.CENTER,
                                    verticalAlignment: JLabel.CENTER)

                            // Create a loading panel with spinner
                            panel(constraints: BorderLayout.SOUTH) {
                                label(text: "Loading image...", horizontalAlignment: JLabel.CENTER)
                            }
                        }

                        // Add mouse listeners for selection and hover effects
                        imagePanel.addMouseListener(new java.awt.event.MouseAdapter() {
                            void mouseClicked(java.awt.event.MouseEvent e) {
                                selectedUrl = url
                                dialog.dispose()
                            }

                            void mouseEntered(java.awt.event.MouseEvent e) {
                                imagePanel.border = BorderFactory.createCompoundBorder(
                                        BorderFactory.createLineBorder(Color.BLUE, 2),
                                        BorderFactory.createEmptyBorder(3, 3, 3, 3)
                                )
                            }

                            void mouseExited(java.awt.event.MouseEvent e) {
                                imagePanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
                            }
                        })

                        // Load image in background thread
                        new Thread({
                            try {
                                // Get image bytes using the provided loader
                                byte[] imageBytes = imageLoader(url)
                                if (imageBytes) {
                                    // Create image and wait for loading
                                    def image = Toolkit.defaultToolkit.createImage(imageBytes)
                                    def tracker = new MediaTracker(new Panel()) // Dummy component
                                    tracker.addImage(image, 0)
                                    tracker.waitForAll()
                                    
                                    // Validate dimensions
                                    int width = image.getWidth(null)
                                    int height = image.getHeight(null)
                                    if (width <= 0 || height <= 0) {
                                        throw new IllegalStateException("Invalid image dimensions: ${width}x${height}")
                                    }

                                    // Scale to reasonable preview size (max 256x256)
                                    int maxDim = 256
                                    double scale = 1.0
                                    
                                    if (width > maxDim || height > maxDim) {
                                        scale = Math.min(maxDim.toDouble() / width, maxDim.toDouble() / height)
                                        width = (int) (width * scale)
                                        height = (int) (height * scale)
                                    }

                                    def scaledImage = image.getScaledInstance(
                                            width, height, java.awt.Image.SCALE_SMOOTH)
                                            
                                    // Wait for scaled image to load
                                    def scaledTracker = new MediaTracker(new Panel())
                                    scaledTracker.addImage(scaledImage, 0)
                                    scaledTracker.waitForAll()

                                    // Update UI on EDT
                                    SwingUtilities.invokeLater {
                                        // Replace loading text with image
                                        imagePanel.removeAll()
                                        imagePanel.add(new JLabel(new ImageIcon(scaledImage)), BorderLayout.CENTER)
                                        imagePanel.revalidate()
                                        imagePanel.repaint()
                                    }
                                }
                            } catch (Exception e) {
                                LogUtils.severe("Failed to load image: ${e.message}", e)
                                // Update UI on EDT to show error
                                SwingUtilities.invokeLater {
                                    def components = imagePanel.components
                                    for (component in components) {
                                        if (component instanceof JLabel) {
                                            component.icon = null
                                            component.text = "Failed to load image"
                                            component.foreground = Color.RED
                                        } else if (component instanceof JPanel) {
                                            imagePanel.remove(component)
                                        }
                                    }
                                    imagePanel.revalidate()
                                    imagePanel.repaint()
                                }
                            }
                        }).start()
                    }
                }

                panel(constraints: BorderLayout.SOUTH) {
                    button(text: 'Cancel', actionPerformed: { dialog.dispose() })
                }
            }

            // Set size and position
            dialog.pack()
            dialog.minimumSize = new Dimension(600, 600)
            ui.setDialogLocationRelativeTo(dialog, ui.currentFrame)
            dialog.visible = true

            return selectedUrl
        } catch (Exception e) {
            LogUtils.severe("Error showing image selection dialog: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Handles the complete flow of image selection, downloading, and attachment
     *
     * @param ui The Freeplane UI object
     * @param imageUrls List of image URLs to display
     * @param node The node to attach the image to
     * @param config The Freeplane config object
     */
    static void handleImageSelection(UITools ui, List<String> imageUrls, def node, def config) {
        try {
            // Define the downloader within the helper
            Closure<byte[]> downloader = { String url ->
                try {
                    LogUtils.info("Downloader: Loading image from: ${url}")
                    if (url.startsWith("/")) {
                        return ResourceLoaderService.loadBundledResourceBytes(url)
                    } else {
                        def connection = new URL(url).openConnection()
                        connection.connectTimeout = 10000
                        connection.readTimeout = 30000
                        return connection.inputStream.bytes
                    }
                } catch (Exception e) {
                    LogUtils.severe("Downloader error: ${e.message}")
                    return null
                }
            }

            // Show selection dialog and get URL
            String selectedUrl = showImageSelectionDialog(ui, imageUrls, downloader)
            
            if (selectedUrl) {
                // Show download progress
                def downloadProgress = createProgressDialog(ui, "Downloading Image", "Downloading selected image...")
                try {
                    downloadProgress.visible = true
                    byte[] imageBytes = downloader(selectedUrl)
                    
                    if (imageBytes) {
                        SwingUtilities.invokeLater {
                            // Attach image
                            String baseName = ImageAttachmentHandler.sanitizeBaseName(node.text)
                            String extension = ImageAttachmentHandler.getFileExtension(selectedUrl)
                            ImageAttachmentHandler.attachImageToNode(node, imageBytes, baseName, extension)
                            ui.showInformationMessage("Image added to node!")
                        }
                    } else {
                        ui.showErrorMessage("Failed to download image")
                    }
                } finally {
                    downloadProgress.dispose()
                }
            }
        } catch (Exception e) {
            LogUtils.severe("Image handling error: ${e.message}", e)
            ui.showErrorMessage("Image handling failed: ${e.message}")
        }
    }

    /**
     * Creates a simple progress dialog
     * 
     * @param ui The Freeplane UI object
     * @param title Dialog title
     * @param message Message to display
     * @return The created dialog
     */
    static JDialog createProgressDialog(UITools ui, String title, String message) {
        new SwingBuilder().dialog(
            title: title,
            modal: true,
            owner: ui.currentFrame,
            defaultCloseOperation: WindowConstants.DO_NOTHING_ON_CLOSE
        ) {
            label(text: message, horizontalAlignment: JLabel.CENTER, border: BorderFactory.createEmptyBorder(10, 20, 10, 20))
        }.with {
            pack()
            setLocationRelativeTo(ui.currentFrame)
            it
        }
    }
}
