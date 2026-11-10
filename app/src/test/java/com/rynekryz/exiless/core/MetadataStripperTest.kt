package com.rynekryz.exiless.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

class MetadataStripperTest {

    private fun strip(format: Format, input: ByteArray, options: StripOptions = StripOptions()): Pair<ByteArray, StripReport> {
        val out = ByteArrayOutputStream()
        val report = MetadataStripper.strip(format, input.inputStream(), out, options)
        val dry = MetadataStripper.strip(format, input.inputStream(), null, options)
        assertEquals("dry run must match", report.removedBytes, dry.removedBytes)
        return out.toByteArray() to report
    }

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun ascii(s: String) = s.toByteArray(Charsets.ISO_8859_1)
    private fun contains(hay: ByteArray, needle: String) = String(hay, Charsets.ISO_8859_1).contains(needle)

    private fun jpegSegment(marker: Int, payload: ByteArray) =
        bytes(0xFF, marker, (payload.size + 2) shr 8, (payload.size + 2) and 0xFF) + payload

    private fun exif(orientation: Int) =
        ascii("Exif\u0000\u0000") + bytes(
            'I'.code, 'I'.code, 42, 0, 8, 0, 0, 0, 2, 0,
            0x0F, 0x01, 2, 0, 5, 0, 0, 0, 26, 0, 0, 0,
            0x12, 0x01, 3, 0, 1, 0, 0, 0, orientation, 0, 0, 0, 0, 0, 0, 0,
        ) + ascii("Evil\u0000")

    @Test fun detectsFormats() {
        assertEquals(Format.JPEG, Format.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals(Format.GIF, Format.detect(ascii("GIF89a......")))
        assertEquals(Format.WEBP, Format.detect(ascii("RIFF\u0000\u0000\u0000\u0000WEBP")))
        assertEquals(Format.HEIF, Format.detect(bytes(0, 0, 0, 24) + ascii("ftypheic")))
        assertEquals(Format.MP4, Format.detect(bytes(0, 0, 0, 24) + ascii("ftypisom")))
        assertEquals(null, Format.detect(ascii("hello world!")))
    }

    @Test fun jpegDropsMetadataKeepsOrientationAndScan() {
        val scan = bytes(0x11, 0xFF, 0x00, 0x22, 0xFF, 0xD0, 0x33)
        val input = bytes(0xFF, 0xD8) +
            jpegSegment(0xE0, ascii("JFIF\u0000") + bytes(1, 1, 0, 0, 1, 0, 1, 0, 0)) +
            jpegSegment(0xE1, exif(6)) +
            jpegSegment(0xE1, ascii("http://ns.adobe.com/xap/1.0/\u0000<x>secret</x>")) +
            jpegSegment(0xFE, ascii("a comment")) +
            jpegSegment(0xDB, bytes(0, 1, 2, 3)) +
            jpegSegment(0xDA, bytes(1, 1, 0, 0, 63, 0)) + scan +
            bytes(0xFF, 0xD9) + ascii("TRAILING VIDEO")
        val (out, report) = strip(Format.JPEG, input)
        assertFalse(contains(out, "secret"))
        assertFalse(contains(out, "Evil"))
        assertFalse(contains(out, "comment"))
        assertFalse(contains(out, "TRAILING"))
        assertTrue(contains(out, "JFIF"))
        assertTrue(contains(out, "Exif"))
        assertTrue(out.copyOfRange(0, 2).contentEquals(bytes(0xFF, 0xD8)))
        assertArrayEquals(bytes(0xFF, 0xD9), out.copyOfRange(out.size - 2, out.size))
        assertTrue(String(out, Charsets.ISO_8859_1).contains(String(scan, Charsets.ISO_8859_1)))
        assertEquals((input.size - out.size).toLong(), report.removedBytes)
        assertEquals(6, Tiff.orientation(out, out.indexOfSubArray(ascii("Exif\u0000\u0000")) + 6))
    }

    @Test fun jpegWithoutKeepOrientationHasNoExif() {
        val input = bytes(0xFF, 0xD8) + jpegSegment(0xE1, exif(6)) + jpegSegment(0xDA, bytes(1, 1, 0, 0, 63, 0)) + bytes(1, 0xFF, 0xD9)
        val (out, _) = strip(Format.JPEG, input, StripOptions(keepOrientation = false))
        assertFalse(contains(out, "Exif"))
    }

    private fun pngChunk(type: String, data: ByteArray): ByteArray {
        val crc = CRC32().apply { update(ascii(type)); update(data) }.value
        val len = data.size
        return bytes(len ushr 24, len ushr 16, len ushr 8, len) + ascii(type) + data +
            bytes((crc ushr 24).toInt(), (crc ushr 16).toInt(), (crc ushr 8).toInt(), crc.toInt())
    }

    @Test fun pngKeepsRenderingChunksDropsTextAndTrailer() {
        val input = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
            pngChunk("IHDR", ByteArray(13)) + pngChunk("tEXt", ascii("Author\u0000secret")) +
            pngChunk("acTL", ByteArray(8)) + pngChunk("IDAT", bytes(1, 2, 3)) + pngChunk("tIME", ByteArray(7)) +
            pngChunk("IEND", ByteArray(0)) + ascii("junk")
        val (out, report) = strip(Format.PNG, input)
        assertFalse(contains(out, "secret"))
        assertFalse(contains(out, "tIME"))
        assertTrue(contains(out, "acTL"))
        assertEquals((input.size - out.size).toLong(), report.removedBytes)
    }

    @Test fun gifDropsCommentsKeepsLoop() {
        val loop = bytes(0x21, 0xFF, 11) + ascii("NETSCAPE2.0") + bytes(3, 1, 0, 0, 0)
        val comment = bytes(0x21, 0xFE, 6) + ascii("secret") + bytes(0)
        val frame = bytes(0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x44, 0x01, 0)
        val input = ascii("GIF89a") + bytes(1, 0, 1, 0, 0, 0, 0) + loop + comment + frame + bytes(0x3B)
        val (out, report) = strip(Format.GIF, input)
        assertFalse(contains(out, "secret"))
        assertTrue(contains(out, "NETSCAPE2.0"))
        assertEquals((input.size - out.size).toLong(), report.removedBytes)
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val n = payload.size + 8
        return bytes(n ushr 24, n ushr 16, n ushr 8, n) + ascii(type) + payload
    }

    @Test fun mp4ZeroesUserDataAndTimestampsInPlace() {
        val mvhd = box("mvhd", bytes(0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 9))
        val udta = box("udta", box("\u00A9xyz", ascii("+48.85+002.29/")))
        val input = box("ftyp", ascii("isom") + ByteArray(4)) + box("moov", mvhd + udta) + box("mdat", bytes(1, 2, 3))
        val (out, report) = strip(Format.MP4, input)
        assertEquals(input.size, out.size)
        assertFalse(contains(out, "48.85"))
        assertTrue(contains(out, "free"))
        assertEquals(udta.size - 8L, report.removedBytes)
        assertEquals(0, out[input.indexOfSubArray(ascii("mvhd")) + 8].toInt())
        assertEquals(0, out[input.indexOfSubArray(ascii("mvhd")) + 8 + 4].toInt())
    }

    private fun ByteArray.indexOfSubArray(sub: ByteArray): Int {
        outer@ for (i in 0..size - sub.size) {
            for (j in sub.indices) if (this[i + j] != sub[j]) continue@outer
            return i
        }
        return -1
    }
}
