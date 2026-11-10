package com.rynekryz.exiless.ui

import android.app.Application
import android.net.Uri
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rynekryz.exiless.R
import com.rynekryz.exiless.data.Cleaner
import com.rynekryz.exiless.data.FileItem
import com.rynekryz.exiless.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val cleaner = Cleaner(app, settings)
    private val work = Mutex()

    private val _items = MutableStateFlow<List<FileItem>>(emptyList())
    val items = _items.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) { cleaner.clearCache() }
    }

    fun add(uris: List<Uri>) {
        viewModelScope.launch {
            val known = _items.value.map { it.uri }.toSet()
            val fresh = withContext(Dispatchers.IO) { uris.distinct().filter { it !in known }.map(cleaner::open) }
            _items.update { it + fresh }
            for (item in fresh) {
                launch(Dispatchers.IO) { replace(cleaner.analyze(item)) }
            }
        }
    }

    fun cleanAll() {
        val targets = _items.value.filter { it.format != null && it.cleaned == null && !it.analyzing }
        if (targets.isEmpty()) return say(app.getString(R.string.msg_nothing_clean))
        viewModelScope.launch(Dispatchers.IO) {
            work.withLock {
                var done = 0; var bytes = 0L; var gps = 0
                for (t in targets) {
                    val r = cleaner.clean(t)
                    replace(r)
                    if (r.cleaned != null) { done++; bytes += r.removed; if (r.hasGps) gps++ }
                }
                settings.addStats(done, bytes, gps)
                say(app.getString(R.string.msg_cleaned, done))
            }
        }
    }

    fun clean(item: FileItem) {
        if (item.format == null || item.cleaned != null) return
        viewModelScope.launch(Dispatchers.IO) {
            work.withLock {
                val r = cleaner.clean(item)
                replace(r)
                if (r.cleaned != null) settings.addStats(1, r.removed, if (r.hasGps) 1 else 0)
            }
        }
    }

    fun save(tree: Uri, only: FileItem? = null) {
        val targets = _items.value.filter { it.cleaned != null && !it.saved && (only == null || it.uri == only.uri) }
        if (targets.isEmpty()) return say(app.getString(R.string.msg_nothing_save))
        viewModelScope.launch(Dispatchers.IO) {
            work.withLock {
                var ok = 0
                for (t in targets) if (cleaner.save(t, tree)) { ok++; replace(t.copy(saved = true)) }
                say(app.getString(R.string.msg_saved, ok))
                if (ok < targets.size) say(app.getString(R.string.msg_save_failed, targets.size - ok))
            }
        }
    }

    fun shareUris(only: FileItem? = null): List<Uri> =
        _items.value.filter { it.cleaned != null && (only == null || it.uri == only.uri) }.map { cleaner.shareUri(it.cleaned!!) }

    fun hasCleaned() = _items.value.any { it.cleaned != null }

    fun remove(item: FileItem) = _items.update { l -> l.filter { it.uri != item.uri } }
    fun clear() = _items.update { emptyList() }

    fun summary(item: FileItem): String {
        val parts = ArrayList<String>()
        when {
            item.format == null -> return app.getString(R.string.status_unsupported)
            item.analyzing -> return app.getString(R.string.status_analyzing)
            item.failed -> return app.getString(R.string.status_failed)
            item.cleaned != null -> {
                parts += app.getString(R.string.status_cleaned, Formatter.formatShortFileSize(app, item.removed))
                if (item.saved) parts += app.getString(R.string.status_saved)
            }
            item.removable == 0L -> parts += app.getString(R.string.status_clean_already)
            else -> {
                parts += app.getString(R.string.status_removable, Formatter.formatShortFileSize(app, item.removable))
                if (item.hasGps) parts += app.getString(R.string.status_gps)
            }
        }
        return parts.joinToString(app.getString(R.string.separator))
    }

    private fun replace(updated: FileItem) = _items.update { l -> l.map { if (it.uri == updated.uri) updated else it } }
    private fun say(text: String) { _messages.trySend(text) }
}
