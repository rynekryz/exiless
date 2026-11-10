package com.rynekryz.exiless.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.rynekryz.exiless.R
import dev.oneuiproject.oneui.layout.AppInfoLayout

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        findViewById<AppInfoLayout>(R.id.app_info).apply {
            updateStatus = AppInfoLayout.Status.NotUpdatable
            addOptionalText(getString(R.string.about_by))
            addOptionalText(getString(R.string.about_license_line))
            addOptionalText(getString(R.string.about_disclaimer))
        }
    }

    fun openSource(@Suppress("UNUSED_PARAMETER") v: View) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri())) }
    }

    fun openLicenses(@Suppress("UNUSED_PARAMETER") v: View) {
        startActivity(Intent(this, LicensesActivity::class.java))
    }

    private companion object {
        const val SOURCE_URL = "https://github.com/rynekryz/exiless"
    }
}
