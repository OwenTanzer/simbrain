/** Looks up a neuron in the generated v783 annotation sidecar without retaining the entire table in memory. */
package org.simbrain.custom_sims.simulations.neuroscience

import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

data class FlyAnnotation(val rootId: Long, val fields: Map<String, String>) {
    fun value(field: String): String = fields[field].orEmpty().ifBlank { "unassigned" }
}

class FlyAnnotations(
    private val path: String = DEFAULT_PATH,
    private val expectedSha256: String = EXPECTED_SHA256,
) {
    fun at(graph: FlyConnectome, index: Int): FlyAnnotation? {
        require(index in 0 until graph.size)
        val file = File(path)
        if (!file.isFile) return null
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual == expectedSha256) { "Fly annotations differ from the pinned v783 sidecar. Regenerate with tools/flybrain/prepare_annotations.py." }

        return GZIPInputStream(file.inputStream()).bufferedReader().use { reader ->
            val header = reader.readLine()?.split('\t') ?: error("Empty fly annotation sidecar")
            val idColumn = header.indexOf("root_id")
            require(idColumn >= 0 && header.distinct().size == header.size) {
                "Invalid fly annotation header"
            }
            repeat(index) { require(reader.readLine() != null) { "Fly annotations end before neuron index $index" } }
            val row = reader.readLine()?.split('\t') ?: error("Fly annotations end before neuron index $index")
            require(row.size == header.size && row[idColumn].toLongOrNull() == graph.ids[index]) {
                "Fly annotation ID does not match neuron index $index"
            }
            FlyAnnotation(graph.ids[index], header.zip(row).toMap())
        }
    }

    companion object {
        const val DEFAULT_PATH = "simulations/data/flybrain/annotations-v783.tsv.gz"
        const val EXPECTED_SHA256 = "297a211a19f9decd45b39105ca52323a4601d7c364c0a7f59736c173cd6c7bab"
    }
}
