package com.rynekryz.exiless.core

import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

internal class Reader(private val src: InputStream) {
    private val buf = ByteArray(BUFFER)
    private var pos = 0
    private var lim = 0

    private fun fill(): Boolean {
        val n = src.read(buf, 0, buf.size)
        pos = 0
        lim = if (n > 0) n else 0
        return lim > 0
    }

    fun read(): Int {
        if (pos >= lim && !fill()) return -1
        return buf[pos++].toInt() and 0xFF
    }

    fun u8(): Int = read().also { if (it < 0) throw EOFException() }
    fun u16(): Int = (u8() shl 8) or u8()
    fun u32(): Long = (u16().toLong() shl 16) or u16().toLong()
    fun u32le(): Long = u8().toLong() or (u8().toLong() shl 8) or (u8().toLong() shl 16) or (u8().toLong() shl 24)
    fun u64(): Long = (u32() shl 32) or u32()

    fun readFully(n: Int): ByteArray {
        require(n >= 0) { "negative length" }
        val out = ByteArray(n)
        var done = 0
        while (done < n) {
            if (pos >= lim && !fill()) throw EOFException()
            val c = minOf(n - done, lim - pos)
            System.arraycopy(buf, pos, out, done, c)
            pos += c
            done += c
        }
        return out
    }

    fun skip(n: Long) {
        var left = n
        while (left > 0) {
            if (pos < lim) {
                val c = minOf(left, (lim - pos).toLong()).toInt()
                pos += c
                left -= c
                continue
            }
            val s = src.skip(left)
            if (s > 0) left -= s else if (read() < 0) throw EOFException() else left--
        }
    }

    fun copyTo(sink: Sink, n: Long) {
        if (!sink.live) return skip(n)
        var left = n
        while (left > 0) {
            if (pos >= lim && !fill()) throw EOFException()
            val c = minOf(left, (lim - pos).toLong()).toInt()
            sink.write(buf, pos, c)
            pos += c
            left -= c
        }
    }

    fun readAll(max: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        while (pos < lim || fill()) {
            out.write(buf, pos, lim - pos)
            pos = lim
            if (out.size() > max) throw java.io.IOException("File too large")
        }
        return out.toByteArray()
    }

    fun copyRest(sink: Sink) {
        while (pos < lim || fill()) {
            sink.write(buf, pos, lim - pos)
            pos = lim
        }
    }

    fun drain(): Long {
        var total = (lim - pos).toLong()
        pos = lim
        while (true) {
            val n = src.read(buf, 0, buf.size)
            if (n <= 0) return total
            total += n
        }
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}

internal class Sink(target: OutputStream?) {
    private val out = target?.let { BufferedOutputStream(it, 64 * 1024) }
    val live: Boolean get() = out != null

    fun write(b: Int) { out?.write(b) }
    fun write(b: ByteArray, off: Int = 0, len: Int = b.size) { out?.write(b, off, len) }
    fun u16(v: Int) { write(v ushr 8); write(v) }
    fun u32(v: Long) { u16((v ushr 16).toInt() and 0xFFFF); u16(v.toInt() and 0xFFFF) }
    fun u32le(v: Long) { write(v.toInt()); write((v ushr 8).toInt()); write((v ushr 16).toInt()); write((v ushr 24).toInt()) }

    fun zeros(n: Long) {
        if (!live) return
        val z = ByteArray(8192)
        var left = n
        while (left > 0) {
            val c = minOf(left, z.size.toLong()).toInt()
            out!!.write(z, 0, c)
            left -= c
        }
    }

    fun flush() { out?.flush() }
}

internal fun ByteArray.startsWith(prefix: String, at: Int = 0): Boolean {
    if (size - at < prefix.length) return false
    for (i in prefix.indices) if (this[at + i] != prefix[i].code.toByte()) return false
    return true
}

internal fun fourcc(s: String): Int =
    (s[0].code shl 24) or (s[1].code shl 16) or (s[2].code shl 8) or s[3].code
