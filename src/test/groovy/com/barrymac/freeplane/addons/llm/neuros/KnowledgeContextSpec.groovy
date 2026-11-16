package com.barrymac.freeplane.addons.llm.neuros

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

import java.nio.file.Files

@Subject(KnowledgeContext)
class KnowledgeContextSpec extends Specification {

    @TempDir
    File root

    private File knowledgeSurface(File directory, String scope) {
        directory.mkdirs()
        new File(directory, 'README.cartridge').text = '# cartridge\n'
        new File(directory, 'by-query').mkdirs()
        new File(directory, 'watcher-status').text = "status: OK\nscope: ${scope}\n"
        return directory
    }

    private File mapIn(File directory) {
        directory.mkdirs()
        File map = new File(directory, 'map.mm')
        map.text = '<map version="freeplane 1.12.1"><node TEXT="root"/></map>'
        return map
    }

    def "an unsaved map is simply told neurOS is not enabled"() {
        when:
        def context = KnowledgeContext.discover(null)

        then:
        !context.enabled
        context.reason.contains('Save it in a neurOS-enabled')
    }

    def "a map in a plain directory has no neurOS configuration to perform"() {
        given:
        File map = mapIn(root)

        when:
        def context = KnowledgeContext.discover(map)

        then:
        !context.enabled
        context.reason.contains('not enabled for this map')
    }

    def "a map inside a scoped knowledge surface discovers its own scope"() {
        given:
        File surface = knowledgeSurface(new File(root, 'scoped'), 'chosen-scope')
        File map = mapIn(new File(surface, 'nested'))

        when:
        def context = KnowledgeContext.discover(map)

        then:
        context.enabled
        context.source.scope == 'chosen-scope'
        !context.canWrite()
        context.writeDestination == null
    }

    def "a map inside a global single-surface scope gets a writable destination"() {
        given:
        File surface = knowledgeSurface(
                new File(root, 'global/scopes/single-scope'), 'single-scope')
        File map = mapIn(new File(surface, 'nested'))

        when:
        def context = KnowledgeContext.discover(map)

        then:
        context.enabled
        context.source.scope == 'single-scope'
        context.canWrite()
        context.writeDestination == surface.path
    }

    def "a watched workspace is discovered through any cartridge knowledge link"() {
        given:
        File target = knowledgeSurface(new File(root, 'target'), 'workspace-scope')
        File workspace = new File(root, 'workspace')
        workspace.mkdirs()
        Files.createSymbolicLink(new File(workspace, 'ANY_KNOWLEDGE').toPath(), target.toPath())
        File map = mapIn(new File(workspace, 'deep/subdir'))

        when:
        def context = KnowledgeContext.discover(map)

        then:
        context.enabled
        context.source.scope == 'workspace-scope'
        context.canWrite()
        context.writeDestination == workspace.path
    }

    def "a workspace link without a cartridge README does not enable neurOS"() {
        given:
        File target = new File(root, 'not-a-knowledge-source')
        target.mkdirs()
        new File(target, 'by-query').mkdirs()
        new File(target, 'watcher-status').text = 'scope: missing-cartridge\n'
        File workspace = new File(root, 'workspace')
        workspace.mkdirs()
        Files.createSymbolicLink(new File(workspace, 'SOFTWARE_KNOWLEDGE').toPath(), target.toPath())
        File map = mapIn(workspace)

        when:
        def context = KnowledgeContext.discover(map)

        then:
        !context.enabled
        context.reason.contains('not ready')
    }

    def "multiple valid knowledge sources are refused instead of guessed"() {
        given:
        File first = knowledgeSurface(new File(root, 'first'), 'first')
        File second = knowledgeSurface(new File(root, 'second'), 'second')
        File workspace = new File(root, 'workspace')
        workspace.mkdirs()
        Files.createSymbolicLink(new File(workspace, 'FIRST_KNOWLEDGE').toPath(), first.toPath())
        Files.createSymbolicLink(new File(workspace, 'SECOND_KNOWLEDGE').toPath(), second.toPath())
        File map = mapIn(workspace)

        when:
        def context = KnowledgeContext.discover(map)

        then:
        !context.enabled
        context.reason.contains('more than one')
    }

    def "a scoped writable network surface is detected from its mount, not its scope layout"() {
        given:
        String mounts = '36 29 0:33 / /mnt/neuros-9p-mss1/freeplane rw,relatime ' +
                '- 9p 192.168.8.150 rw,trans=tcp\n'
        File directory = new File('/mnt/neuros-9p-mss1/freeplane')

        expect:
        KnowledgeContext.isNetworkSurface(mounts, directory)
    }

    def "a scoped FUSE surface is not mistaken for a writable network workspace"() {
        given:
        String mounts = '36 29 0:33 / /mnt/neuros/software/example rw,nosuid ' +
                '- fuse /dev/fuse rw\n'
        File directory = new File('/mnt/neuros/software/example')

        expect:
        !KnowledgeContext.isNetworkSurface(mounts, directory)
    }

    def "mount detection uses the longest matching mount point and decodes escaped spaces"() {
        given:
        String mounts = '36 29 0:33 / /mnt/knowledge\\040space rw - 9p server rw\n' +
                '37 29 0:34 / /mnt/knowledge\\040space/scope rw - fuse /dev/fuse rw\n'
        File directory = new File('/mnt/knowledge space/scope')

        expect:
        !KnowledgeContext.isNetworkSurface(mounts, directory)
    }

    def "a dangling knowledge link reports that the source is not ready"() {
        given:
        File workspace = new File(root, 'workspace')
        workspace.mkdirs()
        Files.createSymbolicLink(new File(workspace, 'SOFTWARE_KNOWLEDGE').toPath(),
                new File(root, 'does-not-exist').toPath())
        File map = mapIn(workspace)

        when:
        def context = KnowledgeContext.discover(map)

        then:
        !context.enabled
        context.reason.contains('not ready')
    }
}
