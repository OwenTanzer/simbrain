/** Checks that annotation lookups stay aligned with the model's neuron IDs. */
package org.simbrain.custom_sims.simulations.neuroscience

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

class FlyAnnotationsTest {
    private val graph = FlyConnectome(longArrayOf(11, 22, 33), intArrayOf(0, 0, 0, 0), intArrayOf(), doubleArrayOf())

    @Test
    fun `looks up a row by graph index and preserves missing labels`() {
        val file = Files.createTempFile("fly-annotations", ".tsv.gz").toFile()
        try {
            GZIPOutputStream(file.outputStream()).bufferedWriter().use {
                it.write("supervoxel_id\troot_id\tcell_type\tside\n101\t11\tA\tleft\n202\t22\t\tright\n303\t33\tC\t\n")
            }
            val checksum = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            val lookup = FlyAnnotations(file.path, checksum)
            assertEquals("unassigned", lookup.at(graph, 1)?.value("cell_type"))
            assertEquals("right", lookup.at(graph, 1)?.value("side"))
            assertEquals("C", lookup.at(graph, 2)?.value("cell_type"))
            assertEquals("unassigned", lookup.at(graph, 2)?.value("side"))
            assertNull(FlyAnnotations(file.path + ".missing", checksum).at(graph, 0))
            assertThrows(IllegalArgumentException::class.java) { FlyAnnotations(file.path, "wrong").at(graph, 1) }
            val reordered = FlyConnectome(longArrayOf(22, 11, 33), intArrayOf(0, 0, 0, 0), intArrayOf(), doubleArrayOf())
            assertThrows(IllegalArgumentException::class.java) { lookup.at(reordered, 1) }
        } finally {
            file.delete()
        }
    }
}
