package com.livetrans.overlay

import android.app.Application
import com.livetrans.overlay.data.AppPreferences

class LiveTransApplication : Application() {

    lateinit var appPreferences: AppPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        appPreferences = AppPreferences(this)
    }
}
