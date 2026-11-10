package com.rynekryz.exiless.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rynekryz.exiless.R
import com.rynekryz.exiless.data.FileItem
import com.rynekryz.exiless.data.Settings
import com.rynekryz.exiless.databinding.ActivityMainBinding
import dev.oneuiproject.oneui.layout.NavDrawerLayout
import dev.oneuiproject.oneui.R as iconsR
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), MainActions {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: Settings
    private val vm: MainViewModel by viewModels()
    private var afterFolderPicked: ((Uri) -> Unit)? = null
    private lateinit var gallery: GalleryFragment
    private lateinit var stats: StatsFragment
    private var galleryTab = true

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.add(it) }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val action = afterFolderPicked
        afterFolderPicked = null
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            settings.outputFolder = uri
            action?.invoke(uri)
        }
    }

    private val folderImport = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        if (!settings.oobeCompleted) {
            startActivity(Intent(this, OOBEActivity::class.java))
            finish()
            return
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initDrawer()
        initTabs()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.messages.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
            }
        }

        if (savedInstanceState == null) handleShare(intent)
    }

    private fun initDrawer() {
        binding.drawerLayout.apply {
            setupHeaderButton(
                icon = AppCompatResources.getDrawable(context, iconsR.drawable.ic_oui_info_outline)!!,
                tooltipText = getString(R.string.menu_about),
                listener = { startActivity(Intent(this@MainActivity, AboutActivity::class.java)) },
            )
            closeNavRailOnBack = true
        }
        binding.navigationView.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.close()
            when (item.itemId) {
                R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.nav_about -> startActivity(Intent(this, AboutActivity::class.java))
            }
            true
        }
    }

    private fun initTabs() {
        gallery = supportFragmentManager.findFragmentByTag(TAG_GALLERY) as? GalleryFragment ?: GalleryFragment.newInstance()
        stats = supportFragmentManager.findFragmentByTag(TAG_STATS) as? StatsFragment ?: StatsFragment.newInstance()

        if (!gallery.isAdded) supportFragmentManager.beginTransaction().add(R.id.container, gallery, TAG_GALLERY).hide(gallery).commit()
        if (!stats.isAdded) supportFragmentManager.beginTransaction().add(R.id.container, stats, TAG_STATS).hide(stats).commitNow()

        binding.bottomTab.inflateMenu(R.menu.menu_bottom_tabs) { item ->
            showTab(if (item.itemId == R.id.tab_stats) stats else gallery)
            true
        }
        binding.bottomTab.setSelectedItem(0)
        showTab(gallery)
    }

    private fun showTab(fragment: Fragment) {
        supportFragmentManager.beginTransaction().apply {
            hide(gallery)
            hide(stats)
            show(fragment)
        }.commit()
        galleryTab = fragment == gallery
        binding.drawerLayout.setTitle(getString(R.string.app_name))
        updateTabIcons()
        invalidateOptionsMenu()
    }

    private fun updateTabIcons() {
        val active = if (galleryTab) 0 else 1
        val outlines = intArrayOf(iconsR.drawable.ic_oui_image_outline, iconsR.drawable.ic_oui_clear_storage_outline)
        val filled = intArrayOf(iconsR.drawable.ic_oui_image, iconsR.drawable.ic_oui_clear_storage)
        for (i in 0 until binding.bottomTab.tabCount) {
            binding.bottomTab.getTabAt(i)?.icon =
                AppCompatResources.getDrawable(this, if (i == active) filled[i] else outlines[i])
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (galleryTab) menuInflater.inflate(R.menu.gallery_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when {
        galleryTab -> gallery.onMenuItem(item.itemId)
        else -> super.onOptionsItemSelected(item)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent) {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) vm.add(uris)
    }

    override fun addFiles() = filePicker.launch(arrayOf("image/*", "video/*"))

    override fun addFolder() = folderImport.launch(null)

    override fun saveAll() {
        if (settings.replaceOriginals) vm.save(null) else withFolder { vm.save(it) }
    }

    override fun saveOne(item: FileItem) {
        if (settings.replaceOriginals) vm.save(null, item) else withFolder { vm.save(it, item) }
    }

    override fun shareAll() {
        val uris = vm.shareUris(null)
        if (uris.isEmpty()) return Toast.makeText(this, R.string.msg_nothing_save, Toast.LENGTH_SHORT).show()
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = "*/*"
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, null))
    }

    override fun clearList() = vm.clear()

    private fun withFolder(action: (Uri) -> Unit) {
        val folder = settings.outputFolder
        if (folder != null && contentResolver.persistedUriPermissions.any { it.uri == folder && it.isWritePermission }) {
            action(folder)
        } else {
            Toast.makeText(this, R.string.msg_pick_folder, Toast.LENGTH_LONG).show()
            afterFolderPicked = action
            folderPicker.launch(null)
        }
    }

    private companion object {
        const val TAG_GALLERY = "gallery"
        const val TAG_STATS = "stats"
    }
}
