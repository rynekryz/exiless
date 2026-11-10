package com.rynekryz.exiless.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.format.Formatter
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.rynekryz.exiless.R
import com.rynekryz.exiless.data.Settings
import dev.oneuiproject.oneui.ktx.onNewValue
import dev.oneuiproject.oneui.preference.HorizontalRadioPreference
import dev.oneuiproject.oneui.layout.ToolbarLayout

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_container)
        findViewById<ToolbarLayout>(R.id.toolbar_layout).apply {
            setTitle(getString(R.string.settings_title))
            showNavigationButtonAsBack = true
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.container, SettingsFragment()).commit()
        }
    }
}

class SettingsFragment : PreferenceFragmentCompat() {
    private lateinit var settings: Settings
    private lateinit var themeAuto: SwitchPreferenceCompat
    private lateinit var themeMode: HorizontalRadioPreference

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            settings.outputFolder = uri
            updateFolderSummary()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        settings = Settings(requireContext())
        settings.migrateLegacyTheme()
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<Preference>(Settings.OUTPUT_FOLDER)!!.setOnPreferenceClickListener { folderPicker.launch(null); true }
        findPreference<Preference>(Settings.STATS_RESET)!!.setOnPreferenceClickListener {
            settings.resetStats()
            updateStatsSummary()
            true
        }
        findPreference<Preference>(Settings.ABOUT)!!.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), AboutActivity::class.java))
            true
        }
        initThemePrefs()
        initReplacePref()
        updateFolderSummary()
        updateStatsSummary()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(resources.getColor(dev.oneuiproject.oneui.design.R.color.oui_des_background_color, requireActivity().theme))
    }

    private fun initThemePrefs() {
        themeAuto = findPreference(Settings.THEME_AUTO)!!
        themeMode = findPreference(Settings.THEME_MODE)!!
        themeMode.value = settings.themeMode()
        themeMode.isEnabled = !themeAuto.isChecked
        themeMode.setDividerEnabled(false)
        themeMode.setTouchEffectEnabled(false)
        themeMode.onNewValue { mode -> settings.setTheme(auto = false, mode = mode) }
        themeAuto.onNewValue { auto ->
            themeMode.isEnabled = !auto
            settings.setTheme(auto = auto, mode = settings.themeMode())
        }
    }

    private fun updateFolderSummary() {
        findPreference<Preference>(Settings.OUTPUT_FOLDER)!!.summary =
            when {
                findPreference<SwitchPreferenceCompat>(Settings.REPLACE_ORIGINALS)!!.isChecked ->
                    getString(R.string.pref_folder_disabled)
                else -> settings.outputFolder?.let(::folderLabel) ?: getString(R.string.pref_folder_unset)
            }
    }

    private fun initReplacePref() {
        val replace = findPreference<SwitchPreferenceCompat>(Settings.REPLACE_ORIGINALS)!!
        findPreference<Preference>(Settings.OUTPUT_FOLDER)!!.isEnabled = !replace.isChecked
        replace.onNewValue { on ->
            findPreference<Preference>(Settings.OUTPUT_FOLDER)!!.isEnabled = !on
            updateFolderSummary()
        }
    }

    private fun updateStatsSummary() {
        val s = settings.stats
        findPreference<Preference>(Settings.STATS)!!.summary = getString(
            R.string.pref_stats_summary, s.files, Formatter.formatShortFileSize(requireContext(), s.bytes), s.gps,
        )
    }

    private fun folderLabel(tree: Uri): String =
        runCatching { DocumentsContract.getTreeDocumentId(tree).replace(':', '/') }.getOrDefault(tree.toString())
}
