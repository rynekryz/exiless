package com.rynekryz.exiless.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.StringRes
import androidx.exifinterface.media.ExifInterface
import com.rynekryz.exiless.R
import com.rynekryz.exiless.core.Format

class Section(@StringRes val title: Int, val rows: List<Pair<String, String>>)

class MetadataInfo(val sections: List<Section>, val latLong: DoubleArray?) {
    val hasGps: Boolean get() = latLong != null
    companion object { val EMPTY = MetadataInfo(emptyList(), null) }
}

object MetadataReader {
    fun read(context: Context, uri: Uri, format: Format): MetadataInfo = try {
        when {
            format.isVideo -> readVideo(context, uri)
            format == Format.GIF -> MetadataInfo.EMPTY
            else -> readExif(context, uri)
        }
    } catch (_: Exception) {
        MetadataInfo.EMPTY
    }

    private fun readExif(context: Context, uri: Uri): MetadataInfo {
        val exif = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) } ?: return MetadataInfo.EMPTY
        fun rows(vararg tags: Pair<String, String>) =
            tags.mapNotNull { (label, tag) -> exif.getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() }?.let { label to it } }

        val camera = rows(
            "Make" to ExifInterface.TAG_MAKE, "Model" to ExifInterface.TAG_MODEL,
            "Lens" to ExifInterface.TAG_LENS_MODEL, "Aperture" to ExifInterface.TAG_F_NUMBER,
            "Exposure" to ExifInterface.TAG_EXPOSURE_TIME, "ISO" to ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            "Focal length" to ExifInterface.TAG_FOCAL_LENGTH, "Body serial" to ExifInterface.TAG_BODY_SERIAL_NUMBER,
            "Lens serial" to ExifInterface.TAG_LENS_SERIAL_NUMBER,
        )
        val date = rows(
            "Taken" to ExifInterface.TAG_DATETIME_ORIGINAL, "Modified" to ExifInterface.TAG_DATETIME,
            "Digitized" to ExifInterface.TAG_DATETIME_DIGITIZED,
        )
        val latLong = exif.latLong
        val location = buildList {
            latLong?.let { add("Coordinates" to "%.5f, %.5f".format(it[0], it[1])) }
            val alt = exif.getAltitude(Double.NaN)
            if (!alt.isNaN()) add("Altitude" to "%.0f m".format(alt))
        }
        val author = rows(
            "Software" to ExifInterface.TAG_SOFTWARE, "Artist" to ExifInterface.TAG_ARTIST,
            "Owner" to ExifInterface.TAG_CAMERA_OWNER_NAME, "Copyright" to ExifInterface.TAG_COPYRIGHT,
            "Description" to ExifInterface.TAG_IMAGE_DESCRIPTION, "Comment" to ExifInterface.TAG_USER_COMMENT,
        ) + listOfNotNull(if (exif.getAttribute(ExifInterface.TAG_XMP) != null) "XMP" to "present" else null)

        return MetadataInfo(
            listOf(
                Section(R.string.section_camera, camera),
                Section(R.string.section_date, date),
                Section(R.string.section_location, location),
                Section(R.string.section_author, author),
            ).filter { it.rows.isNotEmpty() },
            latLong,
        )
    }

    private fun readVideo(context: Context, uri: Uri): MetadataInfo {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            fun get(key: Int) = r.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
            val location = get(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            val latLong = location?.let(::parseIso6709)
            val rows = listOfNotNull(
                get(MediaMetadataRetriever.METADATA_KEY_DATE)?.let { "Date" to it },
                location?.let { "Location" to it },
                get(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { "Duration" to "%.1f s".format(it / 1000.0) },
                get(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let { "Type" to it },
            )
            return MetadataInfo(listOf(Section(R.string.section_video, rows)).filter { it.rows.isNotEmpty() }, latLong)
        } finally {
            r.release()
        }
    }

    private fun parseIso6709(s: String): DoubleArray? {
        val m = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)").find(s) ?: return null
        return doubleArrayOf(m.groupValues[1].toDouble(), m.groupValues[2].toDouble())
    }
}
