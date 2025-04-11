package com.barrymac.freeplane.addons.llm.ui

import groovy.swing.SwingBuilder
import org.freeplane.core.ui.components.UITools
import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.services.ImageAttachmentHandler
import com.barrymac.freeplane.addons.llm.services.ResourceLoaderService
// --- ADDED IMPORT ---
import static com.barrymac.freeplane.addons.llm.ui.UiHelper.*

import javax.swing.*
import java.awt.*
import java.util.List
import javax.swing.SwingUtilities
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import java.util.concurrent.ExecutionException // Import needed

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
            // Define the downloader closure (remains the same)
            Closure<byte[]> downloader = { String url ->
                try {
                    LogUtils.info("Downloader: Loading image from: ${url}")
                    if (url.startsWith("/")) {
                        return ResourceLoaderService.loadBundledResourceBytes(url)
                    } else {
                        def connection = new URL(url).openConnection()
                        connection.connectTimeout = 10000 // 10 seconds
                        connection.readTimeout = 30000  // 30 seconds
                        return connection.inputStream.bytes
                    }
                } catch (Exception e) {
                    LogUtils.severe("Downloader error for URL [${url}]: ${e.message}", e) // Add URL to log
                    return null
                }
            }

            // Show selection dialog and get URL (this is modal, runs on EDT)
            String selectedUrl = showImageSelectionDialog(ui, imageUrls, downloader)

            if (selectedUrl) {
                LogUtils.info("User selected image URL: ${selectedUrl}")
                // --- MODIFICATION START: Download in background ---
                // Show download progress dialog (non-cancellable for now)
                def downloadProgress = createProgressDialog(ui, "Downloading Image", "Downloading selected image...")

                // Use SwingWorker for the download and attachment
                def downloadWorker = new SwingWorker<byte[], Void>() {
                    @Override
                    protected byte[] doInBackground() throws Exception {
                        LogUtils.info("DownloadWorker: Starting download for selected image in background...")
                        byte[] imageBytes = downloader(selectedUrl) // Perform download in background
                        LogUtils.info("DownloadWorker: Download finished. Bytes received: ${imageBytes?.length ?: 0}")
                        return imageBytes
                    }

                    @Override
                    protected void done() {
                        LogUtils.info("DownloadWorker: done() method started.")
                        try {
                            byte[] imageBytes = get() // Get result or exception

                            if (imageBytes) {
                                LogUtils.info("DownloadWorker: Attaching image to node...")
                                // Attach image (needs to happen on EDT, which done() is)
                                String baseName = ImageAttachmentHandler.sanitizeBaseName(node.text)
                                String extension = ImageAttachmentHandler.getFileExtension(selectedUrl)
                                ImageAttachmentHandler.attachImageToNode(node, imageBytes, baseName, extension)
                                LogUtils.info("DownloadWorker: Image attached successfully.")
                                // showInformationMessage(ui, "Image added to node!") // REMOVED AS REQUESTED
                            } else {
                                LogUtils.error("DownloadWorker: Download failed (imageBytes is null).")
                                // --- MODIFIED CALL ---
                                showErrorMessage(ui, "Failed to download selected image.")
                            }
                        } catch (ExecutionException e) {
                            Throwable cause = e.getCause() ?: e
                            LogUtils.severe("DownloadWorker: Error during image download/attachment: ${cause.message}", cause)
                            // --- MODIFIED CALL ---
                            showErrorMessage(ui, "Image download/attachment failed: ${cause.message?.split('\n')?.head()}")
                        } catch (Exception e) { // Catch other unexpected errors
                            LogUtils.severe("DownloadWorker: Unexpected error in done(): ${e.message}", e)
                            // --- MODIFIED CALL ---
                            showErrorMessage(ui, "An unexpected error occurred: ${e.message?.split('\n')?.head()}")
                        } finally {
                            LogUtils.info("DownloadWorker: Disposing download progress dialog.")
                            downloadProgress?.dispose() // Dispose the download dialog here
                        }
                        LogUtils.info("DownloadWorker: done() method finished.")
                    }
                } // End downloadWorker definition

                // Start the download worker
                downloadWorker.execute()
                LogUtils.info("DownloadWorker started.")
                // Show the modal progress dialog *after* starting the worker
                downloadProgress.visible = true
                LogUtils.info("Download progress dialog is now visible.")
                // --- MODIFICATION END ---
            } else {
                LogUtils.info("User cancelled image selection.")
            }
        } catch (Exception e) {
            // Catch errors during the setup of handleImageSelection itself
            LogUtils.severe("Image handling setup error: ${e.message}", e)
            // --- MODIFIED CALL (outside worker, but good practice) ---
            showErrorMessage(ui, "Image handling setup failed: ${e.message}")
        }
    } // End handleImageSelection

    // --- REPLACED METHOD START ---
    /**
     * Creates a progress dialog with an optional Cancel button.
     * @param ui Freeplane UI object (UITools)
     * @param title Dialog title
     * @param message Message to display
     * @param cancelAction (Optional) Closure to execute when Cancel is clicked or Escape is pressed.
     * @return The created JDialog instance.
     */
    static JDialog createProgressDialog(UITools ui, String title, String message, Closure cancelAction = null) {
        def dialog
        def swingBuilder = new SwingBuilder() // Need to instantiate SwingBuilder here
        swingBuilder.edt { // Ensure UI creation happens on EDT
            dialog = swingBuilder.dialog( // Dialog definition starts here
                title: title,
                modal: true, // Keep it modal to block user interaction with main window
                owner: ui.currentFrame,
                defaultCloseOperation: WindowConstants.DO_NOTHING_ON_CLOSE // Prevent closing via 'X' button
            ) {
                borderLayout()
                panel(constraints: BorderLayout.CENTER, border: BorderFactory.createEmptyBorder(20, 20, 20, 20)) {
                    boxLayout(axis: BoxLayout.Y_AXIS)
                    label(text: message, alignmentX: Component.CENTER_ALIGNMENT)
                    // Add an indeterminate progress bar for better visual feedback
                    panel(alignmentX: Component.CENTER_ALIGNMENT, border: BorderFactory.createEmptyBorder(10, 0, 0, 0)) { // Panel for progress bar with top margin
                        progressBar(indeterminate: true, alignmentX: Component.CENTER_ALIGNMENT)
                    }
                }
                // Add Cancel button only if cancelAction is provided
                if (cancelAction) {
                    panel(constraints: BorderLayout.SOUTH, layout: new FlowLayout(FlowLayout.CENTER)) {
                        button(text: 'Cancel', actionPerformed: { cancelAction() })
                    }
                    // --- KEY BINDING BLOCK WAS HERE ---
                }
            } // Dialog definition ends here

            // --- MODIFICATION START: Move key binding setup here ---
            // Add Escape key binding AFTER dialog is created, but before packing/showing
            if (cancelAction) {
                // Ensure dialog is assigned before accessing rootPane
                dialog.rootPane.with { // Now 'dialog' is guaranteed to be non-null
                    getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelProgressAction")
                    getActionMap().put("cancelProgressAction", new AbstractAction() {
                        void actionPerformed(ActionEvent e) {
                            cancelAction()
                        }
                    })
                }
            }
            // --- MODIFICATION END ---

            dialog.pack()
            // Ensure minimum width for better layout
            // --- FIX START: Cast height to int ---
            dialog.minimumSize = new Dimension(300, dialog.preferredSize.height as int)
            // --- FIX END ---
            dialog.setLocationRelativeTo(ui.currentFrame)
            // DO NOT set visible here - the caller (SwingWorker setup) will do it
        }
        return dialog
    }
    // --- REPLACED METHOD END ---
}
