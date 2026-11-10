package com.rynekryz.exiless.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.rynekryz.exiless.R
import com.rynekryz.exiless.data.Settings
import dev.oneuiproject.oneui.ktx.setOnClickListenerWithProgress
import dev.oneuiproject.oneui.R as iconsR
import dev.oneuiproject.oneui.widget.OnboardingTipsItemView

class OOBEActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oobe)

        val tips = listOf(
            Triple(R.string.oobe_tip1_title, R.string.oobe_tip1_summary, iconsR.drawable.ic_oui_lock_outline),
            Triple(R.string.oobe_tip2_title, R.string.oobe_tip2_summary, iconsR.drawable.ic_oui_search),
            Triple(R.string.oobe_tip3_title, R.string.oobe_tip3_summary, iconsR.drawable.ic_oui_analyze_storage),
        )
        val container = findViewById<ViewGroup>(R.id.oobe_tips_container)
        for ((title, summary, icon) in tips) {
            val item = OnboardingTipsItemView(this)
            item.setIcon(icon)
            item.title = getString(title)
            item.summary = getString(summary)
            container.addView(item, ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        val continueButton = findViewById<AppCompatButton>(R.id.oobe_continue)
        continueButton.setOnClickListenerWithProgress { _, _ ->
            Settings(this).completeOobe()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
