package com.rynekryz.exiless.core

import java.io.ByteArrayOutputStream
import java.io.IOException

internal object WebpStripper {
    private val keep = setOf("VP8 ", "VP8L", "VP8X", "ALPH", "ANIM", "ANMF", "ICCP")
    private const val FLAG_EXIF = 0x08
    private const val FLAG_XMP = 0x04

    fun run(r: Reader, s: Sink, o: StripOptions): Long {
        val head = r.readFully(12)
        if (Format.detect(head) != Format.WEBP) throw IOException("Not a WebP")
        val riffSize = (head[4].toLong() and 0xFF) or ((head[5].toLong() and 0xFF) shl 8) or
            ((head[6].toLong() and 0xFF) shl 16) or ((head[7].toLong() and 0xFF) shl 24)
        var left = riffSize - 4
        var removed = 0L
        var orientation = 0
        var flagsAt = -1
        val body = ByteArrayOutputStream()

        while (left >= 8) {
            val type = String(r.readFully(4), Charsets.ISO_8859_1)
            val size = r.u32le()
            val padded = size + (size and 1)
            if (padded > Int.MAX_VALUE || padded + 8 > left) throw IOException("Corrupt WebP chunk")
            val payload = r.readFully(padded.toInt())
            left -= 8 + padded
            when {
                type in keep -> {
                    if (type == "VP8X") flagsAt = body.size() + 8
                    body.write(type.toByteArray(Charsets.ISO_8859_1)); body.write(le32(size)); body.write(payload)
                }
                type == "EXIF" -> {
                    if (o.keepOrientation && orientation == 0) {
                        val off = if (payload.startsWith("Exif\u0000\u0000")) 6 else 0
                        orientation = Tiff.orientation(payload, off)
                    }
                    removed += 8 + padded
                }
                else -> removed += 8 + padded
            }
        }
        removed += r.drain() + left.coerceAtLeast(0)

        val out = body.toByteArray()
        var keepExif = false
        if (orientation > 1 && flagsAt >= 0) {
            val tiff = Tiff.minimal(orientation)
            val extra = ByteArrayOutputStream().apply {
                write("EXIF".toByteArray()); write(le32(tiff.size.toLong())); write(tiff)
            }.toByteArray()
            removed -= extra.size
            keepExif = true
            return finish(s, out + extra, flagsAt, keepExif, removed)
        }
        return finish(s, out, flagsAt, keepExif, removed)
    }

    private fun finish(s: Sink, body: ByteArray, flagsAt: Int, keepExif: Boolean, removed: Long): Long {
        if (flagsAt >= 0) {
            var f = body[flagsAt].toInt() and (FLAG_EXIF or FLAG_XMP).inv()
            if (keepExif) f = f or FLAG_EXIF
            body[flagsAt] = f.toByte()
        }
        s.write("RIFF".toByteArray()); s.u32le(body.size + 4L); s.write("WEBP".toByteArray()); s.write(body)
        return removed
    }

    private fun le32(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
}
