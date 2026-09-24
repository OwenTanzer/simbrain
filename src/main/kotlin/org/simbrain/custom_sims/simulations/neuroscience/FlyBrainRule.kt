/**
 * Whole-connectome leaky integrate-and-fire dynamics as a native Simbrain array rule.
 * Discrete event order follows Brian2: integrate, threshold, synapses/input, reset.
 */
package org.simbrain.custom_sims.simulations.neuroscience

import org.simbrain.network.core.*
import org.simbrain.network.util.SpikingMatrixData
import org.simbrain.network.util.SpikingScalarData
import org.simbrain.util.propertyeditor.HiddenTypeOption
import org.simbrain.util.WithXStreamPropertyConverter
import org.simbrain.util.createXStreamPropertyConverter
import kotlin.math.exp
import kotlin.math.roundToInt

@HiddenTypeOption
class FlyBrainRule : SpikingNeuronUpdateRule<SpikingScalarData, FlyBrainState>() {
    override val name = "FlyWire whole-brain LIF"
    override val graphicalLowerBound get() = -52.0
    override val graphicalUpperBound get() = -45.0
    override fun copy() = FlyBrainRule()
    override fun createMatrixData(size: Int) = FlyBrainState(size)

    context(Network)
    override fun apply(neuron: Neuron, data: SpikingScalarData) = error("The whole-brain rule requires a NeuronArray.")

    context(Network)
    override fun apply(layer: Layer, dataHolder: FlyBrainState) {
        require(layer is NeuronArray)
        val steps = (timeStep / FlyBrainState.DT).roundToInt()
        require(steps >= 1 && kotlin.math.abs(steps * FlyBrainState.DT - timeStep) < 1e-9) {
            "Use a network time step that is a positive multiple of 0.1 ms."
        }
        synchronized(dataHolder) {
            for (i in 0 until layer.size) dataHolder.voltage[i] = layer.activations[i, 0] + layer.inputs[i, 0]
            dataHolder.lastBinSpikes = 0
            dataHolder.binSpikes.fill(false)
            repeat(steps) { dataHolder.step() }
            for (i in 0 until layer.size) {
                layer.activations[i, 0] = dataHolder.voltage[i]
                dataHolder.spikes[i] = dataHolder.binSpikes[i]
                if (dataHolder.binSpikes[i]) dataHolder.lastSpikeTimes[i] = dataHolder.lastSpikeStep[i] * FlyBrainState.DT
            }
        }
    }
}

class FlyBrainState(size: Int) : SpikingMatrixData(size) {
    var graphPath = FlyConnectome.DEFAULT_PATH
    var graphFingerprint = ""
    var circuitView: FlyCircuitViewState? = null
    @Transient private var loadedGraph: FlyConnectome? = null
    var voltage = DoubleArray(size) { REST }
    var synapticDrive = DoubleArray(size)
    var lastSpikeStep = LongArray(size) { -1_000_000L }
    var counts = LongArray(size)
    var silenced = BooleanArray(size)
    var stimulusIndices = IntArray(0)
    var rateHz = 100.0
    var stimulusOn = true
    var seed = 42L
    var randomState = seed
    var tick = 0L
    var lastBinSpikes = 0
    var binSpikes = BooleanArray(size)
    var watchIndex = 0
    var recentSpikes = ArrayList<FlySpike>()
    var maxRecordedSpikes = 200_000
    var droppedRecords = 0L
    private var delayQueue = Array(DELAY + 1) { IntArray(0) }
    private var firing = IntArray(size)
    private var eligible = BooleanArray(size)

    fun attach(graph: FlyConnectome) {
        require(graph.size == size)
        loadedGraph = graph
    }

    fun graph(): FlyConnectome = loadedGraph ?: FlyConnectome.load(graphPath, graphFingerprint.ifEmpty { null }).let {
        graphFingerprint = it.first
        it.second.also { graph -> attach(graph) }
    }

    /** Advances 0.1 ms. Explicit impulses replace random input during reference replay. */
    fun step(impulses: IntArray? = null): Int {
        val graph = graph()
        var nFiring = 0
        for (i in 0 until size) {
            eligible[i] = tick - lastSpikeStep[i] >= if (i.inStimulus()) 0 else REFRACTORY
            if (eligible[i]) {
                voltage[i] = REST + (voltage[i] - REST) * MEMBRANE_DECAY + synapticDrive[i] * DRIVE_TO_VOLTAGE
                synapticDrive[i] *= SYNAPSE_DECAY
            }
            if (eligible[i] && voltage[i] > THRESHOLD) {
                firing[nFiring++] = i
                lastSpikeStep[i] = tick
                eligible[i] = false
                counts[i]++
                binSpikes[i] = true
                if (recentSpikes.size < maxRecordedSpikes) recentSpikes.add(FlySpike(tick, i)) else droppedRecords++
            }
        }
        val due = (tick % delayQueue.size).toInt()
        for (source in delayQueue[due]) {
            if (silenced[source]) continue
            for (edge in graph.offsets[source] until graph.offsets[source + 1]) {
                val target = graph.targets[edge]
                if (eligible[target]) synapticDrive[target] += graph.weights[edge]
            }
        }
        delayQueue[due] = IntArray(0)
        delayQueue[((tick + DELAY) % delayQueue.size).toInt()] = firing.copyOf(nFiring)
        if (impulses != null) {
            for (i in impulses) voltage[i] += INPUT_JUMP
        } else if (stimulusOn) {
            val p = rateHz * DT / 1000.0
            for (i in stimulusIndices) if (uniform() < p) voltage[i] += INPUT_JUMP
        }
        for (j in 0 until nFiring) {
            val i = firing[j]
            voltage[i] = REST
            synapticDrive[i] = 0.0
        }
        lastBinSpikes += nFiring
        tick++
        return nFiring
    }

    @Transient private var stimulusMask: BooleanArray? = null
    private fun Int.inStimulus(): Boolean = stimulusMask?.get(this) ?: false

    fun setStimulus(indices: IntArray) {
        require(indices.all { it in 0 until size })
        stimulusIndices = indices.distinct().toIntArray()
        rebuildStimulusMask()
    }

    fun rebuildStimulusMask() {
        stimulusMask = BooleanArray(size).also { mask -> stimulusIndices.forEach { mask[it] = true } }
    }

    private fun uniform(): Double {
        var x = randomState
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        randomState = x
        return (x ushr 11).toDouble() / 9007199254740992.0
    }

    override fun clear() {
        voltage.fill(REST)
        synapticDrive.fill(0.0)
        lastSpikeStep.fill(-1_000_000)
        counts.fill(0)
        spikes.fill(false)
        binSpikes.fill(false)
        lastSpikeTimes.fill(Double.NEGATIVE_INFINITY)
        delayQueue = Array(DELAY + 1) { IntArray(0) }
        tick = 0
        lastBinSpikes = 0
        randomState = if (seed == 0L) 42L else seed
        recentSpikes.clear()
        droppedRecords = 0
        rebuildStimulusMask()
    }

    override fun copy() = FlyBrainState(size).also { c ->
        commonCopy(c)
        c.circuitView = circuitView?.copy()
        c.graphPath = graphPath; c.graphFingerprint = graphFingerprint; c.loadedGraph = loadedGraph
        c.voltage = voltage.copyOf(); c.synapticDrive = synapticDrive.copyOf()
        c.lastSpikeStep = lastSpikeStep.copyOf(); c.counts = counts.copyOf(); c.silenced = silenced.copyOf()
        c.setStimulus(stimulusIndices); c.rateHz = rateHz; c.stimulusOn = stimulusOn
        c.seed = seed; c.randomState = randomState; c.tick = tick; c.watchIndex = watchIndex
        c.delayQueue = delayQueue.map { it.copyOf() }.toTypedArray()
        c.recentSpikes = ArrayList(recentSpikes); c.droppedRecords = droppedRecords
        c.maxRecordedSpikes = maxRecordedSpikes; c.lastBinSpikes = lastBinSpikes; c.binSpikes = binSpikes.copyOf()
    }

    companion object : WithXStreamPropertyConverter {
        override val xStreamPropertyConverter = createXStreamPropertyConverter<FlyBrainState>(
            marshal = {
                on(FlyBrainState::recentSpikes) { writer, context ->
                    writer.startNode("recentSpikes")
                    context.convertAnother(LongArray(size * 2) { i -> if (i % 2 == 0) this[i / 2].tick else this[i / 2].neuron.toLong() })
                    writer.endNode()
                }
            },
            unmarshal = {
                on("recentSpikes") { reader, context ->
                    val events = context.convertAnother(null, LongArray::class.java) as LongArray
                    withConstructedObject {
                        recentSpikes = ArrayList(events.size / 2)
                        require(events.size % 2 == 0)
                        for (i in events.indices step 2) recentSpikes.add(FlySpike(events[i], events[i + 1].toInt()))
                    }
                }
            }
        )
        const val DT = 0.1
        const val REST = -52.0
        const val THRESHOLD = -45.0
        const val DELAY = 18
        const val REFRACTORY = 22
        const val INPUT_JUMP = 68.75
        private val MEMBRANE_DECAY = exp(-DT / 20.0)
        private val SYNAPSE_DECAY = exp(-DT / 5.0)
        private val DRIVE_TO_VOLTAGE = (MEMBRANE_DECAY - SYNAPSE_DECAY) / 3.0
    }
}

data class FlySpike(val tick: Long, val neuron: Int)
