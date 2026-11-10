package com.rynekryz.exiless.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rynekryz.exiless.data.FileItem
import com.rynekryz.exiless.databinding.ItemFileBinding
import com.rynekryz.exiless.databinding.ItemFileGridBinding

class FileAdapter(
    private val thumbnails: Thumbnails,
    private val summary: (FileItem) -> String,
    private val onClick: (FileItem) -> Unit,
    private val onHold: (FileItem) -> Unit,
) : ListAdapter<FileItem, RecyclerView.ViewHolder>(Diff) {

    var viewMode: String = "grid"
        private set

    fun setViewMode(mode: String) {
        if (viewMode == mode) return
        viewMode = mode
        notifyDataSetChanged()
    }

    class ListHolder(val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root)

    class GridHolder(val binding: ItemFileGridBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int) = if (viewMode == "grid") TYPE_GRID else TYPE_LIST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GRID) {
            GridHolder(ItemFileGridBinding.inflate(inflater, parent, false))
        } else {
            ListHolder(ItemFileBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is GridHolder -> {
                holder.binding.badgeGps.visibility = if (item.hasGps && item.cleaned == null) View.VISIBLE else View.GONE
                holder.binding.badgeDone.visibility = if (item.cleaned != null) View.VISIBLE else View.GONE
                holder.binding.status.text = summary(item)
                thumbnails.load(holder.binding.thumb, item)
                holder.binding.root.setOnClickListener { onClick(item) }
                holder.binding.root.setOnLongClickListener { onHold(item); true }
            }
            is ListHolder -> {
                holder.binding.name.text = item.name
                holder.binding.status.text = summary(item)
                thumbnails.load(holder.binding.thumb, item)
                holder.binding.root.setOnClickListener { onClick(item) }
                holder.binding.root.setOnLongClickListener { onHold(item); true }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(a: FileItem, b: FileItem) = a.uri == b.uri
        override fun areContentsTheSame(a: FileItem, b: FileItem) = a == b
    }

    private companion object {
        const val TYPE_LIST = 0
        const val TYPE_GRID = 1
    }
}
