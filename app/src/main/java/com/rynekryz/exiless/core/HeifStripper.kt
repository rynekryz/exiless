package com.rynekryz.exiless.core

import java.io.IOException

internal object HeifStripper {
    private const val MAX_SIZE = 256L * 1024 * 1024

    private val EMPTY_EXIF = byteArrayOf(0, 0, 0, 0, 'M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0)

    private class Item(val id: Long, val wipe: Boolean, val isExif: Boolean)
    private class Extent(val itemId: Long, val method: Int, val base: Long, val offset: Long, val length: Long)

    fun run(r: Reader, s: Sink): Long {
        val data = r.readAll(MAX_SIZE)
        val removed = try { wipe(data) } catch (e: IndexOutOfBoundsException) {
            throw IOException("Unsupported HEIF structure", e)
        }
        s.write(data)
        return removed
    }

    private fun wipe(d: ByteArray): Long {
        val meta = findBox(d, 0, d.size, "meta") ?: return 0
        val inner = meta.first + 4 // version/flags
        val iinf = findBox(d, inner, meta.second, "iinf")
        val iloc = findBox(d, inner, meta.second, "iloc") ?: return 0
        val idat = findBox(d, inner, meta.second, "idat")
        val items = if (iinf != null) readItems(d, iinf.first, iinf.second) else emptyList()
        val wipeIds = items.filter { it.wipe }.map { it.id }.toSet()
        val exifIds = items.filter { it.isExif }.map { it.id }.toSet()
        if (wipeIds.isEmpty()) return 0
        var removed = 0L
        for (e in readExtents(d, iloc.first)) {
            if (e.itemId !in wipeIds) continue
            val start = when (e.method) {
                0 -> e.base + e.offset
                1 -> if (idat != null) idat.first + e.base + e.offset else continue
                else -> continue
            }
            val end = start + e.length
            if (start < 0 || end > d.size || e.length <= 0) continue
            java.util.Arrays.fill(d, start.toInt(), end.toInt(), 0)
            if (e.itemId in exifIds && e.length >= EMPTY_EXIF.size) EMPTY_EXIF.copyInto(d, start.toInt())
            removed += e.length
        }
        return removed
    }

    private fun findBox(d: ByteArray, from: Int, to: Int, type: String): Pair<Int, Int>? {
        var p = from
        while (p + 8 <= to) {
            var size = u32(d, p)
            var header = 8
            if (size == 1L) { size = u64(d, p + 8); header = 16 } else if (size == 0L) size = (to - p).toLong()
            if (size < header || p + size > to) return null
            if (d.startsWith(type, p + 4)) return Pair(p + header, (p + size).toInt())
            p += size.toInt()
        }
        return null
    }

    private fun readItems(d: ByteArray, start: Int, end: Int): List<Item> {
        val version = d[start].toInt() and 0xFF
        var p = start + 4
        val count = if (version == 0) u16(d, p).also { p += 2 }.toLong() else u32(d, p).also { p += 4 }
        val items = ArrayList<Item>()
        for (i in 0 until count) {
            if (p + 8 > end) break
            val size = u32(d, p).toInt()
            if (size < 8 || p + size > end || !d.startsWith("infe", p + 4)) break
            val boxEnd = p + size
            val v = d[p + 8].toInt() and 0xFF
            var q = p + 12
            if (v >= 2) {
                val id = if (v == 2) u16(d, q).toLong().also { q += 2 } else u32(d, q).also { q += 4 }
                q += 2 // protection index
                val type = String(d, q, 4, Charsets.ISO_8859_1); q += 4
                var wipe = type == "Exif"
                if (type == "mime") {
                    q = skipString(d, q, boxEnd) // item name
                    val ct = String(d, q, (skipString(d, q, boxEnd) - q - 1).coerceAtLeast(0), Charsets.ISO_8859_1)
                    wipe = ct.contains("xmp", ignoreCase = true) || ct.contains("rdf+xml", ignoreCase = true)
                }
                items += Item(id, wipe, type == "Exif")
            }
            p = boxEnd
        }
        return items
    }

    private fun skipString(d: ByteArray, from: Int, end: Int): Int {
        var q = from
        while (q < end && d[q].toInt() != 0) q++
        return minOf(q + 1, end)
    }

    private fun readExtents(d: ByteArray, start: Int): List<Extent> {
        val version = d[start].toInt() and 0xFF
        var p = start + 4
        val sizes = u16(d, p); p += 2
        val offSize = sizes shr 12
        val lenSize = (sizes shr 8) and 0xF
        val baseSize = (sizes shr 4) and 0xF
        val idxSize = if (version >= 1) sizes and 0xF else 0
        val count = if (version < 2) u16(d, p).also { p += 2 }.toLong() else u32(d, p).also { p += 4 }
        val out = ArrayList<Extent>()
        for (i in 0 until count) {
            val id = if (version < 2) u16(d, p).toLong().also { p += 2 } else u32(d, p).also { p += 4 }
            var method = 0
            if (version >= 1) { method = u16(d, p) and 0xF; p += 2 }
            p += 2 // data reference
            val base = uN(d, p, baseSize); p += baseSize
            val n = u16(d, p); p += 2
            for (j in 0 until n) {
                p += idxSize
                val off = uN(d, p, offSize); p += offSize
                val len = uN(d, p, lenSize); p += lenSize
                out += Extent(id, method, base, off, len)
            }
        }
        return out
    }

    private fun uN(d: ByteArray, p: Int, n: Int): Long {
        var v = 0L
        for (i in 0 until n) v = (v shl 8) or (d[p + i].toLong() and 0xFF)
        return v
    }

    private fun u16(d: ByteArray, p: Int) = ((d[p].toInt() and 0xFF) shl 8) or (d[p + 1].toInt() and 0xFF)
    private fun u32(d: ByteArray, p: Int) = uN(d, p, 4)
    private fun u64(d: ByteArray, p: Int) = uN(d, p, 8)
}
