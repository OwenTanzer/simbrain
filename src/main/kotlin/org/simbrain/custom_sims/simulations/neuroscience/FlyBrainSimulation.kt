/**
 * An explorable FlyWire v783 connectome in Simbrain: native array, scheduler, plots and workspace persistence.
 * The sugar preset uses sensory IDs from Shiu et al.'s released whole-brain model.
 */
package org.simbrain.custom_sims.simulations.neuroscience

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.simbrain.custom_sims.*
import org.simbrain.network.NetworkComponent
import org.simbrain.network.core.NeuronArray
import org.simbrain.plot.timeseries.TimeSeriesPlotComponent
import org.simbrain.util.*
import org.simbrain.util.genericframe.GenericJInternalFrame
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import org.simbrain.workspace.Workspace
import java.awt.Dimension
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JLabel

val flyBrainSimulation = newSim("flywire_v783") { optionString ->
    workspace.clearWorkspace()
    exposeTypes(FlyBrainRule::class)
    val (fingerprint, graph) = FlyConnectome.load()
    val component = addNetworkComponent("FlyWire v783 — 138,639 neurons")
    component.network.timeStep = 1.0
    val brain = NeuronArray(graph.size).apply {
        label = "Fly brain • membrane potential (mV)"
        updateRule = FlyBrainRule()
        gridMode = true
        circleMode = false
        labelArray = graph.ids.map { it.toString() }.toTypedArray()
        activations.fill(FlyBrainState.REST)
        location = point(0, 0)
    }
    val state = brain.dataHolder as FlyBrainState
    state.attach(graph)
    state.graphFingerprint = fingerprint
    state.setStimulus(SUGAR_IDS.map { graph.index(it) }.toIntArray())
    state.watchIndex = graph.index(MN9_ID)
    component.network.addNetworkModel(brain, usePlacementManager = false)
    addTimeSeriesComponent("Whole-brain spikes per 1 ms", "All neurons")
    addTimeSeriesComponent("Watched neuron — membrane potential (mV)", "Voltage")
    setupFlyBrain(workspace)

    addSidebarInfo("""
        # Fly brain · FlyWire v783

        **138,639 neurons · 15,091,983 directed edges · 54,492,922 synaptic contacts.**
        This is a connectome-constrained spiking model of an adult female fruit-fly brain.
        Each array element is one neuron; the image is ordered by dataset row, **not anatomy**.
        Hover over an element for its FlyWire ID. Wiring is stored sparsely inside the custom array rule.

        ## Try it
        Click **Run 1 s**. The sugar preset stimulates 20 retained sensory neurons at 100 Hz each.
        One of the original 21 IDs (720575940620900446) is absent in v783 and is omitted.
        The watched neuron defaults to **MN9 ($MN9_ID)**, used as an output in the source experiment.
        Compare with **Stimulus on/off**, then **Reset experiment** and run again.
        The plots use milliseconds; the status shows total spikes and the watched neuron's spike count.

        ## Control Panel Settings
        - **Input IDs:** comma- or space-separated FlyWire IDs receiving independent Poisson input.
        - **Input rate (Hz):** per-neuron input rate, from 0 to 1,000 Hz.
        - **Silenced IDs:** block these neurons' outgoing transmission; their own firing remains possible.
        - **Watch ID:** neuron shown in the voltage plot and status counter.
        - **Random seed:** repeatable stimulus sequence; applied on reset (zero maps to 42).
        - **Apply + reset:** validate all fields, apply them and restart the experiment.
        - **Sugar preset:** restore the 20 sensory inputs, MN9, 100 Hz and seed 42, then reset.
        - **Stimulus on/off:** toggle input without resetting ongoing activity.
        - **Run 100 ms / Run 1 s:** advance by a fixed simulated duration.
        - **Stop:** stop a fixed run or continuous toolbar run.
        - **Reset experiment:** clear voltage, currents, delay queues, counts, recordings and plots; keep settings.
        - **Export experiment:** choose a folder for spike events, all-neuron counts and experiment metadata.
        - **Explore feeding circuit:** open the 43-neuron sugar-responsive pathway inside the running whole-brain model.
        - **Circuit Neuron ID / Inspect ID:** navigate by exact ID without resetting or changing the watched voltage trace.
        - **Circuit neuron chooser:** select a literature-identified member of the preset.
        - **Anatomy / Connectivity:** switch between physical arbors and a schematic of actual directed connections.
        - **XY / XZ / YZ:** choose physical dataset axes in micrometres; the vertical coordinate increases downward.
        - **Brain context:** show a sampled whole-brain anchor cloud. Circles are anchors; squares are available somata.
        - **All internal edges:** show all schematic edges, capped at 2,000 with displayed/total counts; otherwise show selected-neuron edges.
        - **Fit view:** restore camera scale and position; drag to pan and use the mouse wheel to zoom.
        - **Add selected:** extend the canvas to an inspected partner, up to 100 neurons; outside anatomy may be unavailable.
        - **Reset circuit view:** restore preset membership and MN9 selection without resetting neural dynamics.
        - **Incoming / Outgoing tables:** sort and scroll complete directed connections, including outside partners. Double-click a row to follow its partner.
        - **Circuit connection columns:** source contact counts where verified (blank means unavailable), canonical signed model weights in mV, and preset membership. Weights increment synaptic drive; arrows do not locate individual synapses.
        - **Circuit close button:** stop observer workers; reopening preserves selection, projection and camera. Save Workspace also preserves an open view.
        - **Inspect watch neuron:** show the watched neuron's v783 annotation (when installed), outgoing connection count and ten strongest targets.

        Use Simbrain's **File → Save Workspace** to preserve state, settings and pending spikes.
        Reopening restores controls and plots; keep the pinned data file at the same relative path.
        Existing array producers and consumers support further Simbrain couplings. Array inputs add mV once
        per network update; array spike flags report whether each neuron spiked anywhere in that update.

        The feeding view contains 20 retained left sugar inputs, 22 named pathway neurons and right MN9.
        It has 579 internal edges plus 4,668 incoming and 4,604 outgoing boundary edges; all outside wiring
        remains active. The old source notebook's right-sugar wording conflicts with v783 left annotations.
        An absent historical left MN9 is not replaced by a guessed ID. Literature aliases and current types
        are separate fields. Bract is a family label; ambiguous Roundtree aliases are excluded.

        ## Model and scope
        Exact linear LIF integration at 0.1 ms; Simbrain updates/plots every 1 ms.
        Rest/reset −52 mV, threshold −45 mV, membrane 20 ms, synaptic decay 5 ms,
        delay 1.8 ms, refractory 2.2 ms (zero for input neurons); signed contact count × 0.275 mV.
        Input jumps are 68.75 mV. Discrete-time Poisson sampling uses a local seeded generator.
        Voltage is sampled at 1 ms, so brief peaks can occur between plotted points.
        Up to 200,000 spike events are recorded per reset; total neuron counts remain complete.

        This implements the released model's dynamics, with a Brian2 fixture checking voltages, currents
        and spike timing. The v783 sugar demo is an exploratory experiment, not a validation of the paper's
        v630 behavioral results. The model does not supply a body, learned behavior or all the biological
        properties missing from a wiring map.

        ## Sources
        [Shiu et al. model and data](https://github.com/philshiu/Drosophila_brain_model)
        · [FlyWire](https://flywire.ai/)
        · [Simbrain](https://github.com/simbrain/simbrain)
    """.trimIndent(), initiallyOpened = false)

    if (optionString?.startsWith("benchmark") == true) {
        val milliseconds = optionString.substringAfter(':', "1000").toInt()
        require(milliseconds in 1..60_000)
        val started = System.nanoTime()
        workspace.iterateSuspend(milliseconds)
        val elapsed = (System.nanoTime() - started) / 1e9
        val directory = File("build/flybrain-demo").apply { mkdirs() }
        exportFlyExperiment(state, directory)
        workspace.simulationId = "flywire_v783"
        workspace.save(File(directory, "flybrain.zip"), headless = true)
        val result = "Simulated ${milliseconds} ms in ${"%.3f".format(elapsed)} s; neurons=${graph.size}; edges=${graph.edges}; spikes=${state.counts.sum()}; active neurons=${state.counts.count { it > 0 }}; MN9=${state.counts[state.watchIndex]}"
        File(directory, "benchmark.txt").writeText(result + "\n")
        println(result)
    }
}.registerReopenFunction { workspace -> setupFlyBrain(workspace) }

private suspend fun SimulationScope.setupFlyBrain(workspace: Workspace) {
    exposeTypes(FlyBrainRule::class)
    val component = workspace.componentList.filterIsInstance<NetworkComponent>().first()
    val network = component.network
    val brain = network.getModels<NeuronArray>().first()
    val state = brain.dataHolder as FlyBrainState
    val graph = state.graph()
    state.rebuildStimulusMask()
    val plots = workspace.componentList.filterIsInstance<TimeSeriesPlotComponent>()
    plots.forEach { it.model.windowSize = 2000; it.model.fixedWidth = true }
    var status: JLabel? = null
    suspend fun updatePlots(block: () -> Unit) {
        if (desktop != null) withContext(Dispatchers.Swing) { block() } else block()
    }
    fun statusText() = "<html>Time: %.1f ms · spikes: %,d<br>Watch: %,d spikes · recorded: %,d%s</html>".format(
        state.tick * FlyBrainState.DT, state.counts.sum(), state.counts[state.watchIndex], state.recentSpikes.size,
        if (state.droppedRecords > 0) " (cap reached)" else "")
    suspend fun refreshStatus() {
        if (status != null) {
            val text = synchronized(state) { statusText() }
            withContext(Dispatchers.Swing) { status?.text = text }
        }
    }
    suspend fun stopAndAwait() {
        workspace.stop()
        while (workspace.updater.hasActiveIteration) delay(5)
    }
    suspend fun runFor(milliseconds: Int) {
        if (workspace.updater.isRunning || workspace.updater.hasActiveIteration) return
        val end = state.tick + milliseconds * 10
        workspace.updater.iterateWhile { workspace.updater.isRunning && state.tick < end }
        refreshStatus()
    }
    suspend fun reset() {
        stopAndAwait()
        synchronized(state) { state.clear(); brain.activations.fill(FlyBrainState.REST); brain.inputs.fill(0.0) }
        network.resetTime(); workspace.resetTime()
        updatePlots { plots.forEach { it.model.clearData() } }
        brain.events.updated.fire()
        refreshStatus()
    }
    network.updateManager.addAction(updateAction("Record fly activity") {
        val time = state.tick * FlyBrainState.DT
        updatePlots {
            plots[0].model.addData(0, time, state.lastBinSpikes.toDouble())
            plots[1].model.addData(0, time, state.voltage[state.watchIndex])
        }
        if (state.tick % 100L == 0L) refreshStatus()
    })
    withGui {
        var explorer: GenericJInternalFrame? = null
        fun openCircuit() {
            explorer?.takeIf { !it.isClosed }?.let { it.isIcon = false; it.moveToFront(); return }
            val view = state.circuitView ?: FlyCircuitViewState().also { state.circuitView = it }
            view.open = true
            val panel = FlyCircuitPanel(state, view)
            explorer = GenericJInternalFrame("Feeding circuit · whole-brain observer", true, true, true, true).apply {
                defaultCloseOperation = javax.swing.WindowConstants.DISPOSE_ON_CLOSE
                contentPane = panel
                val availableWidth = desktopPane.width.takeIf { it > 0 } ?: 1560
                val availableHeight = desktopPane.height.takeIf { it > 0 } ?: 780
                val desiredX = desktopPane.allFrames.firstOrNull { it.title == "Fly brain · experiment" }
                    ?.let { it.x + it.width + SIM_WINDOW_GAP } ?: 370
                val x = desiredX.coerceAtMost((availableWidth - 900).coerceAtLeast(SIM_WINDOW_GAP))
                setBounds(x, SIM_WINDOW_GAP, (availableWidth - x - SIM_WINDOW_GAP).coerceIn(800, 1180),
                    (availableHeight - 2 * SIM_WINDOW_GAP).coerceIn(400, 720))
                addInternalFrameListener(object : InternalFrameAdapter() {
                    override fun internalFrameClosed(e: InternalFrameEvent) { panel.close(); explorer = null }
                })
                addInternalFrame(this)
                isVisible = true
                moveToFront()
            }
        }
        val controls = createControlPanel("Fly brain · experiment", SIM_WINDOW_GAP, SIM_WINDOW_GAP) {
            addLabel("<html><b>FlyWire v783</b><br>138,639 neurons · 15.09 million edges</html>")
            val inputs = addTextField("Input IDs", state.stimulusIndices.joinToString(",") { graph.ids[it].toString() }, toolTip = "Comma- or space-separated FlyWire sensory neuron IDs.").apply { columns = 23 }
            val rate = addTextField("Input rate (Hz)", state.rateHz.toString(), toolTip = "Independent input rate per stimulated neuron, 0–1000 Hz.").apply { columns = 23 }
            val silence = addTextField("Silenced IDs", graph.ids.indices.filter { state.silenced[it] }.joinToString(",") { graph.ids[it].toString() }, toolTip = "Neuron IDs whose outgoing connections are blocked.").apply { columns = 23 }
            val watch = addTextField("Watch ID", graph.ids[state.watchIndex].toString(), toolTip = "FlyWire ID for the voltage trace and spike counter.").apply { columns = 23 }
            val seed = addTextField("Random seed", state.seed.toString(), toolTip = "Integer seed used when the experiment resets; zero maps to 42.").apply { columns = 23 }
            fun parseIds(text: String) = text.trim().split(Regex("[,\\s]+")).filter { it.isNotEmpty() }.map { graph.index(it.toLong()) }.toIntArray()
            addButton("Apply + reset") {
                try {
                    val settings = withContext(Dispatchers.Swing) { listOf(inputs.text, rate.text, silence.text, watch.text, seed.text) }
                    val inputIds = parseIds(settings[0]); val hz = settings[1].toDouble()
                    require(hz.isFinite() && hz in 0.0..1000.0) { "Input rate must be between 0 and 1000 Hz." }
                    val silencedIds = parseIds(settings[2]); val watchIndex = graph.index(settings[3].trim().toLong()); val seedValue = settings[4].trim().toLong()
                    stopAndAwait()
                    synchronized(state) {
                        state.setStimulus(inputIds); state.rateHz = hz; state.silenced.fill(false)
                        silencedIds.forEach { state.silenced[it] = true }
                        state.watchIndex = watchIndex; state.seed = seedValue
                    }
                    reset()
                } catch (e: IllegalArgumentException) {
                    withContext(Dispatchers.Swing) { showWarningDialog(e.message, "Check experiment settings") }
                } catch (e: IllegalStateException) {
                    withContext(Dispatchers.Swing) { showWarningDialog(e.message, "Unknown neuron") }
                }
            }.toolTipText = "Validate every field, apply all settings and reset dynamics."
            val toggle = addButton(if (state.stimulusOn) "Stimulus: ON" else "Stimulus: OFF") {
                synchronized(state) { state.stimulusOn = !state.stimulusOn }
                withContext(Dispatchers.Swing) { text = if (state.stimulusOn) "Stimulus: ON" else "Stimulus: OFF" }
            }.apply { toolTipText = "Toggle external sensory input without resetting the brain." }
            addButton("Sugar preset") {
                stopAndAwait()
                synchronized(state) {
                    state.setStimulus(SUGAR_IDS.map { graph.index(it) }.toIntArray()); state.rateHz = 100.0
                    state.watchIndex = graph.index(MN9_ID); state.silenced.fill(false); state.seed = 42; state.stimulusOn = true
                }
                withContext(Dispatchers.Swing) {
                    inputs.text = SUGAR_IDS.joinToString(","); rate.text = "100"; silence.text = ""
                    watch.text = MN9_ID.toString(); seed.text = "42"; toggle.text = "Stimulus: ON"
                }
                reset()
            }.toolTipText = "Restore the released model's sugar sensory IDs and the MN9 output watch."
            addButton("Run 100 ms") { runFor(100) }.toolTipText = "Advance 100 milliseconds of neural dynamics."
            addButton("Run 1 s") { runFor(1000) }.toolTipText = "Advance one simulated second."
            addButton("Stop") { workspace.stop() }.toolTipText = "Stop the current fixed-duration or continuous run."
            addButton("Reset experiment") { reset() }.toolTipText = "Clear dynamics and recordings, preserving the applied settings."
            addButton("Export experiment") {
                stopAndAwait()
                val folder = withContext(Dispatchers.Swing) {
                    JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY; dialogTitle = "Choose experiment export folder" }.let {
                        if (it.showSaveDialog(this@withGui.frame) == JFileChooser.APPROVE_OPTION) it.selectedFile else null
                    }
                }
                if (folder != null) {
                    val destination = File(folder, "flybrain-${System.currentTimeMillis()}")
                    synchronized(state) { exportFlyExperiment(state, destination) }
                    withContext(Dispatchers.Swing) { showMessageDialog("Saved experiment to ${destination.absolutePath}", "Experiment exported") }
                }
            }.toolTipText = "Export exact spike times, all neuron counts and settings to a new folder."
            addButton("Explore feeding circuit") { withContext(Dispatchers.Swing) { openCircuit() } }
                .toolTipText = "Explore the identified sugar-to-MN9 pathway, its anatomy and all incoming/outgoing connections."
            addButton("Inspect watch neuron") {
                val i = state.watchIndex
                val annotation = try {
                    withContext(Dispatchers.IO) { FlyAnnotations().at(graph, i) }
                } catch (e: Exception) {
                    withContext(Dispatchers.Swing) { showWarningDialog(e.message ?: "Could not read fly annotations.", "Fly annotations") }
                    return@addButton
                }
                val identity = if (annotation == null) {
                    "Annotations unavailable. Run python tools/flybrain/prepare_annotations.py to install the v783 sidecar."
                } else {
                    """Cell type: ${annotation.value("cell_type")}
                       |Class: ${annotation.value("cell_class")} · superclass: ${annotation.value("super_class")}
                       |Side: ${annotation.value("side")}
                       |Predicted transmitter: ${annotation.value("top_nt")} (confidence: ${annotation.value("top_nt_conf")})
                       |Anchor (4×4×40 nm voxels): ${annotation.value("pos_x")}, ${annotation.value("pos_y")}, ${annotation.value("pos_z")}
                       |Soma (same voxel space): ${annotation.value("soma_x")}, ${annotation.value("soma_y")}, ${annotation.value("soma_z")}""".trimMargin()
                }
                val edges = (graph.offsets[i] until graph.offsets[i + 1]).sortedByDescending { kotlin.math.abs(graph.weights[it]) }
                val details = edges.take(10).joinToString("\n") { "${graph.ids[graph.targets[it]]}: %.3f mV".format(graph.weights[it]) }
                withContext(Dispatchers.Swing) {
                    showMessageDialog("Neuron ${graph.ids[i]}\n$identity\n\n${edges.size} outgoing targets\n\nStrongest targets:\n$details", "Watch neuron", rows = 22, columns = 105)
                }
            }.toolTipText = "Inspect the watched neuron's FlyWire annotation and strongest outgoing weights."
            status = addLabel(statusText()).apply { preferredSize = Dimension(350, 45) }
        }.awaitLayout()
        val x = SIM_WINDOW_GAP + controls.width + SIM_WINDOW_GAP
        place(component, x, SIM_WINDOW_GAP, 560, 410)
        place(plots[0], x, 420 + SIM_WINDOW_GAP, 560, 290)
        place(plots[1], x + 560 + SIM_WINDOW_GAP, SIM_WINDOW_GAP, 500, 410)
        getNetworkPanel(component).network.events.zoomToFitPage.fire()
        if (state.circuitView?.open == true) openCircuit()
    }
}

internal fun exportFlyExperiment(state: FlyBrainState, directory: File) {
    directory.mkdirs()
    val graph = state.graph()
    File(directory, "spikes.csv").bufferedWriter().use { out ->
        out.appendLine("time_ms,flywire_id")
        state.recentSpikes.forEach { out.appendLine("${it.tick * FlyBrainState.DT},${graph.ids[it.neuron]}") }
    }
    File(directory, "neuron-counts.csv").bufferedWriter().use { out ->
        out.appendLine("flywire_id,spikes")
        graph.ids.indices.forEach { out.appendLine("${graph.ids[it]},${state.counts[it]}") }
    }
    File(directory, "experiment.txt").writeText("""
        FlyWire v783 / Shiu et al. LIF model / local Simbrain port
        graph_sha256=${state.graphFingerprint}
        simulated_ms=${state.tick * FlyBrainState.DT}
        stimulus_ids=${state.stimulusIndices.joinToString(",") { graph.ids[it].toString() }}
        stimulus_rate_hz=${state.rateHz}
        stimulus_on=${state.stimulusOn}
        silenced_outgoing_ids=${graph.ids.indices.filter { state.silenced[it] }.joinToString(",") { graph.ids[it].toString() }}
        watch_id=${graph.ids[state.watchIndex]}
        seed=${state.seed}
        recorded_spikes=${state.recentSpikes.size}
        unrecorded_spikes=${state.droppedRecords}
        Counts include all spikes. Settings describe the current configuration; toggle history is not recorded.
    """.trimIndent() + "\n")
}

internal const val MN9_ID = 720575940660219265L
internal val SUGAR_IDS = longArrayOf(
    720575940624963786, 720575940630233916, 720575940637568838, 720575940638202345,
    720575940617000768, 720575940630797113, 720575940632889389, 720575940621754367,
    720575940621502051, 720575940640649691, 720575940639332736, 720575940616885538,
    720575940639198653, 720575940617937543, 720575940632425919,
    720575940633143833, 720575940612670570, 720575940628853239, 720575940629176663,
    720575940611875570
)
