/** Verifies the observer's data contract and exact all-neuron dynamics while Swing selection remains active. */
package org.simbrain.custom_sims.simulations.neuroscience

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.File
import java.nio.file.Files
import javax.swing.SwingUtilities

class FlyCircuitTest {
    private fun graph(): FlyConnectome {
        assumeTrue(File(FlyConnectome.DEFAULT_PATH).isFile, "Install the full graph to run the circuit integration gates")
        return FlyConnectome.load().second
    }

    @Test
    fun `preset resolves exact incident edges contacts and physical skeletons`() {
        val graph = graph(); val circuit = FlyCircuit.load(graph)
        assertEquals(43, circuit.members.size)
        assertEquals(43, circuit.skeletons.size)
        assertEquals(579, circuit.internalConnections().size)
        val incoming = circuit.members.flatMap { circuit.incoming(it) }
        val outgoing = circuit.members.flatMap { circuit.outgoing(it) }
        assertEquals(4668, incoming.count { it.source !in circuit.members })
        assertEquals(4604, outgoing.count { it.target !in circuit.members })
        assertEquals(9851, (incoming + outgoing).map { it.edge }.toSet().size)
        (incoming + outgoing).forEach { edge ->
            assertEquals(edge.source, circuit.sourceOf(edge.edge))
            assertEquals(edge.target, graph.targets[edge.edge])
            assertTrue(edge.edge in graph.offsets[edge.source] until graph.offsets[edge.source+1])
            assertEquals(edge.contacts!! * .275, kotlin.math.abs(graph.weights[edge.edge]), 0.0)
        }
        val mn9 = graph.index(MN9_ID)
        assertEquals(227, circuit.incoming(mn9).size); assertEquals(82, circuit.outgoing(mn9).size)
        val roundup = graph.index(720575940623211725L)
        val edge = circuit.incoming(mn9).single { it.source == roundup }
        assertEquals(424, edge.contacts); assertEquals(116.6, graph.weights[edge.edge], 1e-12)
        assertEquals(21, circuit.members.count { circuit.annotations[it]!!.fields.getValue("soma_x").isNotEmpty() })
        circuit.members.forEach { i ->
            val a = circuit.annotations.getValue(i)
            val anchor = "xyz".mapIndexed { d, axis -> a.fields.getValue("pos_$axis").toDouble() * if (d == 2) .04 else .004 }
            val points = circuit.skeletons.getValue(i).points
            val distanceSquared = (points.indices step 3).minOf { p -> (0..2).sumOf { d -> (points[p+d] - anchor[d]).let { it * it } } }
            assertTrue(distanceSquared < 1.685 * 1.685, "Physical coordinate mismatch for ${graph.ids[i]}")
        }
        val outside = incoming.first { it.source !in circuit.members }.source
        val expected = graph.targets.indices.filter { graph.targets[it] == outside }
        assertEquals(expected, circuit.incoming(outside).map { it.edge })
    }

    @Test
    fun `missing anatomy leaves verified connectivity and corrupt assets are rejected`() {
        val graph = graph(); val temp = Files.createTempDirectory("fly-circuit-assets").toFile()
        try {
            File(FlyCircuit.DIRECTORY, "feeding-circuit.zip").copyTo(File(temp, "feeding-circuit.zip"))
            val missing = FlyCircuit.load(graph, temp)
            assertTrue(missing.skeletons.isEmpty()); assertEquals(579, missing.internalConnections().size)
            assertTrue(missing.anatomyStatus.startsWith("Anatomy unavailable"))
            File(temp, "feeding-skeletons.zip").writeText("corrupt")
            assertTrue(FlyCircuit.load(graph, temp).skeletons.isEmpty())
            File(temp, "feeding-circuit.zip").appendText("corrupt")
            assertThrows(IllegalArgumentException::class.java) { FlyCircuit.load(graph, temp) }
        } finally { temp.deleteRecursively() }
    }

    @Test
    @Timeout(600)
    fun `live observer selection preserves every state value delayed event and spike across five conditions`() {
        val graph = graph()
        val originalWeights = graph.weights.contentHashCode(); val originalTargets = graph.targets.contentHashCode()
        val expected = FlyBrainState(graph.size).apply { attach(graph); setStimulus(SUGAR_IDS.map { graph.index(it) }.toIntArray()) }
        val observed = expected.copy()
        lateinit var panel: FlyCircuitPanel
        SwingUtilities.invokeAndWait { panel = FlyCircuitPanel(observed, FlyCircuitViewState()) }
        try {
            val deadline = System.nanoTime() + 30_000_000_000
            while (!panel.ready && System.nanoTime() < deadline) Thread.sleep(20)
            assertTrue(panel.ready, "Circuit panel did not finish loading")
            SwingUtilities.invokeAndWait {
                assertEquals(227, panel.incomingTable.rowCount); assertEquals(82, panel.outgoingTable.rowCount)
            }
            val circuit = panel.circuit!!
            val pending = FlyBrainState::class.java.getDeclaredField("delayQueue").apply { isAccessible = true }
            val cases = listOf(Triple(42L,100.0,true), Triple(43L,100.0,true), Triple(44L,100.0,true), Triple(42L,100.0,false), Triple(42L,200.0,true))
            for ((seed, rate, on) in cases) {
                for (state in listOf(expected, observed)) synchronized(state) { state.seed = seed; state.rateHz = rate; state.stimulusOn = on; state.clear() }
                repeat(1000) { bin ->
                    repeat(10) { expected.step() }
                    synchronized(observed) { repeat(10) { observed.step() } }
                    if (bin % 50 == 0) SwingUtilities.invokeAndWait { panel.selectNeuron(circuit.members.elementAt((bin/50) % 43)) }
                    synchronized(observed) {
                        assertEquals(expected.tick, observed.tick); assertEquals(expected.randomState, observed.randomState)
                        assertArrayEquals(expected.voltage, observed.voltage, 0.0)
                        assertArrayEquals(expected.synapticDrive, observed.synapticDrive, 0.0)
                        assertArrayEquals(expected.counts, observed.counts)
                        assertArrayEquals(expected.lastSpikeStep, observed.lastSpikeStep)
                        assertArrayEquals(expected.binSpikes, observed.binSpikes)
                        @Suppress("UNCHECKED_CAST") val a = pending.get(expected) as Array<IntArray>
                        @Suppress("UNCHECKED_CAST") val b = pending.get(observed) as Array<IntArray>
                        a.indices.forEach { assertArrayEquals(a[it], b[it]) }
                    }
                }
                assertEquals(expected.recentSpikes, observed.recentSpikes)
                assertEquals(0L, observed.droppedRecords)
                if (!on) assertEquals(0L, observed.counts.sum())
                if (seed == 42L && rate == 100.0 && on) {
                    assertEquals(8553L, observed.counts.sum())
                    assertEquals(59L, observed.counts[graph.index(MN9_ID)])
                }
                println("Circuit parity PASS: seed=$seed rate=$rate stimulus=$on ticks=${observed.tick} spikes=${observed.counts.sum()}")
            }
            assertEquals(originalWeights, graph.weights.contentHashCode()); assertEquals(originalTargets, graph.targets.contentHashCode())
        } finally { SwingUtilities.invokeAndWait { panel.close() }; assertTrue(panel.disposed) }
    }
}
