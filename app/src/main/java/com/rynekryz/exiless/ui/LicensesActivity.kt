package com.rynekryz.exiless.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rynekryz.exiless.R
import com.rynekryz.exiless.databinding.ItemLicenseBinding
import dev.oneuiproject.oneui.layout.ToolbarLayout

class LicensesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_licenses)
        findViewById<ToolbarLayout>(R.id.toolbar_layout).apply {
            setTitle(getString(R.string.licenses_title))
            showNavigationButtonAsBack = true
        }
        findViewById<RecyclerView>(R.id.licenses_list).apply {
            layoutManager = LinearLayoutManager(this@LicensesActivity)
            adapter = LicenseAdapter()
        }
    }

    private class Entry(val name: String, val label: String, val text: Int)

    private class VH(val binding: ItemLicenseBinding) : RecyclerView.ViewHolder(binding.root)

    private inner class LicenseAdapter : RecyclerView.Adapter<VH>() {
        private val entries = listOf(
            Entry(getString(R.string.app_name), getString(R.string.license_gpl), R.raw.license_gpl3),
            Entry("One UI Design library (io.github.tribalfs:oneui-design)", getString(R.string.license_mit), R.raw.license_mit),
            Entry("SESL AndroidX (sesl.androidx.*)", getString(R.string.license_apache), R.raw.license_apache2),
            Entry("SESL Material Components (sesl.com.google.android.material)", getString(R.string.license_apache), R.raw.license_apache2),
            Entry("AndroidX, ExifInterface, CameraX", getString(R.string.license_apache), R.raw.license_apache2),
            Entry("Kotlin standard library and kotlinx.coroutines", getString(R.string.license_apache), R.raw.license_apache2),
            Entry("ZXing core", getString(R.string.license_apache), R.raw.license_apache2),
            Entry("Lottie for Android", getString(R.string.license_apache), R.raw.license_apache2),
            Entry("HiddenApiRefinePlugin runtime (dev.rikka.tools.refine)", getString(R.string.license_mit), R.raw.license_mit),
            Entry("Google Sans Flex (res/font/google_sans_flex.ttf)", getString(R.string.license_ofl), R.raw.license_ofl11),
            Entry("One UI Sample App (display help images)", getString(R.string.license_apache), R.raw.license_apache2),
            Entry("One UI icons (io.github.oneuiproject:icons)", getString(R.string.license_apache), R.raw.license_apache2),
            Entry("Material Symbols (app icon glyph)", getString(R.string.license_apache), R.raw.license_apache2),
        )
        private val expanded = mutableSetOf<Int>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemLicenseBinding.inflate(layoutInflater, parent, false))

        override fun getItemCount() = entries.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val entry = entries[position]
            holder.binding.name.text = entry.name
            holder.binding.licenseLabel.text = entry.label
            val open = position in expanded
            if (open && holder.binding.licenseText.text.isEmpty()) {
                holder.binding.licenseText.text =
                    resources.openRawResource(entry.text).bufferedReader().use { it.readText() }
            }
            holder.binding.licenseText.visibility = if (open) View.VISIBLE else View.GONE
            holder.binding.root.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                if (!expanded.add(pos)) expanded.remove(pos)
                notifyItemChanged(pos)
            }
        }
    }
}
