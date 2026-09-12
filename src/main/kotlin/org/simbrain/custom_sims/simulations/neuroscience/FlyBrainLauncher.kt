/** Desktop entry point for the standalone local fly-brain demo distribution. */
package org.simbrain.custom_sims.simulations.neuroscience

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.simbrain.workspace.gui.SimbrainDesktop

suspend fun main(args: Array<String>) {
    if (args.firstOrNull() == "--benchmark") {
        flyBrainSimulation.run(optionString = "benchmark:${args.getOrNull(1) ?: "1000"}")
        return
    }
    withContext(Dispatchers.Swing) {
        SimbrainDesktop.frame.setSize(1560, 960)
        SimbrainDesktop.main(emptyArray())
    }
    withContext(Dispatchers.Default) {
        flyBrainSimulation.run(desktop = SimbrainDesktop)
    }
}
