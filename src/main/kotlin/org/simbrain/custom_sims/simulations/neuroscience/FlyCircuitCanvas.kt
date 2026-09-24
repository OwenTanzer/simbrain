/** Draws physical arbor projections and a separately labelled schematic without creating simulated neurons. */
package org.simbrain.custom_sims.simulations.neuroscience

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import javax.swing.JPanel
import kotlin.math.*

internal class FlyCircuitCanvas(
    private val circuit: FlyCircuit,
    private val view: FlyCircuitViewState,
    private val select: (Int) -> Unit,
) : JPanel() {
    var displayed = circuit.members.toMutableSet()
    var selected = circuit.graph.index(view.selectedId)
        set(value) { field = value; selectionImage = null }
    private val positions = mutableMapOf<Int, Point2D.Double>()
    private val paths = mutableMapOf<Pair<Int, String>, Path2D.Float>()
    private var image: BufferedImage? = null
    private var selectionImage: BufferedImage? = null
    private var scale = 1.0
    private var left = 0.0
    private var top = 0.0
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var drag: Point? = null
    private var dragged = false
    var edgeSummary = ""
        private set

    init {
        name = "Circuit canvas"
        preferredSize = Dimension(700, 540)
        minimumSize = Dimension(300, 250)
        background = Color(250, 251, 253)
        toolTipText = "Click an anchor/node to inspect; drag to pan; wheel to zoom. Circles are anchors, squares are somata."
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { drag = e.point; dragged = false }
            override fun mouseDragged(e: MouseEvent) {
                drag?.let { view.panX += e.x - it.x; view.panY += e.y - it.y }
                drag = e.point; dragged = true; invalidateDrawing()
            }
            override fun mouseReleased(e: MouseEvent) {
                if (!dragged) positions.minByOrNull { (_, p) -> screen(p).distance(e.point) }
                    ?.takeIf { screen(it.value).distance(e.point) < 18 }?.let { select(it.key) }
                drag = null
            }
            override fun mouseWheelMoved(e: MouseWheelEvent) {
                view.zoom = (view.zoom * 1.15.pow(-e.preciseWheelRotation)).coerceIn(.3, 15.0)
                invalidateDrawing()
            }
        }
        addMouseListener(mouse); addMouseMotionListener(mouse); addMouseWheelListener(mouse)
    }

    fun invalidateDrawing() { image = null; selectionImage = null; repaint() }
    fun fit() { view.zoom = 1.0; view.panX = 0.0; view.panY = 0.0; invalidateDrawing() }
    private fun axes() = when (view.projection) { "XZ" -> 0 to 2; "YZ" -> 1 to 2; else -> 0 to 1 }
    private fun point(index: Int, soma: Boolean = false): Point2D.Double? {
        val fields = circuit.annotations[index]?.fields ?: return null
        val prefix = if (soma) "soma_" else "pos_"
        val coordinates = "xyz".mapIndexed { i, a -> fields[prefix + a]?.toDoubleOrNull()?.times(if (i == 2) .04 else .004) ?: return null }
        val (x, y) = axes()
        return Point2D.Double(coordinates[x], coordinates[y])
    }
    private fun screen(p: Point2D.Double) = Point2D.Double(offsetX + (p.x - left) * scale, offsetY + (p.y - top) * scale)
    private fun path(index: Int): Path2D.Float? {
        val skeleton = circuit.skeletons[index] ?: return null
        return paths.getOrPut(index to view.projection) {
            val (x, y) = axes()
            Path2D.Float().apply {
                for (i in skeleton.segments.indices step 2) {
                    val a = skeleton.segments[i] * 3; val b = skeleton.segments[i + 1] * 3
                    moveTo(skeleton.points[a + x].toDouble(), skeleton.points[a + y].toDouble())
                    lineTo(skeleton.points[b + x].toDouble(), skeleton.points[b + y].toDouble())
                }
            }
        }
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        if (width < 1 || height < 1) return
        if (image?.width != width || image?.height != height) {
            selectionImage = null
            image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { buffer ->
                val g = buffer.createGraphics()
                try { drawBase(g) } finally { g.dispose() }
            }
        }
        if (selectionImage == null) {
            selectionImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { buffer ->
                val overlay = buffer.createGraphics()
                try {
                    overlay.drawImage(image, 0, 0, null)
                    drawSelection(overlay)
                } finally { overlay.dispose() }
            }
        }
        graphics.drawImage(selectionImage, 0, 0, null)
    }

    private fun drawBase(g: Graphics2D) {
        g.color = background; g.fillRect(0, 0, width, height)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        positions.clear()
        if (view.mode == "Anatomy") {
            displayed.forEach { i -> point(i)?.let { positions[i] = it } }
        } else {
            val groups = displayed.groupBy { circuit.aliases[it] ?: "External" }.toSortedMap()
            var column = 0
            groups.forEach { (_, members) ->
                val col = column % 4; val row = column / 4
                members.sorted().forEachIndexed { j, i -> positions[i] = Point2D.Double(col * 230.0 + (j % (if (members.size > 4) 5 else 2)) * (if (members.size > 4) 36 else 105), row * 170.0 + (j / (if (members.size > 4) 5 else 2)) * 32) }
                column++
            }
        }
        val bounds = positions.values.map { Point2D.Double(it.x, it.y) }.toMutableList()
        if (view.mode == "Anatomy") {
            displayed.forEach { i -> path(i)?.bounds2D?.let { bounds.add(Point2D.Double(it.minX, it.minY)); bounds.add(Point2D.Double(it.maxX, it.maxY)) } }
            if (view.context) {
                val (x, y) = axes()
                for (i in circuit.contextPoints.indices step 24) bounds.add(Point2D.Double(circuit.contextPoints[i+x].toDouble(), circuit.contextPoints[i+y].toDouble()))
            }
        }
        if (bounds.isEmpty()) { g.color = foreground; g.drawString("No anatomical coordinates available for this selection", 20, 40); return }
        left = bounds.minOf { it.x }; top = bounds.minOf { it.y }
        val spanX = max(1.0, bounds.maxOf { it.x } - left); val spanY = max(1.0, bounds.maxOf { it.y } - top)
        scale = min((width - 100).coerceAtLeast(1) / spanX, (height - 110).coerceAtLeast(1) / spanY) * view.zoom
        offsetX = (width - spanX * scale) / 2 + view.panX
        offsetY = (height - spanY * scale) / 2 + view.panY
        if (view.mode == "Anatomy") {
            val geometry = g.create() as Graphics2D
            geometry.translate(offsetX - left * scale, offsetY - top * scale); geometry.scale(scale, scale)
            if (view.context) {
                val (x, y) = axes()
                geometry.color = Color(187, 194, 203, 90)
                for (i in circuit.contextPoints.indices step 24) geometry.fill(java.awt.geom.Rectangle2D.Float(circuit.contextPoints[i+x], circuit.contextPoints[i+y], .8f, .8f))
            }
            displayed.forEach { i ->
                geometry.color = Color(153, 164, 181, 120); geometry.stroke = BasicStroke((.55 / scale).toFloat())
                path(i)?.let { geometry.draw(it) }
            }
            geometry.dispose()
        }
    }

    // Selection changes reuse the expensive context/arbor raster and only draw this small overlay.
    private fun drawSelection(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        if (view.mode == "Anatomy" && selected in displayed) {
            val geometry = g.create() as Graphics2D
            try {
                geometry.translate(offsetX - left * scale, offsetY - top * scale)
                geometry.scale(scale, scale)
                geometry.color = Color(181, 73, 36)
                geometry.stroke = BasicStroke((1.2 / scale).toFloat())
                path(selected)?.let { geometry.draw(it) }
            } finally { geometry.dispose() }
        }
        val edges = circuit.internalConnections(displayed)
        val visible = (if (view.allEdges) edges else edges.filter { it.source == selected || it.target == selected }).take(2000)
        edgeSummary = "${visible.size}/${edges.size} displayed internal edges"
        if (view.mode == "Connectivity") {
            visible.forEach { edge ->
                val a = positions[edge.source]?.let { screen(it) } ?: return@forEach
                val b = positions[edge.target]?.let { screen(it) } ?: return@forEach
                val positive = circuit.graph.weights[edge.edge] > 0
                g.color = if (positive) Color(36, 110, 170, 110) else Color(183, 54, 91, 150)
                g.stroke = if (positive) BasicStroke(1f) else BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(4f, 3f), 0f)
                val angle = atan2(b.y - a.y, b.x - a.x)
                val tx = b.x - 7 * cos(angle); val ty = b.y - 7 * sin(angle)
                if (a.distance(b) < 1) g.drawOval(a.x.toInt()-15, a.y.toInt()-18, 22, 20)
                else {
                    g.drawLine(a.x.toInt(), a.y.toInt(), tx.toInt(), ty.toInt())
                    g.stroke = BasicStroke(1f)
                    for (d in doubleArrayOf(-.45, .45)) g.drawLine(tx.toInt(), ty.toInt(), (tx - 7*cos(angle+d)).toInt(), (ty - 7*sin(angle+d)).toInt())
                }
            }
        }
        g.stroke = BasicStroke(1f)
        positions.forEach { (i, p) ->
            val s = screen(p); val radius = if (i == selected) 6 else 4
            g.color = if (i == selected) Color(191, 76, 32) else if (i in circuit.members) Color(30, 90, 137) else Color(120, 90, 155)
            g.fillOval(s.x.toInt()-radius, s.y.toInt()-radius, radius*2, radius*2)
            if (view.mode == "Anatomy") point(i, true)?.let { soma -> val t = screen(soma); g.drawRect(t.x.toInt()-3, t.y.toInt()-3, 6, 6) }
            if (view.mode == "Connectivity" || i == selected) {
                g.font = font.deriveFont(11f)
                val label = if (circuit.name(i) == "sugar_input") "S${circuit.members.filter { circuit.name(it) == "sugar_input" }.indexOf(i)+1}" else circuit.name(i) + " " + (circuit.annotations[i]?.fields?.get("side")?.take(1) ?: "?")
                g.drawString(label, (s.x+7).toFloat(), (s.y-6).toFloat())
            }
        }
        g.color = Color(42, 54, 68); g.font = font.deriveFont(12f)
        val title = if (view.mode == "Anatomy") "Physical ${view.projection} projection · µm · vertical coordinate increases downward" else "Connectivity schematic · blue solid + / red dashed − · arrows show direction"
        g.drawString(title, 12, 20)
        val footer = if (view.mode == "Anatomy") "Arbors are geometry, not synapses · circles: anchors · squares: available somata" else "$edgeSummary · complete connections remain in tables"
        g.drawString(footer, 12, height-12)
        if (view.mode == "Anatomy") {
            val bar = 50 * scale
            g.drawLine(16, height-42, (16+bar).toInt(), height-42)
            g.drawString("50 µm", 16, height-48)
        }
    }
}
