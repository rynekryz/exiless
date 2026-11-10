package com.rynekryz.exiless.core

import java.io.IOException
import java.util.zip.CRC32

internal object PngStripper {
    private val keep = setOf(
        "IHDR", "PLTE", "IDAT", "IEND", "tRNS", "gAMA", "cHRM", "sRGB", "iCCP", "sBIT", "pHYs",
        "bKGD", "hIST", "sPLT", "acTL", "fcTL", "fdAT", "cICP", "mDCV", "cLLI",
    )

    fun run(r: Reader, s: Sink, o: StripOptions): Long {
        val sig = r.readFully(8)
        if (Format.detect(sig) != Format.PNG) throw IOException("Not a PNG")
        s.write(sig)
        var removed = 0L
        while (true) {
            val len = r.u32()
            if (len > Int.MAX_VALUE) throw IOException("Corrupt PNG chunk")
            val type = String(r.readFully(4), Charsets.ISO_8859_1)
            when {
                type in keep -> {
                    s.u32(len); s.write(type.toByteArray(Charsets.ISO_8859_1))
                    r.copyTo(s, len + 4)
                    if (type == "IEND") return removed + r.drain()
                }
                type == "eXIf" && o.keepOrientation -> {
                    val data = r.readFully(len.toInt())
                    r.skip(4)
                    val orientation = Tiff.orientation(data)
                    if (orientation > 1) {
                        val tiff = Tiff.minimal(orientation)
                        writeChunk(s, "eXIf", tiff)
                        removed += len + 12 - (tiff.size + 12)
                    } else removed += len + 12
                }
                else -> { r.skip(len + 4); removed += len + 12 }
            }
        }
    }

    private fun writeChunk(s: Sink, type: String, data: ByteArray) {
        val t = type.toByteArray(Charsets.ISO_8859_1)
        s.u32(data.size.toLong()); s.write(t); s.write(data)
        val crc = CRC32().apply { update(t); update(data) }
        s.u32(crc.value)
    }
}
