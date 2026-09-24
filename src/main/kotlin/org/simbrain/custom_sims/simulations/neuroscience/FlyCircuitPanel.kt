/** Owns circuit-view workers and Swing controls; observations are snapshots of the unchanged whole-brain state. */
package org.simbrain.custom_sims.simulations.neuroscience

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.concurrent.Executors
import javax.swing.*
import javax.swing.table.AbstractTableModel

class FlyCircuitPanel(
    private val state: FlyBrainState,
    val view: FlyCircuitViewState,
    directory: File = File(FlyCircuit.DIRECTORY),
) : JPanel(BorderLayout()), AutoCloseable {
    @Volatile var circuit: FlyCircuit? = null
        private set
    @Volatile var disposed = false
        private set
    @Volatile private var generation = 0
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "Fly circuit observer").apply { isDaemon = true } }
    private val status = JLabel("Loading the verified feeding circuit…")
    private val identity = JTextArea(5, 40).apply { isEditable = false; lineWrap = true; wrapStyleWord = true }
    private val activity = JLabel(" ")
    private val search = JTextField(view.selectedId.toString(), 21)
    private val members = JComboBox<String>()
    private var memberOrder = intArrayOf()
    private var canvas: FlyCircuitCanvas? = null
    private val center = JPanel(BorderLayout())
    private val incomingModel = Connections(true)
    private val outgoingModel = Connections(false)
    val incomingTable = table(incomingModel, "Incoming connections")
    val outgoingTable = table(outgoingModel, "Outgoing connections")
    private var loadingSelection = false
    private var snapshotPending = false
    private val extraAnnotations = ArrayDeque<Int>()
    private val refresh = Timer(100) { refreshActivity() }
    val ready get() = circuit != null && !loadingSelection && !disposed

    init {
        name = "Feeding circuit explorer"
        preferredSize = Dimension(1180, 720)
        status.border = BorderFactory.createEmptyBorder(5, 8, 5, 8)
        status.toolTipText = "Asset and selection status. All outside connections remain active in the whole-brain simulation."
        val controls = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val first = JPanel(FlowLayout(FlowLayout.LEFT, 5, 2))
        search.toolTipText = "Exact FlyWire root ID to inspect; this does not reset the experiment or change the watched trace."
        first.add(JLabel("Neuron ID").apply { toolTipText = search.toolTipText }); first.add(search)
        first.add(button("Inspect ID", "Inspect a neuron anywhere in the whole-brain graph.") { inspectId() })
        search.addActionListener { inspectId() }
        members.preferredSize = Dimension(390, 28)
        members.toolTipText = "Choose one of the 43 literature-identified feeding pathway neurons."
        members.addActionListener { if (!loadingSelection && members.selectedIndex in memberOrder.indices) selectNeuron(memberOrder[members.selectedIndex]) }
        first.add(members)
        val second = JPanel(FlowLayout(FlowLayout.LEFT, 5, 2))
        val mode = JComboBox(arrayOf("Anatomy", "Connectivity")).apply {
            selectedItem = view.mode; toolTipText = "Switch between physical anatomy and a schematic of actual directed connections."
            addActionListener { view.mode = selectedItem.toString(); canvas?.fit() }
        }
        val projection = JComboBox(arrayOf("XY", "XZ", "YZ")).apply {
            selectedItem = view.projection; toolTipText = "Dataset axes in physical micrometres; vertical coordinate increases downward."
            addActionListener { view.projection = selectedItem.toString(); canvas?.fit() }
        }
        val context = JCheckBox("Brain context", view.context).apply {
            toolTipText = "Show a sampled whole-brain anchor cloud around the selected circuit."
            addActionListener { view.context = isSelected; canvas?.fit() }
        }
        val all = JCheckBox("All internal edges", view.allEdges).apply {
            toolTipText = "Show all displayed-neuron edges in the schematic, up to 2,000; otherwise highlight the selected neuron's edges. Tables stay complete."
            addActionListener { view.allEdges = isSelected; canvas?.invalidateDrawing() }
        }
        second.add(mode); second.add(projection); second.add(context); second.add(all)
        second.add(button("Fit view", "Reset the camera without changing selection or dynamics.") { canvas?.fit() })
        second.add(button("Add selected", "Add the inspected external neuron to the canvas, up to 100 neurons. Its anatomy may be unavailable.") {
            val c = circuit ?: return@button; val drawing = canvas ?: return@button
            val i = c.graph.index(view.selectedId)
            if (drawing.displayed.size >= 100 && i !in drawing.displayed) status.text = "100-neuron canvas limit reached; complete connections remain inspectable."
            else { drawing.displayed.add(i); saveMembership(); drawing.fit(); showStatus() }
        })
        second.add(button("Reset circuit view", "Restore the 43-neuron membership and MN9 selection without resetting the simulation.") {
            val c = circuit ?: return@button
            canvas?.displayed = c.members.toMutableSet(); saveMembership(); canvas?.fit(); selectNeuron(c.graph.index(MN9_ID))
        })
        controls.add(first); controls.add(second); controls.add(status)
        add(controls, BorderLayout.NORTH)
        val details = JPanel(BorderLayout(0, 5))
        details.add(JPanel(BorderLayout()).apply { add(JScrollPane(identity), BorderLayout.CENTER); add(activity, BorderLayout.SOUTH) }, BorderLayout.NORTH)
        val tables = JTabbedPane().apply {
            addTab("Incoming", JScrollPane(incomingTable)); addTab("Outgoing", JScrollPane(outgoingTable))
            toolTipText = "Complete connection lists, including partners outside the displayed circuit. Double-click a row to follow its partner."
        }
        details.add(tables, BorderLayout.CENTER)
        details.preferredSize = Dimension(480, 500); details.minimumSize = Dimension(330, 250)
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, center, details).apply { resizeWeight = .60; dividerLocation = 680 }
        add(split, BorderLayout.CENTER)
        worker.execute {
            try {
                val loaded = FlyCircuit.load(state.graph(), directory)
                SwingUtilities.invokeLater {
                    if (!disposed) {
                        circuit = loaded
                        if (runCatching { loaded.graph.index(view.selectedId) }.isFailure) view.selectedId = MN9_ID
                        canvas = FlyCircuitCanvas(loaded, view, ::selectNeuron).also { drawing ->
                            val restored = view.displayedIds.take(100).mapNotNull { runCatching { loaded.graph.index(it) }.getOrNull() }
                            if (restored.isNotEmpty()) drawing.displayed = (loaded.members + restored).take(100).toMutableSet()
                            center.add(drawing, BorderLayout.CENTER)
                        }
                        loadingSelection = true
                        memberOrder = loaded.members.sortedBy { loaded.label(it) }.toIntArray()
                        memberOrder.forEach { members.addItem(loaded.label(it)) }
                        loadingSelection = false
                        selectNeuron(loaded.graph.index(view.selectedId))
                        revalidate(); repaint(); refresh.start()
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater { if (!disposed) { status.text = "Circuit unavailable: ${e.message}"; identity.text = "The whole-brain experiment can continue. Install or repair the pinned circuit assets to explore this preset." } }
            }
        }
    }

    private fun button(text: String, tip: String, action: () -> Unit) = JButton(text).apply {
        toolTipText = tip; addActionListener { action() }
    }
    private fun inspectId() {
        val c = circuit ?: return
        try { selectNeuron(c.graph.index(search.text.trim().toLong())) }
        catch (e: Exception) { status.text = "Cannot inspect: ${e.message}" }
    }
    private fun saveMembership() { view.displayedIds = canvas?.displayed?.map { state.graph().ids[it] }?.toLongArray() ?: LongArray(0) }
    private fun showStatus() {
        val c = circuit ?: return
        val count = canvas?.displayed?.size ?: 43
        status.text = "$count displayed · 43 preset / 579 preset edges · 4,668 incoming + 4,604 outgoing boundary edges · ${c.anatomyStatus}"
    }

    fun selectNeuron(index: Int) {
        check(SwingUtilities.isEventDispatchThread())
        val c = circuit ?: return
        if (disposed) return
        val request = ++generation
        view.selectedId = c.graph.ids[index]; search.text = view.selectedId.toString()
        loadingSelection = true
        members.selectedIndex = memberOrder.indexOf(index)
        canvas?.selected = index; canvas?.invalidateDrawing()
        incomingModel.rows = emptyList(); outgoingModel.rows = emptyList()
        incomingModel.fireTableDataChanged(); outgoingModel.fireTableDataChanged()
        identity.text = "${c.label(index)}\nLoading complete incoming and outgoing connections…"
        activity.text = " "
        worker.execute {
            if (disposed || request != generation) return@execute
            try {
                var annotationWarning = ""
                if (index !in c.annotations) {
                    try {
                        FlyAnnotations().at(c.graph, index)?.let {
                            c.annotations[index] = it; extraAnnotations.addLast(index)
                            if (extraAnnotations.size > 128) c.annotations.remove(extraAnnotations.removeFirst())
                        }
                    } catch (e: Exception) { annotationWarning = "\nAnnotation unavailable: ${e.message}" }
                }
                val incoming = c.incoming(index); val outgoing = c.outgoing(index)
                SwingUtilities.invokeLater {
                    if (!disposed && request == generation) {
                        incomingModel.rows = incoming; outgoingModel.rows = outgoing
                        incomingModel.fireTableDataChanged(); outgoingModel.fireTableDataChanged()
                        val a = c.annotations[index]
                        identity.text = "${c.graph.ids[index]} · ${c.name(index)}\n" +
                            "Type: ${a?.value("cell_type") ?: "unassigned"} · class: ${a?.value("cell_class") ?: "unassigned"} · side: ${a?.value("side") ?: "unassigned"}\n" +
                            "Predicted transmitter: ${a?.value("top_nt") ?: "unassigned"} (${a?.value("top_nt_conf") ?: "unassigned"})\n" +
                            "${incoming.size} incoming / ${outgoing.size} outgoing; ${if (index in c.members) "in preset" else "outside preset"}. Double-click a connection to navigate.\n" +
                            "FlyWire v783 / annotation v2.1.0; literature aliases from Shiu source mapping and supplements. " +
                            (if (index in c.skeletons) "Verified arbor available." else "Arbor unavailable; any marker uses the annotation anchor.") + annotationWarning
                        loadingSelection = false; showStatus(); canvas?.invalidateDrawing(); refreshActivity()
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater { if (!disposed && request == generation) { loadingSelection = false; status.text = "Inspection failed: ${e.message}" } }
            }
        }
    }

    private fun refreshActivity() {
        val c = circuit ?: return
        if (disposed || snapshotPending || loadingSelection) return
        snapshotPending = true
        val request = generation; val index = c.graph.index(view.selectedId)
        worker.execute {
            val sample = c.snapshot(state, intArrayOf(index))
            SwingUtilities.invokeLater {
                snapshotPending = false
                if (!disposed && request == generation) activity.text = "t = %.1f ms · V = %.3f mV · %,d spikes".format(sample.tick * .1, sample.voltage[0], sample.counts[0])
            }
        }
    }

    private fun table(model: Connections, title: String) = JTable(model).apply {
        name = title; autoCreateRowSorter = true; autoResizeMode = JTable.AUTO_RESIZE_OFF
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        toolTipText = "Double-click to inspect the partner. Weight is canonical model synaptic drive (mV); contacts are verified source counts when available."
        val widths = intArrayOf(155, 100, 55, 80, 90, 85)
        widths.forEachIndexed { i, w -> columnModel.getColumn(i).preferredWidth = w }
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = rowAtPoint(e.point)
                if (e.clickCount == 2 && row >= 0) {
                    val connection = model.rows[convertRowIndexToModel(row)]
                    selectNeuron(if (model.incoming) connection.source else connection.target)
                }
            }
        })
    }

    private inner class Connections(val incoming: Boolean) : AbstractTableModel() {
        var rows = emptyList<FlyCircuitConnection>()
        private val headers = arrayOf("Partner root ID", "Alias / type", "Side", "Contacts", "Weight (mV)", "Membership")
        override fun getRowCount() = rows.size
        override fun getColumnCount() = headers.size
        override fun getColumnName(column: Int) = headers[column]
        override fun getColumnClass(column: Int): Class<*> = when (column) { 3 -> Integer::class.java; 4 -> java.lang.Double::class.java; else -> String::class.java }
        override fun isCellEditable(row: Int, column: Int) = false
        override fun getValueAt(row: Int, column: Int): Any? {
            val c = circuit ?: return null; val edge = rows[row]; val partner = if (incoming) edge.source else edge.target
            return when (column) {
                0 -> c.graph.ids[partner].toString()
                1 -> c.name(partner)
                2 -> c.annotations[partner]?.value("side") ?: "unassigned"
                3 -> edge.contacts
                4 -> c.graph.weights[edge.edge]
                else -> if (partner in c.members) "Preset" else "Outside"
            }
        }
    }

    override fun close() {
        if (disposed) return
        disposed = true; generation++; refresh.stop(); worker.shutdownNow()
        view.open = false
    }
    override fun removeNotify() { close(); super.removeNotify() }
}
