/**
 * Compact, lossless persistence for large integer, boolean and label arrays. Small arrays and legacy
 * XML retain XStream's original converters; large arrays avoid quadratic DOM sibling traversal.
 */
package org.simbrain.util

import com.thoughtworks.xstream.converters.Converter
import com.thoughtworks.xstream.converters.MarshallingContext
import com.thoughtworks.xstream.converters.UnmarshallingContext
import com.thoughtworks.xstream.io.HierarchicalStreamReader
import com.thoughtworks.xstream.io.HierarchicalStreamWriter
import java.io.*
import java.util.Base64

class CompactArrayConverter(private val type: Class<*>, private val legacy: Converter) : Converter {
    override fun canConvert(candidate: Class<*>) = candidate == type

    override fun marshal(source: Any, writer: HierarchicalStreamWriter, context: MarshallingContext) {
        val size = java.lang.reflect.Array.getLength(source)
        if (size < 100) { legacy.marshal(source, writer, context); return }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(size)
            when (source) {
                is IntArray -> source.forEach { out.writeInt(it) }
                is LongArray -> source.forEach { out.writeLong(it) }
                is BooleanArray -> source.forEach { out.writeBoolean(it) }
                is Array<*> -> source.forEach {
                    if (it == null) out.writeInt(-1) else {
                        val value = (it as String).toByteArray(Charsets.UTF_8)
                        out.writeInt(value.size); out.write(value)
                    }
                }
            }
        }
        writer.addAttribute("encoding", "simbrain-array-v1")
        writer.setValue(Base64.getEncoder().encodeToString(bytes.toByteArray()))
    }

    override fun unmarshal(reader: HierarchicalStreamReader, context: UnmarshallingContext): Any {
        if (reader.getAttribute("encoding") != "simbrain-array-v1") return legacy.unmarshal(reader, context)
        return DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(reader.value))).use { input ->
            val n = input.readInt()
            require(n in 0..10_000_000) { "Invalid compact array size." }
            val result: Any = when (type) {
                IntArray::class.java -> IntArray(n) { input.readInt() }
                LongArray::class.java -> LongArray(n) { input.readLong() }
                BooleanArray::class.java -> BooleanArray(n) { input.readBoolean() }
                else -> arrayOfNulls<String>(n).also { array ->
                    repeat(n) {
                        val length = input.readInt()
                        require(length in -1..input.available()) { "Invalid compact label length." }
                        if (length >= 0) array[it] = ByteArray(length).also(input::readFully).toString(Charsets.UTF_8)
                    }
                }
            }
            require(input.read() == -1) { "Trailing compact array data." }
            result
        }
    }
}
