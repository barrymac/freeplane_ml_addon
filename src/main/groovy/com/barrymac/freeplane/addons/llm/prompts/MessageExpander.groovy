package com.barrymac.freeplane.addons.llm.prompts

import groovy.text.SimpleTemplateEngine
import org.freeplane.core.util.LogUtils
import org.freeplane.plugin.script.proxy.NodeProxy

class MessageExpander {
    static Map createBinding(NodeProxy node, NodeProxy otherNode,
                             String dimension, String pole1, String pole2) {
        try {
            def pathToRoot = node.pathToRoot
            def rootText = node.mindMap.root.text
            pathToRoot = pathToRoot.take(pathToRoot.size() - 1)

            [
                    rootText            : rootText,
                    nodeContent         : node.plainText,
                    otherNodeContent    : otherNode?.plainText ?: "",
                    ancestorContents    : pathToRoot*.plainText.join('\n'),
                    siblingContents     : node.isRoot() ? '' :
                            node.parent.children.findAll { it != node }*.plainText.join('\n'),
                    comparativeDimension: dimension,
                    pole1               : pole1,
                    pole2               : pole2
            ]
        } catch (Exception e) {
            LogUtils.severe("Error creating binding map: ${e.message}")
            return [
                    nodeContent         : node?.plainText ?: "",
                    otherNodeContent    : otherNode?.plainText ?: "",
                    comparativeDimension: dimension,
                    pole1               : pole1,
                    pole2               : pole2
            ]
        }
    }

    static String expandTemplate(String template, Map binding) {
        try {
            new SimpleTemplateEngine()
                    .createTemplate(template)
                    .make(binding)
                    .toString()
        } catch (Exception e) {
            LogUtils.severe("Error expanding template: ${e.message}")
            return template
        }
    }
}
