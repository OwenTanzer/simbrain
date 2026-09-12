/** A transparent spike overlay must preserve the underlying activation image when scaled. */
package org.simbrain.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.piccolo2d.util.PPaintContext
import org.simbrain.util.piccolo.SimbrainImage
import java.awt.Color
import java.awt.image.BufferedImage

class SimbrainImageAlphaTest {
    @Test
    fun `scaled transparent pixels preserve the underlying image`() {
        val source = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        source.setRGB(0, 0, Color.RED.rgb)
        val destination = BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB)
        val graphics = destination.createGraphics()
        try {
            graphics.color = Color.BLUE
            graphics.fillRect(0, 0, 20, 20)
            graphics.clipRect(0, 0, 20, 20)
            val node = SimbrainImage(source)
            node.setBounds(0.0, 0.0, 20.0, 20.0)
            node.fullPaint(PPaintContext(graphics))
            assertEquals(Color.RED.rgb, destination.getRGB(2, 2))
            assertEquals(Color.BLUE.rgb, destination.getRGB(15, 15))
        } finally { graphics.dispose() }
    }
}
