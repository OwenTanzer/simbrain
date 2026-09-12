/** Checks exact large-array round trips and compatibility with XStream's existing array XML. */
package org.simbrain.util

import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.io.xml.DomDriver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CompactArrayConverterTest {
    @Test
    fun `large primitive arrays and Unicode labels round trip without loss`() {
        val stream = getSimbrainXStream()
        val ints = IntArray(1000) { it - 500 }
        val longs = LongArray(1000) { Long.MAX_VALUE - it }
        val flags = BooleanArray(1000) { it % 3 == 0 }
        val labels = Array<String?>(1000) { if (it % 5 == 0) null else "μ neuron $it <&>\n" }
        assertArrayEquals(ints, stream.fromXML(stream.toXML(ints)) as IntArray)
        assertArrayEquals(longs, stream.fromXML(stream.toXML(longs)) as LongArray)
        assertArrayEquals(flags, stream.fromXML(stream.toXML(flags)) as BooleanArray)
        assertArrayEquals(labels, stream.fromXML(stream.toXML(labels)) as Array<*>)
    }

    @Test
    fun `existing XML arrays remain readable and small arrays retain their format`() {
        val original = XStream(DomDriver())
        val current = getSimbrainXStream()
        val values = listOf(intArrayOf(-1, 0, 99), longArrayOf(Long.MIN_VALUE, 720575940660219265), booleanArrayOf(true, false), arrayOf("same", "same", "<&>"))
        values.forEach { array ->
            val legacyXml = original.toXML(array)
            assertEquals(legacyXml, current.toXML(array))
            assertEquals(legacyXml, original.toXML(current.fromXML(legacyXml)))
        }
    }
}
