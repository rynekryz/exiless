package com.rynekryz.exiless.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.rynekryz.exiless.R
import com.rynekryz.exiless.data.FileItem
import com.rynekryz.exiless.data.MetadataInfo
import com.rynekryz.exiless.data.MetadataReader
import com.rynekryz.exiless.data.Settings
import com.rynekryz.exiless.databinding.FragmentGalleryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryFragment : Fragment() {
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    private lateinit var settings: Settings
    private lateinit var adapter: FileAdapter
    private var actions: MainActions? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.add(it) }

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        actions = context as? MainActions
    }

    override fun onDetach() {
        actions = null
        super.onDetach()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = Settings(requireContext())
        adapter = FileAdapter(Thumbnails(requireContext(), viewLifecycleOwner.lifecycleScope, 120), vm::summary, ::showDetails)
        adapter.setViewMode(settings.viewMode)
        applyLayoutManager()
        binding.list.adapter = adapter
        binding.emptyAdd.setOnClickListener { actions?.addFiles() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.items.collect { render(it) }
            }
        }
    }

    fun onMenuItem(itemId: Int): Boolean = when (itemId) {
        R.id.action_view_mode -> {
            val mode = if (adapter.viewMode == "grid") "list" else "grid"
            settings.setViewMode(mode)
            adapter.setViewMode(mode)
            applyLayoutManager()
            true
        }
        R.id.action_add -> { actions?.addFiles(); true }
        R.id.menu_save_all -> { actions?.saveAll(); true }
        R.id.menu_share_all -> { actions?.shareAll(); true }
        R.id.menu_clean_all -> { vm.cleanAll(); true }
        R.id.menu_clear -> { actions?.clearList(); true }
        else -> false
    }

    private fun applyLayoutManager() {
        binding.list.layoutManager = if (adapter.viewMode == "grid") {
            GridLayoutManager(requireContext(), 3)
        } else {
            LinearLayoutManager(requireContext())
        }
    }

    private fun render(items: List<FileItem>) {
        adapter.submitList(items)
        binding.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyAdd.setOnClickListener { actions?.addFiles() }
        val gps = items.count { it.hasGps && it.cleaned == null }
        binding.summary.text = when {
            items.isEmpty() -> null
            gps > 0 -> getString(R.string.main_summary_gps, items.size, gps)
            else -> resources.getQuantityString(R.plurals.main_summary_files, items.size, items.size)
        }
        binding.summary.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showDetails(item: FileItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val info = item.format?.let {
                withContext(Dispatchers.IO) { MetadataReader.read(requireContext(), item.uri, it) }
            } ?: MetadataInfo.EMPTY
            val builder = AlertDialog.Builder(requireContext())
                .setTitle(item.name)
                .setMessage(describe(item, info))
                .setNegativeButton(R.string.details_remove) { _, _ -> vm.remove(item) }
            when {
                item.cleaned != null -> builder
                    .setPositiveButton(R.string.details_save) { _, _ -> actions?.saveOne(item) }
                    .setNeutralButton(R.string.details_share) { _, _ -> share(item) }
                item.format != null -> {
                    builder.setPositiveButton(R.string.details_clean) { _, _ -> vm.clean(item) }
                    info.latLong?.let { ll ->
                        builder.setNeutralButton(R.string.details_open_map) { _, _ -> openMap(ll) }
                    }
                }
            }
            builder.show()
        }
    }

    private fun describe(item: FileItem, info: MetadataInfo): String {
        val text = StringBuilder()
        for (section in info.sections) {
            if (text.isNotEmpty()) text.append("\n\n")
            text.append(getString(section.title).uppercase())
            for ((label, value) in section.rows) text.append('\n').append(label).append(": ").append(value)
        }
        if (text.isEmpty()) text.append(getString(R.string.details_none))
        if (item.removable > 0) {
            text.append("\n\n").append(getString(R.string.details_hidden, android.text.format.Formatter.formatShortFileSize(requireContext(), item.removable)))
        }
        return text.toString()
    }

    private fun openMap(ll: DoubleArray) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, "geo:${ll[0]},${ll[1]}".toUri()))
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.msg_no_map_app, Toast.LENGTH_SHORT).show()
        }
    }

    private fun share(item: FileItem?) {
        val uris = vm.shareUris(item)
        if (uris.isEmpty()) return Toast.makeText(requireContext(), R.string.msg_nothing_save, Toast.LENGTH_SHORT).show()
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = "*/*"
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, null))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = GalleryFragment()
    }
}

interface MainActions {
    fun addFiles()
    fun saveAll()
    fun saveOne(item: FileItem)
    fun shareAll()
    fun clearList()
}
