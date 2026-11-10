package com.rynekryz.exiless.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.rynekryz.exiless.core.Format
import com.rynekryz.exiless.core.MetadataStripper
import java.io.File
import java.io.IOException
import java.util.UUID

class Cleaner(private val context: Context, private val settings: Settings) {
    private val resolver = context.contentResolver
    private val cacheDir get() = File(context.cacheDir, "cleaned")

    fun open(uri: Uri): FileItem {
        var name = uri.lastPathSegment ?: "file"
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) {
                    it.getString(0)?.let { n -> name = n }
                    size = it.getLong(1)
                }
            }
        }
        val format = runCatching {
            resolver.openInputStream(uri)?.use { s ->
                val head = ByteArray(16)
                var n = 0
                while (n < head.size) {
                    val r = s.read(head, n, head.size - n)
                    if (r < 0) break
                    n += r
                }
                Format.detect(head.copyOf(n))
            }
        }.getOrNull()
        return FileItem(uri, name, size, format)
    }

    fun analyze(item: FileItem): FileItem {
        val format = item.format ?: return item
        return try {
            val report = resolver.openInputStream(item.uri)!!.use { MetadataStripper.strip(format, it, null, settings.options) }
            val info = MetadataReader.read(context, item.uri, format)
            item.copy(analyzing = false, removable = report.removedBytes, hasGps = info.hasGps)
        } catch (_: Exception) {
            item.copy(analyzing = false, failed = true)
        }
    }

    fun clean(item: FileItem): FileItem {
        val format = item.format ?: return item
        val out = File(File(cacheDir, UUID.randomUUID().toString()).also { it.mkdirs() }, outputName(item.name))
        return try {
            val report = resolver.openInputStream(item.uri)!!.use { input ->
                out.outputStream().use { MetadataStripper.strip(format, input, it, settings.options) }
            }
            item.copy(cleaned = out, removed = report.removedBytes, failed = false)
        } catch (_: Exception) {
            out.delete()
            item.copy(failed = true)
        }
    }

    fun save(item: FileItem, tree: Uri): Boolean = try {
        val file = item.cleaned ?: throw IOException("not cleaned")
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        val target = DocumentsContract.createDocument(resolver, parent, mime, file.name) ?: throw IOException("create failed")
        resolver.openOutputStream(target)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        true
    } catch (_: Exception) {
        false
    }

    fun shareUri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun clearCache() { cacheDir.deleteRecursively() }

    private fun outputName(name: String): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        return (base + settings.suffix + ext).replace('/', '_')
    }
}
