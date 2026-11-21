package com.barrymac.freeplane.addons.llm.neuros

import java.util.regex.Pattern

/**
 * One sitter-structure-v1 rendition, as the substrate exposes it on the
 * sitter-structure-v1 surface.
 *
 * The body is markdown with a strict shape:
 *   # structure: <file>
 *   lang=&lt;lang&gt; lane=&lt;lane&gt; errors=&lt;n&gt; decls=&lt;n&gt; imports=&lt;n&gt; resolved=&lt;n&gt;
 *   ## imports
 *   - &lt;specifier&gt; [&lt;names&gt;] (unresolved)
 *   ## &lt;kind&gt; &lt;name&gt; &lt;startLine:startCol-endLine:endCol&gt;
 *   sig: &lt;signature&gt;
 *
 * The parser is deliberately total: unknown lines are ignored, missing parts
 * leave empty collections, and a degraded rendition (partial or errors&gt;0)
 * still parses — the renderer shows it with a note, never hides it.
 */
class CodeRendition {

    final String uuid
    String sourceSha256
    String file = ''
    String lang = ''
    String lane = ''
    int errors = 0
    boolean partial = false
    int resolvedImports = -1
    final List<ImportRef> imports = []
    final List<Declaration> declarations = []

    CodeRendition(String uuid, String sourceSha256) {
        this.uuid = uuid
        this.sourceSha256 = sourceSha256
    }

    boolean isDegraded() {
        return partial || errors > 0
    }

    static CodeRendition parse(String uuid, String body) {
        if (!uuid?.trim()) throw new IllegalArgumentException('A rendition needs its uuid.')
        if (body == null) throw new IllegalArgumentException('A rendition needs its body.')
        CodeRendition rendition = new CodeRendition(uuid, null)
        Pattern shaLine = ~/^source_sha256:\s*(\S+)\s*$/
        Pattern meta = ~/^lang=(\S+)\s+lane=(\S+)\s+errors=(\d+)\s+decls=(\d+)\s+imports=(\d+)\s+resolved=(\d+)\s*$/
        Pattern heading = ~/^#\s+structure:\s+(.+)$/
        Pattern declaration = ~/^##\s+(\S+)\s+(\S+)\s+(\d+:\d+-\d+:\d+)\s*$/
        boolean inImports = false
        Declaration openDeclaration = null
        body.readLines().each { raw ->
            String line = raw.trim()
            def shaMatch = shaLine.matcher(line)
            if (shaMatch.matches()) {
                rendition.sourceSha256 = shaMatch.group(1)
                return
            }
            def fileMatch = heading.matcher(line)
            if (fileMatch.matches()) {
                rendition.file = fileMatch.group(1).trim()
                return
            }
            def metaMatch = meta.matcher(line)
            if (metaMatch.matches()) {
                rendition.lang = metaMatch.group(1)
                rendition.lane = metaMatch.group(2)
                rendition.errors = Integer.parseInt(metaMatch.group(3))
                rendition.resolvedImports = Integer.parseInt(metaMatch.group(6))
                return
            }
            if (line ==~ /##\s+imports.*/) {
                inImports = true
                openDeclaration = null
                return
            }
            if (line.startsWith('## ')) {
                inImports = false
                def declMatch = declaration.matcher(line)
                if (declMatch.matches()) {
                    openDeclaration = new Declaration(
                            kind: declMatch.group(1), name: declMatch.group(2),
                            range: declMatch.group(3))
                    rendition.declarations << openDeclaration
                } else {
                    openDeclaration = null
                }
                return
            }
            if (openDeclaration != null && line.startsWith('sig:')) {
                openDeclaration.signature = line.substring(4).trim()
                return
            }
            if (inImports && line.startsWith('- ')) {
                rendition.imports << ImportRef.parse(line.substring(2))
            }
        }
        rendition.partial = body.readLines().any { it.trim() == 'partial: true' }
        return rendition
    }

    /** A file's visible title: the basename with its extension. */
    String title() {
        return file ?: (uuid?.take(8) ?: 'source file')
    }

    static class ImportRef {
        final String specifier
        final String names
        final boolean resolved

        ImportRef(String specifier, String names, boolean resolved) {
            this.specifier = specifier
            this.names = names ?: ''
            this.resolved = resolved
        }

        static ImportRef parse(String text) {
            boolean resolved = !text.endsWith('(unresolved)')
            String cleaned = resolved ? text : text.substring(0, text.length() - '(unresolved)'.length()).trim()
            def matcher = (cleaned =~ /^(.*?)\s*\[(.*)]\s*$/)
            if (matcher.matches()) {
                return new ImportRef(matcher.group(1).trim(), matcher.group(2).trim(), resolved)
            }
            return new ImportRef(cleaned.trim(), '', resolved)
        }
    }

    static class Declaration {
        String kind
        String name
        String range
        String signature = ''

        /** The map-visible text: kind and name, the two human parts. */
        String title() {
            return "${kind} ${name}"
        }

        /** Where this lives in the source, in words a person reads. */
        String locationNote() {
            List<String> parts = []
            if (signature) parts << "signature: ${signature}"
            if (range) parts << "source: lines ${range.replace(':', ' col ')}"
            return parts.join('\n')
        }
    }
}
