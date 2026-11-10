package com.rynekryz.exiless.core

import java.io.EOFException
import java.io.IOException

internal object JpegStripper {
    private const val SOI = 0xD8
    private const val EOI = 0xD9
    private const val SOS = 0xDA
    private const val APP0 = 0xE0
    private const val APP1 = 0xE1
    private const val APP2 = 0xE2
    private const val APP14 = 0xEE
    private const val COM = 0xFE

    fun run(r: Reader, s: Sink, o: StripOptions): Long {
        if (r.u8() != 0xFF || r.u8() != SOI) throw IOException("Not a JPEG")
        s.write(0xFF); s.write(SOI)
        var removed = 0L
        var exifHandled = false
        var pending = -1
        try {
            while (true) {
                var m = pending
                pending = -1
                if (m < 0) {
                    if (r.u8() != 0xFF) throw IOException("Corrupt JPEG marker")
                    m = r.u8()
                    while (m == 0xFF) m = r.u8()
                }
                when {
                    m == EOI -> {
                        s.write(0xFF); s.write(EOI)
                        return removed + r.drain()
                    }
                    m == 0x01 || m in 0xD0..0xD7 -> { s.write(0xFF); s.write(m) }
                    else -> {
                        val len = r.u16()
                        if (len < 2) throw IOException("Corrupt JPEG segment")
                        val payload = r.readFully(len - 2)
                        val keep = when (m) {
                            APP0 -> !payload.startsWith("JFXX")
                            APP2 -> payload.startsWith("ICC_PROFILE")
                            APP14 -> payload.startsWith("Adobe")
                            APP1 -> false
                            COM -> false
                            in 0xE3..0xEF -> false
                            else -> true
                        }
                        if (keep) {
                            s.write(0xFF); s.write(m); s.u16(len); s.write(payload)
                        } else {
                            var cost = len + 2L
                            if (m == APP1 && !exifHandled && o.keepOrientation && payload.startsWith("Exif\u0000\u0000")) {
                                val orientation = Tiff.orientation(payload, 6)
                                if (orientation > 1) {
                                    exifHandled = true
                                    val tiff = Tiff.minimal(orientation)
                                    s.write(0xFF); s.write(APP1); s.u16(2 + 6 + tiff.size)
                                    s.write("Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1)); s.write(tiff)
                                    cost -= 2 + 2 + 6 + tiff.size
                                }
                            }
                            removed += cost
                        }
                        if (m == SOS) pending = copyScan(r, s)
                    }
                }
            }
        } catch (_: EOFException) {
            return removed // Truncated
        }
    }

    private fun copyScan(r: Reader, s: Sink): Int {
        while (true) {
            val c = r.u8()
            if (c != 0xFF) { s.write(c); continue }
            var n = r.u8()
            while (n == 0xFF) n = r.u8()
            if (n == 0x00 || n in 0xD0..0xD7) { s.write(0xFF); s.write(n) } else return n
        }
    }
}
