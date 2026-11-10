package com.rynekryz.exiless.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.widget.ImageView
import androidx.exifinterface.media.ExifInterface
import com.rynekryz.exiless.R
import com.rynekryz.exiless.data.FileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Thumbnails(private val context: Context, private val scope: CoroutineScope, targetDp: Int = 56) {
    private val cache = LruCache<Uri, Bitmap>(80)
    private val target = (context.resources.displayMetrics.density * targetDp).toInt()

    fun cached(uri: Uri): Bitmap? = cache.get(uri)

    fun load(view: ImageView, item: FileItem) {
        view.tag = item.uri
        val cached = cache.get(item.uri)
        if (cached != null) return show(view, cached)
        view.scaleType = ImageView.ScaleType.CENTER
        view.setImageResource(R.drawable.ic_image)
        if (item.format == null) return
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { runCatching { decode(item) }.getOrNull() } ?: return@launch
            cache.put(item.uri, bitmap)
            if (view.tag == item.uri) show(view, bitmap)
        }
    }

    fun loadLarge(item: FileItem, onReady: (Bitmap) -> Unit) {
        val big = maxOf(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { runCatching { decode(item, big) }.getOrNull() } ?: return@launch
            onReady(bitmap)
        }
    }

    private fun show(view: ImageView, bitmap: Bitmap) {
        view.scaleType = ImageView.ScaleType.CENTER_CROP
        view.setImageBitmap(bitmap)
    }

    private fun decode(item: FileItem, targetPx: Int = target): Bitmap? {
        val target = targetPx
        if (item.format?.isVideo == true) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, item.uri)
                val frame = r.getFrameAtTime(0) ?: return null
                val scale = target.toFloat() / maxOf(frame.width, frame.height)
                return if (scale < 1f) Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1), true) else frame
            } finally {
                r.release()
            }
        }
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
        val bitmap = resolver.openInputStream(item.uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val degrees = runCatching { resolver.openInputStream(item.uri)?.use { ExifInterface(it).rotationDegrees } }.getOrNull() ?: 0
        if (degrees == 0) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    }
}
