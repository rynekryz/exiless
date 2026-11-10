package com.rynekryz.exiless.core

enum class Format(val isVideo: Boolean) {
    JPEG(false), PNG(false), WEBP(false), GIF(false), HEIF(false), MP4(true);

    companion object {
        private val heifBrands = setOf("heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "msf1", "avif", "avis")

        fun detect(head: ByteArray): Format? = when {
            head.size >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte() -> JPEG
            head.size >= 8 && head[0] == 0x89.toByte() && head.startsWith("PNG\r\n\u001a\n", 1) -> PNG
            head.startsWith("GIF8") -> GIF
            head.size >= 12 && head.startsWith("RIFF") && head.startsWith("WEBP", 8) -> WEBP
            head.size >= 12 && head.startsWith("ftyp", 4) ->
                if (String(head, 8, 4, Charsets.ISO_8859_1) in heifBrands) HEIF else MP4
            else -> null
        }
    }
}

data class StripOptions(
    val keepOrientation: Boolean = true,
    val resetTimestamps: Boolean = true,
)

data class StripReport(val removedBytes: Long)
