/** Captures the actual Simbrain desktop after one second of the full-connectome sugar demo. */
package org.simbrain.util.uisnapshot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.simbrain.custom_sims.simulations.neuroscience.FlyBrainState
import org.simbrain.network.NetworkComponent
import org.simbrain.network.core.NeuronArray
import org.simbrain.custom_sims.simulations.neuroscience.flyBrainSimulation
import org.simbrain.workspace.gui.SimbrainDesktop
import java.awt.Component
import java.awt.Dimension
import java.awt.Container
import javax.swing.JButton

class FlyBrainSnapshot : UiSnapshotDef {
    override val name = "flybrain-desktop"
    override fun build(): Component = runBlocking {
        withContext(Dispatchers.Swing) {
            SimbrainDesktop.frame.setSize(1560, 960)
            SimbrainDesktop.frame.isVisible = true
        }
        flyBrainSimulation.run(desktop = SimbrainDesktop)
        val workspace = SimbrainDesktop.workspace
        val state = workspace.componentList.filterIsInstance<NetworkComponent>().first().network.getModels<NeuronArray>().first().dataHolder as FlyBrainState
        fun buttons(component: Component): List<JButton> =
            (if (component is JButton) listOf(component) else emptyList()) +
                (if (component is Container) component.components.flatMap { buttons(it) } else emptyList())
        suspend fun click(label: String) = withContext(Dispatchers.Swing) {
            buttons(SimbrainDesktop.desktopPane).single { it.text == label }.doClick()
        }
        suspend fun await(condition: () -> Boolean) = withTimeout(60_000) {
            while (!synchronized(state) { condition() }) delay(20)
        }
        click("Run 100 ms")
        await { state.tick == 1000L && !workspace.updater.isRunning }
        check(state.counts.sum() > 0)
        click("Stimulus: ON")
        click("Reset experiment")
        await { state.tick == 0L && !state.stimulusOn }
        click("Run 100 ms")
        await { state.tick == 1000L && !workspace.updater.isRunning }
        check(state.counts.sum() == 0L)
        click("Sugar preset")
        await { state.tick == 0L && state.stimulusOn }
        click("Run 1 s")
        await { state.tick > 0L }
        click("Stop")
        await { !workspace.updater.isRunning && !workspace.updater.hasActiveIteration }
        check(state.tick < 10000L)
        click("Sugar preset")
        await { state.tick == 0L }
        click("Run 1 s")
        await { state.tick == 10000L && !workspace.updater.isRunning }
        check(state.counts.sum() == 8553L)
        println("Desktop controls PASS: fixed run, stimulus toggle, reset, sugar preset, stop, repeatable one-second run.")
        withContext(Dispatchers.Swing) {
            SimbrainDesktop.frame.rootPane.preferredSize = Dimension(1560, 960)
            SimbrainDesktop.frame.rootPane
        }
    }
}
