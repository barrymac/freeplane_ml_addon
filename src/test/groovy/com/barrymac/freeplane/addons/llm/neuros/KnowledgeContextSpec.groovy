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

    def "a direct knowledge surface never becomes a write destination"() {
        given:
        File surface = knowledgeSurface(
                new File(root, 'global/scopes/single-scope'), 'single-scope')
        File map = mapIn(new File(surface, 'nested'))

        when:
        def context = KnowledgeContext.discover(map)

        then:
        context.enabled
        context.source.scope == 'single-scope'
        !context.canWrite()
        context.writeDestination == null
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

    def "a separated mount reads through KNOWLEDGE and writes to its watched workspace"() {
        given:
        File mount = new File(root, 'mount')
        File surfaces = knowledgeSurface(new File(mount, 'KNOWLEDGE'), 'ninep-scope')
        File watched = new File(mount, 'workspace')
        watched.mkdirs()
        File workspace = new File(root, 'workspace')
        workspace.mkdirs()
        Files.createSymbolicLink(new File(workspace, 'LEGAL_KNOWLEDGE').toPath(), mount.toPath())
        File map = mapIn(workspace)

        when:
        def context = KnowledgeContext.discover(map)

        then:
        context.enabled
        context.source.scope == 'ninep-scope'
        // Reads resolve against the KNOWLEDGE surfaces, not the mount root
        context.source.path('').path == surfaces.path
        // Writes land in the mount's watched workspace so the substrate ingests them
        context.canWrite()
        context.writeDestination == watched.path
    }

    def "a map saved inside a separated mount's workspace writes where it lives"() {
        given:
        File mount = new File(root, 'mount')
        knowledgeSurface(new File(mount, 'KNOWLEDGE'), 'ninep-scope')
        File watched = new File(mount, 'workspace')
        File map = mapIn(watched)

        when:
        def context = KnowledgeContext.discover(map)

        then:
        context.enabled
        context.source.scope == 'ninep-scope'
        context.canWrite()
        context.writeDestination == watched.path
    }

    def "a separated mount without a watched workspace falls back to the map's workspace"() {
        given:
        File mount = new File(root, 'mount')
        knowledgeSurface(new File(mount, 'KNOWLEDGE'), 'ninep-scope')
        File workspace = new File(root, 'workspace')
        workspace.mkdirs()
        Files.createSymbolicLink(new File(workspace, 'LEGAL_KNOWLEDGE').toPath(), mount.toPath())
        File map = mapIn(workspace)

        when:
        def context = KnowledgeContext.discover(map)

        then:
        context.enabled
        context.source.scope == 'ninep-scope'
        context.source.path('').path.endsWith('KNOWLEDGE')
        context.canWrite()
        context.writeDestination == workspace.path
    }

    def "a KNOWLEDGE directory without a cartridge is not a knowledge source"() {
        given:
        File mount = new File(root, 'mount')
        File nested = new File(mount, 'KNOWLEDGE')
        nested.mkdirs()
        new File(nested, 'by-query').mkdirs()
        new File(nested, 'watcher-status').text = 'scope: ghost-scope\n'
        File workspace = new File(root, 'workspace')
        workspace.mkdirs()
        Files.createSymbolicLink(new File(workspace, 'LEGAL_KNOWLEDGE').toPath(), mount.toPath())
        File map = mapIn(workspace)

        when:
        def context = KnowledgeContext.discover(map)

        then:
        !context.enabled
        context.reason.contains('not ready yet')
    }

    def "a map nested deeper inside the watched workspace gets no write destination"() {
        given:
        File mount = new File(root, 'mount')
        knowledgeSurface(new File(mount, 'KNOWLEDGE'), 'ninep-scope')
        new File(mount, 'workspace').mkdirs()
        File map = mapIn(new File(mount, 'workspace/deep'))

        when:
        def context = KnowledgeContext.discover(map)

        then:
        context.enabled
        context.source.scope == 'ninep-scope'
        !context.canWrite()
        context.writeDestination == null
    }

    def "a map on the read surfaces of a separated mount cannot write"() {
        given:
        File mount = new File(root, 'mount')
        File surfaces = knowledgeSurface(new File(mount, 'KNOWLEDGE'), 'ninep-scope')
        new File(mount, 'workspace').mkdirs()
        File map = mapIn(new File(surfaces, 'nested'))

        when:
        def context = KnowledgeContext.discover(map)

        then:
        context.enabled
        context.source.scope == 'ninep-scope'
        !context.canWrite()
        context.writeDestination == null
    }

    def "root-level surfaces win over a nested KNOWLEDGE directory when both exist"() {
        given:
        File mount = knowledgeSurface(new File(root, 'mount'), 'root-scope')
        knowledgeSurface(new File(mount, 'KNOWLEDGE'), 'nested-scope')
        new File(mount, 'workspace').mkdirs()
        File map = mapIn(new File(mount, 'workspace'))

        when:
        def context = KnowledgeContext.discover(map)

        then:
        context.enabled
        context.source.scope == 'root-scope'
        context.source.path('').path == mount.path
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

    def "a contract-only knowledge surface is recognized during cartridge warmup"() {
        given:
        File surface = new File(root, 'warming')
        surface.mkdirs()
        new File(surface, 'by-query').mkdirs()
        new File(surface, 'cartridge-contract').text = '{}'
        File map = mapIn(surface)

        when:
        def context = KnowledgeContext.discover(map)

        then:
        !context.enabled
        context.reason.contains('not ready')
        !context.reason.contains('not enabled for this map')
    }

    def "the mount attachment name supplies scope while watcher status is unavailable"() {
        given:
        String mounts = '773 33 0:144 / /mnt/knowledge/example rw,relatime ' +
                '- 9p 192.0.2.10 rw,uname=tester,aname=example-scope,access=client\n'
        File directory = new File('/mnt/knowledge/example')

        expect:
        KnowledgeContext.scopeFromMountInfo(mounts, directory) == 'example-scope'
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
