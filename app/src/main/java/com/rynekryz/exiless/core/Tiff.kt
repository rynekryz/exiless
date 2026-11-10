package com.rynekryz.exiless.core

internal object Tiff {
    private const val TAG_ORIENTATION = 0x0112

    fun orientation(b: ByteArray, off: Int = 0): Int {
        try {
            if (b.size - off < 8) return 0
            val le = when {
                b[off] == 'I'.code.toByte() && b[off + 1] == 'I'.code.toByte() -> true
                b[off] == 'M'.code.toByte() && b[off + 1] == 'M'.code.toByte() -> false
                else -> return 0
            }
            fun u16(p: Int): Int {
                val a = b[p].toInt() and 0xFF
                val c = b[p + 1].toInt() and 0xFF
                return if (le) a or (c shl 8) else (a shl 8) or c
            }
            fun u32(p: Int): Int = if (le) u16(p) or (u16(p + 2) shl 16) else (u16(p) shl 16) or u16(p + 2)
            if (u16(off + 2) != 42) return 0
            val ifd = off + u32(off + 4)
            val count = u16(ifd)
            for (i in 0 until count) {
                val e = ifd + 2 + i * 12
                if (u16(e) == TAG_ORIENTATION) return u16(e + 8).takeIf { it in 1..8 } ?: 0
            }
        } catch (_: IndexOutOfBoundsException) {
        }
        return 0
    }

    fun minimal(orientation: Int): ByteArray = byteArrayOf(
        'M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 8,
        0, 1,
        0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation.toByte(), 0, 0,
        0, 0, 0, 0,
    )
}
