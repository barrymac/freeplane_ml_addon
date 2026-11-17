package com.barrymac.freeplane.addons.llm.neuros

import org.freeplane.core.util.LogUtils
import com.barrymac.freeplane.addons.llm.ui.UiHelper

import java.awt.Desktop

/**
 * Shared implementation for the top-menu and node-context-menu browse actions.
 * The catalog is lazy: invoking on a surface/type/load-more node expands one
 * bounded page; invoking elsewhere creates the catalog metadata.
 */
class NeurosBrowseAction {

    static void run(def c, def ui) {
        LogUtils.info('NeurosBrowse action started.')
        try {
            def node = c.selected
            if (node == null) {
                UiHelper.showInformationMessage(ui, 'Please select a node first.')
                return
            }

            KnowledgeContext knowledgeContext = KnowledgeContext.discover(node.map.file)
            if (!knowledgeContext.isEnabled()) {
                UiHelper.showInformationMessage(ui, knowledgeContext.reason)
                return
            }
            NeurosLimits limits = NeurosLimits.load(node.map.storage)
            KnowledgeSource source = knowledgeContext.source
            def storage = node.map.storage

            if (hasTag(node, 'neurOS result')) {
                openResultSource(node, ui)
                return
            }
            if (hasTag(node, 'neurOS content-type') || hasTag(node, 'neurOS door-capability')) {
                UiHelper.showInformationMessage(ui,
                        node.note ?: 'This is metadata advertised by the knowledge source.')
                return
            }
            if (hasTag(node, 'neurOS load-more')) {
                expandListing(node, source, limits, storage, true, ui)
                return
            }
            if (hasTag(node, 'neurOS surface') || hasTag(node, 'neurOS type')) {
                expandListing(node, source, limits, storage, false, ui)
                return
            }
            if (hasTag(node, 'neurOS knowledge')) {
                UiHelper.showInformationMessage(ui,
                        'This knowledge catalog is already open. Select a surface or type to expand it.')
                return
            }

            createCatalog(node, source)
            UiHelper.showInformationMessage(ui,
                    'Knowledge catalog created. Select a surface or type and choose ' +
                            'Browse neurOS Knowledge again to expand it.')

        } catch (Exception e) {
            LogUtils.severe("Error in NeurosBrowse action: ${e.message}", e)
            UiHelper.showErrorMessage(ui, "Browse neurOS Knowledge Error: ${e.message}")
        }
    }

    /** Open a result's source instead of recursively creating another catalog. */
    private static void openResultSource(def node, def ui) {
        File source = node.link?.file
        if (source == null) {
            UiHelper.showInformationMessage(ui,
                    'This knowledge entry has no source link. Try opening its parent listing.')
            return
        }
        try {
            if (Desktop.isDesktopSupported() &&
                    Desktop.desktop.isSupported(Desktop.Action.BROWSE)) {
                Desktop.desktop.browse(source.toURI())
            } else {
                UiHelper.showInformationMessage(ui,
                        'Open the source using the link on this node.')
            }
        } catch (Exception problem) {
            LogUtils.warn("Could not open neurOS source material: ${problem.message}")
            UiHelper.showErrorMessage(ui,
                    'The source could not be opened right now. The knowledge source may be busy.')
        }
    }

    /** Create only lazy catalog metadata; no instance listings are fetched. */
    private static void createCatalog(def node, KnowledgeSource source) {
        KnowledgeCatalog catalog = KnowledgeCatalog.read(source)
        def root = node.createChild("neurOS knowledge: ${source.scope}")
        root.tags.add('neurOS knowledge')
        root.note = "Knowledge catalog for ${source.description()}\n" +
                'Select a surface or type and choose Browse neurOS Knowledge to expand it.'

        if (catalog.surfaces) {
            def surfaces = root.createChild('Knowledge surfaces')
            catalog.surfaces.each { surface ->
                def child = surfaces.createChild(surface.displayName())
                child.tags.add('neurOS surface')
                child.note = surface.description +
                        '\n\nSelect this node and choose Browse neurOS Knowledge to list entries.'
                child.link.file = source.path(surface.name)
            }
        }

        if (catalog.knowledgeTypes) {
            def types = root.createChild('Knowledge types')
            catalog.knowledgeTypes.each { type ->
                def child = types.createChild(type)
                child.tags.add('neurOS type')
                child.note = "Knowledge type: ${type}\n" +
                        'Select this node and choose Browse neurOS Knowledge to list entries.'
                child.link.file = source.path("by-tag/${type}")
            }
        }

        if (catalog.doorCapabilities) {
            def doors = root.createChild('Doors')
            catalog.doorCapabilities.each { capability ->
                def child = doors.createChild(capability)
                child.tags.add('neurOS door-capability')
                child.note = "Door capability advertised by this knowledge source: ${capability}"
            }
        }

        if (catalog.contentTypes) {
            def content = root.createChild('Supported content')
            catalog.contentTypes.each { contentType ->
                def child = content.createChild(contentType.displayName())
                child.tags.add('neurOS content-type')
                child.note = contentType.description()
            }
        }

        if (catalog.warnings) {
            root.note += '\n\nNotes: ' + catalog.warnings.join(' ')
        }
    }

    /** Expand one bounded page, or append the next page to the same container. */
    private static void expandListing(def selected, KnowledgeSource source,
                                      NeurosLimits limits, def storage,
                                      boolean loadMore, def ui) {
        def container = loadMore ? selected.parent : selected
        File directory = selected.link?.file
        if (directory == null) {
            UiHelper.showInformationMessage(ui, 'This branch does not have a knowledge listing.')
            return
        }
        if (!loadMore && (container.children ?: []).size() > 0) {
            // Refresh only generated entries. User-created children and other
            // metadata are preserved; no Freeplane restart is needed to see
            // newly ingested source material.
            container.children.findAll {
                hasTag(it, 'neurOS result') || hasTag(it, 'neurOS load-more')
            }.each { it.delete() }
        }

        String relativePath = relativeListingPath(directory, source)
        LogUtils.info("NeurosBrowse: refreshing ${relativePath}")
        int offset = loadMore ? intValue(storage[offsetKey(selected)]) : 0
        String subtype = relativePath.startsWith('by-tag/') ? relativePath.split('/')[1] : ''
        KnowledgePage page = KnowledgeListing.list(source, relativePath, offset,
                limits.browseLimit, subtype)
        int rawEntryCount = page.entries.size()
        page.entries = StateMetadataFilter.apply(source, relativePath, page.entries)

        page.entries.each { entry ->
            def entryNode = container.createChild(entry.displayName)
            entryNode.tags.add('neurOS result')
            if (entry.state) entryNode.tags.add('neurOS work-item')
            entryNode.note = (entry.state ? "State: ${entry.state}\n" : '') +
                    (entry.subtype ? "Type: ${entry.subtype}\n" : '') +
                    'Use the link to open the source material.'
            entryNode.link.file = entry.sourceFile(source)
        }

        if (loadMore) {
            selected.delete()
        }

        if (page.entries.isEmpty() && offset == 0) {
            String message = rawEntryCount > 0 ?
                    'No state-tagged work items were found on this page.' :
                    'There are no entries on this knowledge surface.'
            UiHelper.showInformationMessage(ui, message)
            return
        }

        if (page.hasMore) {
            def more = container.createChild('Load more entries…')
            more.tags.add('neurOS load-more')
            more.note = 'More entries are available. Select this node and choose ' +
                    'Browse neurOS Knowledge again.'
            more.link.file = directory
            storage[offsetKey(more)] = Integer.toString(page.nextOffset)
        } else if (page.sourceTruncated) {
            container.note = (container.note ? container.note + '\n\n' : '') +
                    'The source has more entries than this surface currently exposes.'
        }
    }

    /** Resolve the listing directory relative to the current knowledge scope. */
    private static String relativeListingPath(File directory, KnowledgeSource source) {
        String root = source.path('').absoluteFile.path
        String actual = directory.absoluteFile.path
        String prefix = root.endsWith(File.separator) ? root : root + File.separator
        if (!actual.startsWith(prefix)) {
            throw new IOException('This listing is outside the current map knowledge scope.')
        }
        return actual.substring(prefix.length())
    }

    private static boolean hasTag(def node, String tag) {
        return node?.tags?.contains(tag)
    }

    private static String offsetKey(def node) {
        return "neuros.browse.offset.${node.id}"
    }

    private static int intValue(Object value) {
        if (value == null || value.toString().trim().isEmpty()) return 0
        try {
            return Integer.parseInt(value.toString())
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException('The saved browse position is invalid.')
        }
    }
}
