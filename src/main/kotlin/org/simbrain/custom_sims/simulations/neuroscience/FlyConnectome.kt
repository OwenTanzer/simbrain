/**
 * Loads the pinned FlyWire connectivity asset. The immutable graph is shared by state copies;
 * workspaces keep its fingerprint and reload it rather than embedding millions of edges as XML.
 */
package org.simbrain.custom_sims.simulations.neuroscience

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

class FlyConnectome(val ids: LongArray, val offsets: IntArray, val targets: IntArray, val weights: DoubleArray) {
    val size get() = ids.size
    val edges get() = targets.size
    @Transient private var indexCache: Map<Long, Int>? = null

    init {
        require(offsets.size == size + 1 && offsets.first() == 0 && offsets.last() == edges)
        require(weights.size == edges && weights.all { it.isFinite() })
        require((1 until offsets.size).all { offsets[it - 1] <= offsets[it] })
        require(targets.all { it in 0 until size })
        require(ids.toSet().size == size)
    }

    fun index(id: Long): Int {
        val cache = indexCache ?: ids.withIndex().associate { it.value to it.index }.also { indexCache = it }
        return cache[id] ?: error("Neuron $id is absent from this connectome.")
    }

    companion object {
        const val DEFAULT_PATH = "simulations/data/flybrain/flywire-v783.bin.gz"
        private var cached: Pair<String, FlyConnectome>? = null

        @Synchronized
        fun load(path: String = DEFAULT_PATH, fingerprint: String? = null): Pair<String, FlyConnectome> {
            val file = File(path)
            require(file.isFile) { "Fly wiring not found at ${file.absolutePath}. Run tools/flybrain/prepare_data.py or use the demo bundle." }
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            require(fingerprint == null || fingerprint == hash) { "The fly wiring differs from the saved experiment." }
            cached?.takeIf { it.first == hash }?.let { return it }
            val graph = DataInputStream(BufferedInputStream(GZIPInputStream(file.inputStream()), 1024 * 1024)).use { input ->
                val magic = ByteArray(8).also { input.readFully(it) }.toString(Charsets.US_ASCII)
                require(magic == "SBFLY001") { "Unsupported fly asset format." }
                val n = input.readInt()
                val e = input.readInt()
                require(n in 1..200_000 && e in 0..40_000_000) { "Invalid graph dimensions." }
                FlyConnectome(LongArray(n) { input.readLong() }, IntArray(n + 1) { input.readInt() },
                    IntArray(e) { input.readInt() }, DoubleArray(e) { input.readDouble() }).also {
                    require(input.read() == -1) { "Unexpected trailing connectivity data." }
                }
            }
            return (hash to graph).also { cached = it }
        }
    }
}
