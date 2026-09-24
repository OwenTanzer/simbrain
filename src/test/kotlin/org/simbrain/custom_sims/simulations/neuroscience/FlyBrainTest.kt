/** Numerical parity with an independently recorded Brian2 run and native-array integration checks. */
package org.simbrain.custom_sims.simulations.neuroscience

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.simbrain.network.core.Network
import org.simbrain.network.core.NeuronArray
import org.simbrain.custom_sims.SimulationScope
import org.simbrain.network.NetworkComponent
import org.simbrain.workspace.Workspace
import java.nio.file.Files

class FlyBrainTest {
    private fun state() = FlyBrainState(5).apply {
        attach(FlyConnectome(longArrayOf(0, 1, 2, 3, 4), intArrayOf(0, 2, 3, 5, 6, 7),
            intArrayOf(1, 3, 2, 1, 3, 4, 1), doubleArrayOf(120.0, 50.0, 160.0, -90.0, 100.0, 60.0, 30.0).map { it * .275 }.toDoubleArray()))
        setStimulus(intArrayOf(0))
    }

    @Test
    fun `matches upstream Brian2 voltage drive and spike timing`() {
        val s = state()
        val impulses = setOf(0, 5, 13, 28, 40, 62, 120, 180, 230)
        val rows = javaClass.getResourceAsStream("/flybrain/brian2-oracle.csv")!!.bufferedReader().readLines().drop(1)
        rows.chunked(5).forEachIndexed { tick, neurons ->
            s.step(if (tick in impulses) intArrayOf(0) else intArrayOf())
            neurons.forEach { row ->
                val c = row.split(','); val i = c[1].toInt()
                assertEquals(c[2].toDouble(), s.voltage[i], 1e-9, "voltage at tick $tick neuron $i")
                assertEquals(c[3].toDouble(), s.synapticDrive[i], 1e-9, "drive at tick $tick neuron $i")
                assertEquals(c[4] == "1", s.lastSpikeStep[i] == tick.toLong(), "spike at tick $tick neuron $i")
            }
        }
        assertEquals(14, s.counts.sum())
    }

    @Test
    fun `outgoing silencing blocks transmission but preserves source firing`() {
        val s = state().apply { silenced[0] = true }
        repeat(100) { s.step(if (it == 0) intArrayOf(0) else intArrayOf()) }
        assertEquals(1, s.counts[0])
        assertTrue(s.voltage.drop(1).all { it == FlyBrainState.REST })
    }

    @Test
    fun `native array batches ten microsteps without changing dynamics`() = runBlocking {
        val expected = state().apply { rateHz = 500.0 }
        val network = Network().apply { timeStep = 1.0 }
        val array = NeuronArray(5).apply { updateRule = FlyBrainRule() }
        val actual = array.dataHolder as FlyBrainState
        actual.attach(expected.graph()); actual.setStimulus(intArrayOf(0)); actual.rateHz = 500.0
        array.activations.fill(FlyBrainState.REST)
        network.addNetworkModel(array)
        repeat(50) {
            repeat(10) { expected.step() }
            network.update()
        }
        assertArrayEquals(expected.voltage, actual.voltage, 0.0)
        assertArrayEquals(expected.counts, actual.counts)
        val copied = actual.copy()
        repeat(100) { actual.step(); copied.step() }
        assertArrayEquals(actual.voltage, copied.voltage, 0.0)
        assertArrayEquals(actual.counts, copied.counts)
        assertEquals(actual.recentSpikes, copied.recentSpikes)
    }

    @Test
    @org.junit.jupiter.api.Timeout(90)
    fun `full connectome workspace resumes exact pending events and random stream`() = runBlocking {
        // Optional large-asset gate, enabled when the local data bundle is installed.
        org.junit.jupiter.api.Assumptions.assumeTrue(java.io.File(FlyConnectome.DEFAULT_PATH).isFile)
        val scope = SimulationScope()
        flyBrainSimulation.task(scope, null)
        scope.workspace.simulationId = "flywire_v783"
        val array = scope.workspace.componentList.filterIsInstance<NetworkComponent>().first().network.getModels<NeuronArray>().first()
        val state = array.dataHolder as FlyBrainState
        assertEquals(138639, state.graph().size)
        assertEquals(15091983, state.graph().edges)
        assertEquals(20, state.stimulusIndices.size)
        state.circuitView = FlyCircuitViewState().apply {
            open = true; selectedId = 720575940623211725L; projection = "XZ"; mode = "Connectivity"
            zoom = 2.0; panX = 18.0; displayedIds = longArrayOf(MN9_ID, selectedId)
        }
        scope.workspace.iterateSuspend(37)
        val file = Files.createTempFile("flybrain-resume", ".zip").toFile()
        try {
            scope.workspace.save(file, headless = true)
            scope.workspace.iterateSuspend(63)
            val reopened = Workspace()
            reopened.openWorkspace(file, useDesktop = false)
            reopened.iterateSuspend(63)
            val restoredArray = reopened.componentList.filterIsInstance<NetworkComponent>().first().network.getModels<NeuronArray>().first()
            val restored = restoredArray.dataHolder as FlyBrainState
            assertNotNull(restored.circuitView)
            assertEquals(state.circuitView!!.selectedId, restored.circuitView!!.selectedId)
            assertEquals("XZ", restored.circuitView!!.projection)
            assertEquals("Connectivity", restored.circuitView!!.mode)
            assertEquals(2.0, restored.circuitView!!.zoom)
            assertEquals(18.0, restored.circuitView!!.panX)
            assertArrayEquals(state.circuitView!!.displayedIds, restored.circuitView!!.displayedIds)
            assertEquals(state.tick, restored.tick)
            assertEquals(state.randomState, restored.randomState)
            assertArrayEquals(state.voltage, restored.voltage, 0.0)
            assertArrayEquals(state.synapticDrive, restored.synapticDrive, 0.0)
            assertArrayEquals(state.counts, restored.counts)
            assertEquals(state.recentSpikes, restored.recentSpikes)
            assertTrue(state.counts.sum() > 0)
        } finally { file.delete() }
    }
}
