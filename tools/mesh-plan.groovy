// Dry run of "Draw neurOS Relationships" without Freeplane.
//
//   tools/mesh-plan.sh <entry.card.md|diagram.puml> <scope-directory>
//
// Prints the relationships the entry declares, what each one resolves to in the
// scope, and what cannot be resolved. This is the repeatable debugging loop for
// the drawing: parsing, resolution and vocabulary are visible as text, so a
// wrong title or a missing target is obvious without opening a map.
def indexClass = Class.forName('com.barrymac.freeplane.addons.llm.neuros.MeshIndex')
def markersClass = Class.forName('com.barrymac.freeplane.addons.llm.neuros.MeshMarkers')
def pumlClass = Class.forName('com.barrymac.freeplane.addons.llm.neuros.PumlMeshReader')
def edgeClass = Class.forName('com.barrymac.freeplane.addons.llm.neuros.MeshEdge')

if (args.length < 2) {
    System.err.println('usage: mesh-plan.sh <entry> <scope-directory>')
    System.exit(2)
}
File entry = new File(args[0])
File scope = new File(args[1])
if (!entry.isFile()) { System.err.println("no such entry: ${entry}"); System.exit(2) }
if (!scope.isDirectory()) { System.err.println("no such scope: ${scope}"); System.exit(2) }

def index = indexClass.getMethod('load', File, int).invoke(null, scope, 400)
def seed = indexClass.getMethod('nodeFor', File).invoke(null, entry)
def text = entry.getText('UTF-8')
def edges = entry.name.endsWith('.puml') ?
        pumlClass.getMethod('read', String, int).invoke(null, text, 200) :
        markersClass.getMethod('read', String, String).invoke(null, text, seed.id)

println "entry : ${seed.title}"
println "scope : ${scope} (${index.entries.size()} entries indexed)"
println "edges : ${edges.size()}"
println ''
int resolved = 0
int unresolved = 0
edges.each { edge ->
    def target = indexClass.getMethod('find', String).invoke(index, edge.target)
    if (target == null) {
        unresolved++
        println String.format('  %-14s %-8s -> OUTSIDE SCOPE', edge.label, edge.declared ? '' : '(emergent)')
    } else {
        resolved++
        def source = indexClass.getMethod('find', String).invoke(index, edge.source)
        println String.format('  %-14s %-8s %s -> %s',
                edge.label, edge.declared ? '' : '(emergent)',
                source?.title ?: 'this entry', target.title)
    }
}
println ''
println "resolved ${resolved}, outside scope ${unresolved}"