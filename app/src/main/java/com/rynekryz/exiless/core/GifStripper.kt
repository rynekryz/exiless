package com.rynekryz.exiless.core

import java.io.ByteArrayOutputStream
import java.io.IOException

internal object GifStripper {
    fun run(r: Reader, s: Sink): Long {
        val head = r.readFully(6)
        if (Format.detect(head) != Format.GIF) throw IOException("Not a GIF")
        s.write(head)
        val lsd = r.readFully(7)
        s.write(lsd)
        if (lsd[4].toInt() and 0x80 != 0) r.copyTo(s, colorTable(lsd[4]))
        var removed = 0L
        while (true) {
            when (val b = r.u8()) {
                0x3B -> { s.write(b); return removed + r.drain() }
                0x2C -> {
                    s.write(b)
                    val desc = r.readFully(9)
                    s.write(desc)
                    if (desc[8].toInt() and 0x80 != 0) r.copyTo(s, colorTable(desc[8]))
                    s.write(r.u8()) // LZW size
                    s.write(subBlocks(r))
                }
                0x21 -> {
                    val label = r.u8()
                    val blocks = subBlocks(r)
                    val keep = when (label) {
                        0xF9, 0x01 -> true
                        0xFF -> blocks.startsWith("NETSCAPE2.0", 1) || blocks.startsWith("ANIMEXTS1.0", 1)
                        else -> false
                    }
                    if (keep) { s.write(b); s.write(label); s.write(blocks) } else removed += 2 + blocks.size
                }
                else -> throw IOException("Corrupt GIF block")
            }
        }
    }

    private fun colorTable(packed: Byte): Long = 3L shl ((packed.toInt() and 7) + 1)

    private fun subBlocks(r: Reader): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val n = r.u8()
            out.write(n)
            if (n == 0) return out.toByteArray()
            out.write(r.readFully(n))
        }
    }
}
