package com.earbudswipe.app

import android.app.Application
import com.earbudswipe.app.data.SettingsRepository

class EarbudSwipeApp : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
    }
}
