package com.rynekryz.exiless.data

import android.net.Uri
import com.rynekryz.exiless.core.Format
import java.io.File

data class FileItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val format: Format?,
    val analyzing: Boolean = format != null,
    val removable: Long = 0,
    val hasGps: Boolean = false,
    val cleaned: File? = null,
    val removed: Long = 0,
    val saved: Boolean = false,
    val failed: Boolean = false,
)
