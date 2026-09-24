/** Read-only circuit projections reference canonical graph edges; bundled metadata never drives dynamics. */
package org.simbrain.custom_sims.simulations.neuroscience

import java.io.DataInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

class FlyCircuitViewState {
    var open = false
    var selectedId = MN9_ID
    var mode = "Anatomy"
    var projection = "XY"
    var allEdges = false
    var context = true
    var displayedIds = LongArray(0)
    var zoom = 1.0
    var panX = 0.0
    var panY = 0.0
    fun copy() = FlyCircuitViewState().also {
        it.open = open; it.selectedId = selectedId; it.mode = mode; it.projection = projection
        it.allEdges = allEdges; it.context = context; it.displayedIds = displayedIds.copyOf()
        it.zoom = zoom; it.panX = panX; it.panY = panY
    }
}

data class FlyCircuitConnection(val source: Int, val target: Int, val edge: Int, val contacts: Int?)
data class FlySkeleton(val points: FloatArray, val segments: IntArray)
data class FlyCircuitSnapshot(val tick: Long, val voltage: DoubleArray, val counts: LongArray)

class FlyCircuit private constructor(val graph: FlyConnectome) {
    val members = linkedSetOf<Int>()
    val annotations = ConcurrentHashMap<Int, FlyAnnotation>()
    val aliases = mutableMapOf<Int, String>()
    val skeletons = mutableMapOf<Int, FlySkeleton>()
    var contextPoints = FloatArray(0)
        private set
    var anatomyStatus = "Anatomy unavailable"
        private set
    private val contacts = HashMap<Long, Int>()
    private val presetIncoming = HashMap<Int, IntArray>()
    private val extraIncoming = object : LinkedHashMap<Int, IntArray>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, IntArray>?) = size > 32
    }

    fun name(index: Int): String = aliases[index] ?: annotations[index]?.value("cell_type") ?: "unassigned"
    fun label(index: Int): String = "${name(index)} · ${annotations[index]?.value("side") ?: "unassigned"} · ${graph.ids[index]}"

    private fun pair(source: Int, target: Int) = (source.toLong() shl 32) or target.toLong()

    fun sourceOf(edge: Int): Int {
        require(edge in 0 until graph.edges)
        var low = 0
        var high = graph.size
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (graph.offsets[mid] <= edge) low = mid else high = mid - 1
        }
        return low
    }

    private fun connection(source: Int, edge: Int) = FlyCircuitConnection(
        source, graph.targets[edge], edge, contacts[pair(source, graph.targets[edge])])

    /** Potential full-graph scans belong on a worker; cached data are bounded to 32 external selections. */
    @Synchronized
    fun incoming(index: Int): List<FlyCircuitConnection> {
        require(index in 0 until graph.size)
        val edges = presetIncoming[index] ?: extraIncoming.getOrPut(index) {
            val found = ArrayList<Int>()
            for (e in graph.targets.indices) {
                if (e % 65536 == 0 && Thread.currentThread().isInterrupted) throw InterruptedException()
                if (graph.targets[e] == index) found.add(e)
            }
            found.toIntArray()
        }
        return edges.map { connection(sourceOf(it), it) }
    }

    fun outgoing(index: Int): List<FlyCircuitConnection> =
        (graph.offsets[index] until graph.offsets[index + 1]).map { connection(index, it) }

    fun internalConnections(displayed: Set<Int> = members): List<FlyCircuitConnection> =
        displayed.flatMap { outgoing(it).filter { c -> c.target in displayed } }

    fun snapshot(state: FlyBrainState, indices: IntArray): FlyCircuitSnapshot = synchronized(state) {
        FlyCircuitSnapshot(state.tick, DoubleArray(indices.size) { state.voltage[indices[it]] },
            LongArray(indices.size) { state.counts[indices[it]] })
    }

    companion object {
        const val DIRECTORY = "simulations/data/flybrain"
        const val DATA_HASH = "9485d6aa3b97164c1925de0898f2833b575b8c84ff352692930ebede677a3bfe"
        const val SKELETON_HASH = "16d67a22b63a417e616d98125c3ab83fd3ce1fe1e638f60ca18017b684c9e65d"

        private fun checkedZip(file: File, expected: String): ZipFile {
            require(file.isFile) { "Circuit asset unavailable: ${file.name}. Install the feeding circuit data bundle." }
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(65536)
                while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            require(digest.digest().joinToString("") { "%02x".format(it) } == expected) {
                "Circuit asset differs from its pinned checksum: ${file.name}"
            }
            return ZipFile(file)
        }

        fun load(graph: FlyConnectome, directory: File = File(DIRECTORY)): FlyCircuit {
            val result = FlyCircuit(graph)
            checkedZip(File(directory, "feeding-circuit.zip"), DATA_HASH).use { zip ->
                zip.getInputStream(zip.getEntry("neurons.tsv")).bufferedReader().use { reader ->
                    val header = reader.readLine().split('\t')
                    reader.forEachLine { line ->
                        val values = line.split('\t')
                        require(values.size == header.size)
                        val fields = header.zip(values).toMap()
                        val id = fields.getValue("root_id").toLong()
                        val index = graph.index(id)
                        result.annotations[index] = FlyAnnotation(id, fields)
                        if (fields.getValue("in_preset") == "1") {
                            result.members.add(index)
                            result.aliases[index] = fields.getValue("alias_group")
                        }
                    }
                }
                require(result.members.size == 43)
                zip.getInputStream(zip.getEntry("edges.tsv")).bufferedReader().use { reader ->
                    reader.readLine()
                    reader.forEachLine { line ->
                        val row = line.split('\t')
                        val source = graph.index(row[0].toLong()); val target = graph.index(row[1].toLong())
                        require(result.contacts.put(result.pair(source, target), row[2].toInt()) == null)
                    }
                }
                DataInputStream(zip.getInputStream(zip.getEntry("context.bin"))).use { input ->
                    require(input.readInt() == graph.size)
                    result.contextPoints = FloatArray(graph.size * 3) { input.readFloat().also { require(it.isFinite()) } }
                    require(input.read() == -1)
                }
            }
            val incoming = result.members.associateWith { ArrayList<Int>() }
            val seen = HashSet<Long>()
            var internal = 0; var entering = 0; var leaving = 0
            val isMember = BooleanArray(graph.size) { it in result.members }
            for (source in 0 until graph.size) {
                val sourceMember = isMember[source]
                if (source % 1024 == 0 && Thread.currentThread().isInterrupted) throw InterruptedException()
                for (edge in graph.offsets[source] until graph.offsets[source + 1]) {
                    val target = graph.targets[edge]
                    if (!sourceMember && !isMember[target]) continue
                    val key = result.pair(source, target)
                    require(seen.add(key)) { "Duplicate incident edge" }
                    val count = result.contacts[key] ?: error("Incident contact metadata missing")
                    require(count > 0 && kotlin.math.abs(graph.weights[edge]) == count * .275) {
                        "Circuit contacts disagree with canonical weight at edge $edge"
                    }
                    incoming[target]?.add(edge)
                    when {
                        source in result.members && target in result.members -> internal++
                        target in result.members -> entering++
                        else -> leaving++
                    }
                }
            }
            require(seen.size == result.contacts.size && internal == 579 && entering == 4668 && leaving == 4604) {
                "Circuit connectivity differs from the verified v783 preset"
            }
            incoming.forEach { (i, edges) -> result.presetIncoming[i] = edges.toIntArray() }
            try {
                checkedZip(File(directory, "feeding-skeletons.zip"), SKELETON_HASH).use { zip ->
                    for (i in result.members) {
                        val bytes = zip.getInputStream(zip.getEntry("${graph.ids[i]}.bin")).use { it.readBytes() }
                        result.skeletons[i] = decodeSkeleton(bytes)
                    }
                }
                result.anatomyStatus = "43 verified v783 arbors"
            } catch (e: Exception) {
                result.skeletons.clear()
                result.anatomyStatus = "Anatomy unavailable: ${e.message}"
            }
            return result
        }

        internal fun decodeSkeleton(bytes: ByteArray): FlySkeleton {
            val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val vertices = input.int; val edges = input.int
            require(vertices in 1..1_000_000 && edges in 0..2_000_000)
            require(bytes.size.toLong() == 8L + vertices * 16L + edges * 8L)
            val points = FloatArray(vertices * 3) { (input.float / 1000f).also { require(it.isFinite()) } }
            val segments = IntArray(edges * 2) { input.int.also { require(it in 0 until vertices) } }
            return FlySkeleton(points, segments)
        }
    }
}
