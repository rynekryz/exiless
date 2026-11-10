package com.rynekryz.exiless

import android.app.Application
import com.rynekryz.exiless.data.Settings

class ExilessApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings(this).applyTheme()
    }
}
