/** Exercises the actual desktop and records paired whole-brain/viewer timings; also runs against the pre-viewer baseline. */
package org.simbrain.util.uisnapshot

import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.simbrain.custom_sims.simulations.neuroscience.FlyBrainState
import org.simbrain.custom_sims.simulations.neuroscience.flyBrainSimulation
import org.simbrain.network.NetworkComponent
import org.simbrain.network.core.NeuronArray
import org.simbrain.workspace.gui.SimbrainDesktop
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.*

class FlyCircuitValidationSnapshot : UiSnapshotDef {
    private val condition = System.getenv("FLY_CIRCUIT_CONDITION") ?: "C"
    override val name = "fly-circuit-$condition"
    override fun build(): Component = runBlocking {
        val directory = File("build/circuit-validation-$condition").apply { mkdirs() }
        val metrics = linkedMapOf<String, String>()
        fun record(key: String, value: Any) {
            metrics[key] = value.toString()
            File(directory, "metrics.tsv").writeText("metric\tvalue\n" + metrics.entries.joinToString("\n") { "${it.key}\t${it.value}" } + "\n")
            println("CIRCUIT $condition $key=" + value.toString().let { if (it.length > 500) "${it.length} characters recorded" else it })
        }
        record("condition", condition); record("java", System.getProperty("java.version"))
        record("pid", ProcessHandle.current().pid()); record("max_heap_bytes", Runtime.getRuntime().maxMemory())
        withContext(Dispatchers.Swing) {
            SimbrainDesktop.frame.bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
            SimbrainDesktop.frame.extendedState = Frame.MAXIMIZED_BOTH
            SimbrainDesktop.frame.isVisible = true
        }
        flyBrainSimulation.run(desktop = SimbrainDesktop)
        val workspace = SimbrainDesktop.workspace
        fun state() = workspace.componentList.filterIsInstance<NetworkComponent>().first().network.getModels<NeuronArray>().first().dataHolder as FlyBrainState
        fun descendants(component: Component): List<Component> = listOf(component) + if (component is Container) component.components.flatMap { descendants(it) } else emptyList()
        suspend fun components() = withContext(Dispatchers.Swing) { descendants(SimbrainDesktop.desktopPane) }
        suspend fun click(label: String) = withContext(Dispatchers.Swing) {
            descendants(SimbrainDesktop.desktopPane).filterIsInstance<JButton>().single { it.text == label }.doClick(0)
        }
        suspend fun await(test: () -> Boolean) = withTimeout(60_000) { while (!withContext(Dispatchers.Swing) { test() }) delay(5) }
        suspend fun runSecond(): Double {
            click("Sugar preset")
            await { state().tick == 0L && !workspace.updater.hasActiveIteration }
            val started = System.nanoTime(); click("Run 1 s")
            await { state().tick == 10000L && !workspace.updater.hasActiveIteration && !workspace.updater.isRunning }
            check(state().counts.sum() == 8553L)
            check(state().counts[state().watchIndex] == 59L)
            return (System.nanoTime() - started) / 1e6
        }
        fun memory(label: String) {
            System.gc(); Thread.sleep(250)
            val runtime = Runtime.getRuntime()
            record("heap_${label}_bytes", runtime.totalMemory() - runtime.freeMemory())
            val pid = ProcessHandle.current().pid()
            val command = "(Get-Process -Id $pid).WorkingSet64; (Get-Process -Id $pid).PrivateMemorySize64; (Get-Process -Id $pid).PeakWorkingSet64"
            val process = ProcessBuilder("powershell.exe", "-NoProfile", "-Command", command).start()
            val values = process.inputStream.bufferedReader().readLines().mapNotNull { it.trim().toLongOrNull() }
            check(process.waitFor() == 0 && values.size == 3)
            record("working_set_${label}_bytes", values[0]); record("private_${label}_bytes", values[1]); record("peak_working_set_${label}_bytes", values[2])
        }
        record("warmup_ms", runSecond())
        memory("closed")
        var observer: JComponent? = null
        suspend fun ready() = await { observer?.let { it.javaClass.getMethod("getReady").invoke(it) as Boolean } == true }
        suspend fun open(): Double {
            val started = System.nanoTime(); click("Explore feeding circuit")
            await { descendants(SimbrainDesktop.desktopPane).any { it.name == "Feeding circuit explorer" } }
            observer = components().filterIsInstance<JComponent>().single { it.name == "Feeding circuit explorer" }
            ready()
            withContext(Dispatchers.Swing) { observer!!.paintImmediately(0, 0, observer!!.width, observer!!.height) }
            return (System.nanoTime() - started) / 1e6
        }
        suspend fun close() {
            withContext(Dispatchers.Swing) {
                (SwingUtilities.getAncestorOfClass(JInternalFrame::class.java, observer) as JInternalFrame).dispose()
            }
            observer = null
            withTimeout(10_000) { while (Thread.getAllStackTraces().keys.any { it.isAlive && it.name == "Fly circuit observer" }) delay(10) }
        }
        suspend fun screenshot(file: String) = withContext(Dispatchers.Swing) {
            val panel = observer ?: SimbrainDesktop.frame.rootPane
            val image = BufferedImage(panel.width, panel.height, BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics(); panel.paint(graphics); graphics.dispose()
            ImageIO.write(image, "png", File(directory, file))
        }
        if (condition == "C") {
            record("initial_open_ms", open())
            val tables = components().filterIsInstance<JTable>()
            check(tables.single { it.name == "Incoming connections" }.rowCount == 227)
            check(tables.single { it.name == "Outgoing connections" }.rowCount == 82)
            memory("open")
        }
        val delays = mutableListOf<Double>()
        var lastHeartbeat = System.nanoTime()
        val heartbeat = withContext(Dispatchers.Swing) { Timer(20) {
            val now = System.nanoTime(); delays.add(((now-lastHeartbeat)/1e6 - 20).coerceAtLeast(0.0)); lastHeartbeat = now
        }.apply { start() } }
        val times = mutableListOf<Double>()
        val trials = (System.getenv("FLY_CIRCUIT_TRIALS") ?: "5").toInt()
        repeat(trials) { trial ->
            if (condition == "C" && trial == 0) {
                val run = async { runSecond() }
                val latencies = mutableListOf<Double>()
                val cameraLatencies = mutableListOf<Double>()
                val chooser = components().filterIsInstance<JComboBox<*>>().single { it.itemCount == 43 }
                repeat(100) { i ->
                    val start = System.nanoTime()
                    withContext(Dispatchers.Swing) { chooser.selectedIndex = i % 43 }
                    ready()
                    withContext(Dispatchers.Swing) { observer!!.paintImmediately(0, 0, observer!!.width, observer!!.height) }
                    latencies.add((System.nanoTime()-start)/1e6)
                    if (i % 10 == 0) withContext(Dispatchers.Swing) {
                        val cameraStart = System.nanoTime()
                        val all = descendants(observer!!)
                        val projection = all.filterIsInstance<JComboBox<*>>().single { it.itemCount == 3 }
                        projection.selectedIndex = (i/10) % 3
                        val canvas = all.single { it.name == "Circuit canvas" }
                        canvas.dispatchEvent(MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, 220, 200, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, if (i % 20 == 0) -1 else 1))
                        val table = all.filterIsInstance<JTable>().first()
                        table.rowSorter.toggleSortOrder(4)
                        observer!!.paintImmediately(0, 0, observer!!.width, observer!!.height)
                        cameraLatencies.add((System.nanoTime() - cameraStart) / 1e6)
                    }
                    delay(20)
                }
                record("selection_ms", latencies.joinToString(",")); record("camera_sort_ms", cameraLatencies.joinToString(",")); times.add(run.await())
            } else times.add(runSecond())
            record("run_ms", times.joinToString(","))
        }
        withContext(Dispatchers.Swing) { heartbeat.stop() }
        record("dispatch_delay_ms", delays.joinToString(","))
        if (condition == "C") {
            val all = components()
            val id = all.filterIsInstance<JTextField>().single { it.toolTipText?.startsWith("Exact FlyWire") == true }
            withContext(Dispatchers.Swing) { id.text = "720575940660219265" }; click("Inspect ID"); ready()
            val incoming = components().filterIsInstance<JTable>().single { it.name == "Incoming connections" }
            withContext(Dispatchers.Swing) {
                val row = (0 until incoming.rowCount).first { incoming.getValueAt(it, 5) == "Outside" }
                val rect = incoming.getCellRect(row, 0, true)
                incoming.dispatchEvent(MouseEvent(incoming, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, rect.x+5, rect.y+5, 2, false))
            }
            ready(); check(withContext(Dispatchers.Swing) { id.text != "720575940660219265" })
            click("Add selected"); click("Reset circuit view"); ready()
            withContext(Dispatchers.Swing) {
                val controls = descendants(observer!!)
                controls.filterIsInstance<JComboBox<*>>().single { it.itemCount == 3 }.selectedItem = "XZ"
                controls.filterIsInstance<JCheckBox>().single { it.text == "Brain context" }.doClick(0)
            }
            screenshot("anatomy.png")
            withContext(Dispatchers.Swing) {
                val controls = descendants(observer!!)
                controls.filterIsInstance<JComboBox<*>>().single { it.itemCount == 2 }.selectedItem = "Connectivity"
                controls.filterIsInstance<JCheckBox>().single { it.text == "All internal edges" }.doClick(0)
            }
            screenshot("connectivity.png")
            record("boundary_navigation", "passed")
            val reopenTimes = mutableListOf<Double>(); val retained = mutableListOf<Long>()
            repeat(10) {
                close(); reopenTimes.add(open())
                System.gc(); delay(100)
                retained.add(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())
            }
            record("reopen_ms", reopenTimes.joinToString(",")); record("reopen_heap_bytes", retained.joinToString(","))
            val saved = File(directory, "resume.zip")
            workspace.simulationId = "flywire_v783"
            workspace.save(saved, headless = false)
            workspace.openWorkspace(saved, useDesktop = true)
            await { descendants(SimbrainDesktop.desktopPane).any { it.name == "Feeding circuit explorer" } }
            observer = components().filterIsInstance<JComponent>().single { it.name == "Feeding circuit explorer" }
            ready(); check(state().tick == 10000L && state().counts.sum() == 8553L)
            check(withContext(Dispatchers.Swing) { descendants(observer!!).filterIsInstance<JComboBox<*>>().single { it.itemCount == 2 }.selectedItem == "Connectivity" })
            record("workspace_reopen", "passed"); screenshot("reopened.png")
            memory("after_reopen")
            close(); memory("after_close")
            record("observer_threads_after_close", Thread.getAllStackTraces().keys.count { it.isAlive && it.name == "Fly circuit observer" })
        }
        record("result", "PASS")
        withContext(Dispatchers.Swing) { SimbrainDesktop.frame.rootPane.apply { preferredSize = size } }
    }
}
