package com.barrymac.freeplane.addons.llm.maps

import com.barrymac.freeplane.addons.llm.exceptions.LlmAddonException
import org.freeplane.core.util.LogUtils
import org.freeplane.features.map.NodeModel
import org.freeplane.features.url.UrlManager

/**
 * Handles node-related operations with proper error handling and logging
 */
//@CompileStatic
class NodeOperations {
    /**
     * Adds analysis content as a branch to a node with proper tagging
     *
     * @param parentNode The node to add the branch to
     * @param content The content for the new branch
     * @param model The LLM model used for generation
     * @param tagger Function to handle node tagging
     * @throws LlmAddonException if the operation fails
     */
    /**
     * Adds analysis content as a branch to a node with proper tagging
     *
     * @param parentNode The node to add the branch to
     * @param analysisMap Optional map to format into content
     * @param content Optional pre-formatted content string
     * @param model The LLM model used
     * @param tagger Function to handle node tagging
     * @param comparisonType Optional type for map formatting
     */
    static void addAnalysisBranch(def parentNode, Map analysisMap = null,
                                  String content = null,
                                  String model,
                                  Closure tagger,
                                  String comparisonType = null) {
        try {
            LogUtils.info("Adding analysis branch to node: ${parentNode.text}")

            // Format map if provided
            String formattedContent = content
            if (analysisMap != null && content == null) {
                formattedContent = formatAnalysisMap(analysisMap, comparisonType)
            } else if (content == null) {
                throw new LlmAddonException("Either analysisMap or content must be provided")
            }

            // Track existing children before adding
            def childrenBefore = parentNode.children.toSet()

            // Add the content as a new branch
            parentNode.appendTextOutlineAsBranch(formattedContent)

            // Find newly added nodes
            def newNodes = parentNode.children.toSet() - childrenBefore

            if (newNodes.isEmpty()) {
                LogUtils.warn("No new nodes created when adding branch to: ${parentNode.text}")
                return
            }

            // Tag all new nodes recursively
            newNodes.each { node ->
                try {
                    tagger.call(node, model)
                    LogUtils.info("Tagged node: ${node.text}")
                } catch (Exception taggingError) {
                    LogUtils.warn("Failed to tag node: ${node.text}: ${taggingError.message}")
                }
            }

        } catch (Exception e) {
            String errorMsg = "Failed to add branch to node: ${parentNode.text}"
            LogUtils.severe("${errorMsg}: ${e.message}")
            throw new LlmAddonException(errorMsg, e)
        }
    }

    /**
     * Formats analysis map into a structured string
     */
    private static String formatAnalysisMap(Map analysisMap, String comparisonType) {
        def builder = new StringBuilder()
        builder.append("${comparisonType}\n")

        analysisMap.each { category, points ->
            builder.append("    ${category}\n")
            points.each { point ->
                builder.append("        - ${point}\n")
            }
        }

        return builder.toString().trim()
    }

    /**
     * Validates node connection structure between two nodes
     *
     * @param node1 First selected node
     * @param node2 Second selected node
     * @return List containing validated nodes in order [source, target]
     * @throws LlmAddonException if validation fails
     */
    List validateConnection(def node1, def node2) {
        try {
            LogUtils.info("Validating connection between ${node1.text} and ${node2.text}")

            def connectors = node1.connectorsOut.findAll { it.target == node2 } +
                    node1.connectorsIn.findAll { it.source == node2 }

            if (connectors.size() != 1) {
                String msg = connectors.isEmpty() ?
                        "No connection found between nodes" :
                        "Multiple connections between nodes"
                throw new LlmAddonException(msg)
            }

            // Return nodes in connection order
            def connector = connectors.first()
            return [connector.source, connector.target]

        } catch (LlmAddonException e) {
            throw e // Re-throw validation errors
        } catch (Exception e) {
            String errorMsg = "Connection validation failed"
            LogUtils.severe("${errorMsg}: ${e.message}")
            throw new LlmAddonException(errorMsg, e)
        }
    }

    /**
     * Formats analysis results into a structured string
     *
     * @param analysisMap Map of analysis categories and points
     * @param comparisonType Type of comparison being made
     * @return Formatted string for node insertion
     */
    static String formatAnalysis(Map<String, List<String>> analysisMap,
                                 String comparisonType) {
        try {
            LogUtils.info("Formatting analysis for ${comparisonType}")

            def builder = new StringBuilder().with {
                append("Comparison (${comparisonType})\n")
                analysisMap.each { category, points ->
                    append("    ${category}\n")
                    points.each { point ->
                        append("        ${point}\n")
                    }
                }
                return it
            }

            return builder.toString().trim()

        } catch (Exception e) {
            LogUtils.warn("Analysis formatting failed: ${e.message}")
            return "Analysis formatting error: ${e.message}"
        }
    }
    
    /**
     * Attaches image bytes to a node
     * @param node The target node
     * @param imageBytes Byte array of the image
     * @param baseName Base filename (without extension)
     * @param extension File extension (png/jpg/etc)
     */
    static void attachImageToNode(def node, byte[] imageBytes, 
                                String baseName, String extension) {
        try {
            LogUtils.info("Attaching image to node: ${node.text}")
            
            // Debug node capabilities
            LogUtils.info("Node class: ${node.getClass().name}")
            LogUtils.info("Node methods: ${node.metaClass.methods*.name.unique().sort()}")
            LogUtils.info("Node properties: ${node.metaClass.properties*.name.sort()}")

            // 1. Create unique filename
            String safeName = baseName.replaceAll(/[^\w\-]/, '_')
            String timestamp = new Date().format('yyyyMMddHHmmss')
            String fileName = "${safeName}_${timestamp}.${extension}"
            
            // 2. Save image to map's directory
            def mapFile = node.map.file
            if (!mapFile) {
                throw new LlmAddonException("Map must be saved before adding images")
            }
            
            File imageFile = new File(mapFile.parentFile, fileName)
            imageFile.bytes = imageBytes
            
            // 3. Try different attachment approaches
            def uri = imageFile.toURI()
            LogUtils.info("Attempting to attach URI: ${uri}")
            
            // Approach 1: Set URI directly if possible
            if (node.metaClass.hasProperty(node, 'externalObject')) {
                node.externalObject = uri.toString()
                LogUtils.info("Set externalObject property directly")
            }
            // Approach 2: Use node attributes
            else if (node.metaClass.respondsTo(node, 'setAttribute', [String, String] as Class[])) {
                node.setAttribute('externalObject', uri.toString())
                LogUtils.info("Set externalObject attribute")
            }
            // Approach 3: Use Freeplane's core API
            else {
                LogUtils.info("Falling back to core API methods")
                try {
                    // Try to use NodeLinks API if available
                    def nodeLinks = node.getExtension(org.freeplane.features.link.NodeLinks.class)
                    if (nodeLinks) {
                        nodeLinks.setHyperLink(uri)
                        LogUtils.info("Set hyperlink via NodeLinks extension")
                    } else {
                        // Last resort - try to use controller to set link
                        def linkController = org.freeplane.features.link.LinkController.getController()
                        linkController.setLink(node, uri, org.freeplane.features.link.LinkController.LINK_ABSOLUTE)
                        LogUtils.info("Set link via LinkController")
                    }
                } catch (Exception e) {
                    LogUtils.warn("Core API approach failed: ${e.message}")
                    // Try direct field access as last resort
                    node.setObject(uri.toString())
                    LogUtils.info("Set object directly")
                }
            }
            
            LogUtils.info("Image attached: ${fileName}")
        } catch (Exception e) {
            String errorMsg = "Failed to attach image to node"
            LogUtils.severe("${errorMsg}: ${e.message}")
            throw new LlmAddonException(errorMsg, e)
        }
    }
}
