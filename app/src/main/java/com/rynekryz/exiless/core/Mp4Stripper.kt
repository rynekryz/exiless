package com.rynekryz.exiless.core

import java.io.IOException

internal object Mp4Stripper {
    private const val MAX_MOOV = 512L * 1024 * 1024
    private val topLevelMeta = setOf("udta", "meta", "uuid", "XMP_")

    fun run(r: Reader, s: Sink, o: StripOptions): Long {
        var removed = 0L
        var firstBox = true
        while (true) {
            val first8 = r.read()
            if (first8 < 0) return removed
            val size32 = (first8.toLong() shl 24) or (r.u8().toLong() shl 16) or (r.u8().toLong() shl 8) or r.u8().toLong()
            val type = r.readFully(4)
            val name = String(type, Charsets.ISO_8859_1)
            if (firstBox && name != "ftyp") throw IOException("Not an MP4/MOV file")
            firstBox = false
            var header = 8
            var size = size32
            if (size32 == 1L) { size = r.u64(); header = 16 }
            val rest = size - header // To EOF
            fun writeHeader(t: ByteArray) {
                s.u32(if (header == 16) 1 else size32); s.write(t)
                if (header == 16) { s.u32(size ushr 32); s.u32(size and 0xFFFFFFFFL) }
            }
            when {
                size32 == 0L -> { writeHeader(type); r.copyRest(s); return removed }
                rest < 0 -> throw IOException("Corrupt box")
                name == "moov" -> {
                    if (rest > MAX_MOOV) throw IOException("moov too large")
                    val body = r.readFully(rest.toInt())
                    removed += walk(body, 0, body.size, o, 0)
                    writeHeader(type); s.write(body)
                }
                name in topLevelMeta -> {
                    r.skip(rest); removed += rest
                    writeHeader("free".toByteArray()); s.zeros(rest)
                }
                else -> { writeHeader(type); r.copyTo(s, rest) }
            }
        }
    }

    private fun walk(b: ByteArray, from: Int, to: Int, o: StripOptions, depth: Int): Long {
        var removed = 0L
        var p = from
        while (p + 8 <= to) {
            var size = ((b[p].toLong() and 0xFF) shl 24) or ((b[p + 1].toLong() and 0xFF) shl 16) or
                ((b[p + 2].toLong() and 0xFF) shl 8) or (b[p + 3].toLong() and 0xFF)
            var header = 8
            if (size == 1L && p + 16 <= to) {
                size = 0
                for (i in 8 until 16) size = (size shl 8) or (b[p + i].toLong() and 0xFF)
                header = 16
            } else if (size == 0L) size = (to - p).toLong()
            if (size < header || p + size > to) break
            val end = p + size.toInt()
            val start = p + header
            val name = String(b, p + 4, 4, Charsets.ISO_8859_1)
            when {
                name == "udta" || name == "meta" -> {
                    "free".toByteArray().copyInto(b, p + 4)
                    java.util.Arrays.fill(b, start, end, 0)
                    removed += end - start
                }
                name == "trak" || name == "mdia" -> if (depth < 3) removed += walk(b, start, end, o, depth + 1)
                o.resetTimestamps && (name == "mvhd" || name == "tkhd" || name == "mdhd") -> {
                    val zeroLen = if (b[start].toInt() == 1) 16 else 8
                    if (start + 4 + zeroLen <= end) java.util.Arrays.fill(b, start + 4, start + 4 + zeroLen, 0)
                }
            }
            p = end
        }
        return removed
    }
}
