package com.rynekryz.exiless.data

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.rynekryz.exiless.core.StripOptions

data class Stats(val files: Int = 0, val bytes: Long = 0, val gps: Int = 0)

class Settings(context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    val options: StripOptions
        get() = StripOptions(
            keepOrientation = prefs.getBoolean(KEEP_ORIENTATION, true),
            resetTimestamps = prefs.getBoolean(RESET_TIMESTAMPS, true),
        )

    val suffix: String get() = prefs.getString(NAME_SUFFIX, "_clean").orEmpty()

    val viewMode: String
        get() = prefs.getString(VIEW_MODE, "grid").orEmpty()

    fun setViewMode(mode: String) = prefs.edit { putString(VIEW_MODE, mode) }

    val oobeCompleted: Boolean get() = prefs.getBoolean(OOBE_COMPLETED, false)

    fun completeOobe() = prefs.edit { putBoolean(OOBE_COMPLETED, true) }

    var outputFolder: Uri?
        get() = prefs.getString(OUTPUT_FOLDER, null)?.toUri()
        set(value) = prefs.edit { putString(OUTPUT_FOLDER, value?.toString()) }

    val stats: Stats
        get() = Stats(prefs.getInt(STAT_FILES, 0), prefs.getLong(STAT_BYTES, 0), prefs.getInt(STAT_GPS, 0))

    fun addStats(files: Int, bytes: Long, gps: Int) {
        val s = stats
        prefs.edit {
            putInt(STAT_FILES, s.files + files)
            putLong(STAT_BYTES, s.bytes + bytes)
            putInt(STAT_GPS, s.gps + gps)
        }
    }

    fun resetStats() = prefs.edit { remove(STAT_FILES); remove(STAT_BYTES); remove(STAT_GPS) }

    fun applyTheme() = AppCompatDelegate.setDefaultNightMode(nightMode(prefs.getString(THEME, "system")))

    fun themeAuto(): Boolean = prefs.getBoolean(THEME_AUTO, true)

    fun themeMode(): String = prefs.getString(THEME_MODE, "light").orEmpty()

    fun setTheme(auto: Boolean, mode: String) {
        prefs.edit {
            putBoolean(THEME_AUTO, auto)
            putString(THEME_MODE, mode)
            putString(THEME, if (auto) "system" else mode)
        }
        applyTheme()
    }

    fun migrateLegacyTheme() {
        if (!prefs.contains(THEME)) return
        val legacy = prefs.getString(THEME, "system").orEmpty()
        if (legacy != "system") {
            prefs.edit {
                putBoolean(THEME_AUTO, false)
                putString(THEME_MODE, legacy)
            }
        }
        prefs.edit { remove(THEME) }
    }

    companion object {
        const val KEEP_ORIENTATION = "keep_orientation"
        const val RESET_TIMESTAMPS = "reset_timestamps"
        const val NAME_SUFFIX = "name_suffix"
        const val OUTPUT_FOLDER = "output_folder"
        const val THEME = "theme"
        const val THEME_AUTO = "theme_auto"
        const val THEME_MODE = "theme_mode"
        const val VIEW_MODE = "view_mode"
        const val OOBE_COMPLETED = "oobe_completed"
        const val STATS = "stats"
        const val STATS_RESET = "stats_reset"
        const val ABOUT = "about"
        private const val STAT_FILES = "stat_files"
        private const val STAT_BYTES = "stat_bytes"
        private const val STAT_GPS = "stat_gps"

        fun nightMode(value: String?): Int = when (value) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }
}
