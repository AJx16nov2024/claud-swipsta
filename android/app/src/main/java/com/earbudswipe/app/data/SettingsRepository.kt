package com.earbudswipe.app.data

import android.content.Context
import android.content.SharedPreferences
import com.earbudswipe.app.model.AppSettings
import com.earbudswipe.app.model.EarbudCommand
import com.earbudswipe.app.model.GestureAction
import com.earbudswipe.app.model.GestureConfig
import com.earbudswipe.app.model.TargetApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("earbud_swipe_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val autoEnabled = prefs.getBoolean(KEY_AUTO_ENABLED, true)
        val durationMs = prefs.getLong(KEY_DURATION_MS, 250L)
        val debounceCooldownMs = prefs.getLong(KEY_DEBOUNCE_MS, 650L)
        val startX = prefs.getFloat(KEY_START_X, 50f)
        val startY = prefs.getFloat(KEY_START_Y, 80f)
        val endX = prefs.getFloat(KEY_END_X, 50f)
        val endY = prefs.getFloat(KEY_END_Y, 25f)
        val allowQueuing = prefs.getBoolean(KEY_ALLOW_QUEUING, false)
        val debugLogging = prefs.getBoolean(KEY_DEBUG_LOGGING, true)
        val startOnBoot = prefs.getBoolean(KEY_START_ON_BOOT, false)
        val enableVolumeFallback = prefs.getBoolean(KEY_VOLUME_FALLBACK, false)

        val igEnabled = prefs.getBoolean(KEY_IG_ENABLED, true)
        val ytEnabled = prefs.getBoolean(KEY_YT_ENABLED, true)
        val ttEnabled = prefs.getBoolean(KEY_TT_ENABLED, false)

        val targetApps = listOf(
            TargetApp("instagram", "Instagram (Reels)", "com.instagram.android", igEnabled),
            TargetApp("youtube", "YouTube (Shorts)", "com.google.android.youtube", ytEnabled),
            TargetApp("tiktok", "TikTok", "com.zhiliaoapp.musically", ttEnabled)
        )

        val nextAction = GestureAction.valueOf(
            prefs.getString(KEY_ACTION_NEXT, GestureAction.SWIPE_NEXT_REEL.name) ?: GestureAction.SWIPE_NEXT_REEL.name
        )
        val prevAction = GestureAction.valueOf(
            prefs.getString(KEY_ACTION_PREV, GestureAction.SWIPE_PREV_REEL.name) ?: GestureAction.SWIPE_PREV_REEL.name
        )
        val playPauseAction = GestureAction.valueOf(
            prefs.getString(KEY_ACTION_PLAY_PAUSE, GestureAction.NO_ACTION.name) ?: GestureAction.NO_ACTION.name
        )

        val mappings = mapOf(
            EarbudCommand.MEDIA_NEXT to nextAction,
            EarbudCommand.MEDIA_PREVIOUS to prevAction,
            EarbudCommand.MEDIA_PLAY_PAUSE to playPauseAction,
            EarbudCommand.HEADSETHOOK to nextAction,
            EarbudCommand.VOLUME_UP to GestureAction.NO_ACTION,
            EarbudCommand.VOLUME_DOWN to GestureAction.NO_ACTION
        )

        return AppSettings(
            automationEnabled = autoEnabled,
            targetApps = targetApps,
            mappings = mappings,
            gestureConfig = GestureConfig(
                startXPercent = startX,
                startYPercent = startY,
                endXPercent = endX,
                endYPercent = endY,
                durationMs = durationMs,
                debounceCooldownMs = debounceCooldownMs,
                allowQueuing = allowQueuing
            ),
            debugLogging = debugLogging,
            startOnBoot = startOnBoot,
            enableVolumeFallback = enableVolumeFallback
        )
    }

    fun setAutomationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ENABLED, enabled).apply()
        _settings.value = _settings.value.copy(automationEnabled = enabled)
    }

    fun setTargetAppEnabled(appId: String, enabled: Boolean) {
        when (appId) {
            "instagram" -> prefs.edit().putBoolean(KEY_IG_ENABLED, enabled).apply()
            "youtube" -> prefs.edit().putBoolean(KEY_YT_ENABLED, enabled).apply()
            "tiktok" -> prefs.edit().putBoolean(KEY_TT_ENABLED, enabled).apply()
        }
        val updatedApps = _settings.value.targetApps.map {
            if (it.id == appId) it.copy(enabled = enabled) else it
        }
        _settings.value = _settings.value.copy(targetApps = updatedApps)
    }

    fun updateGestureConfig(config: GestureConfig) {
        prefs.edit()
            .putFloat(KEY_START_X, config.startXPercent)
            .putFloat(KEY_START_Y, config.startYPercent)
            .putFloat(KEY_END_X, config.endXPercent)
            .putFloat(KEY_END_Y, config.endYPercent)
            .putLong(KEY_DURATION_MS, config.durationMs)
            .putLong(KEY_DEBOUNCE_MS, config.debounceCooldownMs)
            .putBoolean(KEY_ALLOW_QUEUING, config.allowQueuing)
            .apply()
        _settings.value = _settings.value.copy(gestureConfig = config)
    }

    fun updateCommandMapping(command: EarbudCommand, action: GestureAction) {
        when (command) {
            EarbudCommand.MEDIA_NEXT -> prefs.edit().putString(KEY_ACTION_NEXT, action.name).apply()
            EarbudCommand.MEDIA_PREVIOUS -> prefs.edit().putString(KEY_ACTION_PREV, action.name).apply()
            EarbudCommand.MEDIA_PLAY_PAUSE -> prefs.edit().putString(KEY_ACTION_PLAY_PAUSE, action.name).apply()
            else -> {}
        }
        val updatedMappings = _settings.value.mappings.toMutableMap()
        updatedMappings[command] = action
        _settings.value = _settings.value.copy(mappings = updatedMappings)
    }

    fun setVolumeFallback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOLUME_FALLBACK, enabled).apply()
        _settings.value = _settings.value.copy(enableVolumeFallback = enabled)
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
        _settings.value = loadSettings()
    }

    companion object {
        private const val KEY_AUTO_ENABLED = "auto_enabled"
        private const val KEY_START_X = "start_x"
        private const val KEY_START_Y = "start_y"
        private const val KEY_END_X = "end_x"
        private const val KEY_END_Y = "end_y"
        private const val KEY_DURATION_MS = "duration_ms"
        private const val KEY_DEBOUNCE_MS = "debounce_ms"
        private const val KEY_ALLOW_QUEUING = "allow_queuing"
        private const val KEY_DEBUG_LOGGING = "debug_logging"
        private const val KEY_START_ON_BOOT = "start_on_boot"
        private const val KEY_VOLUME_FALLBACK = "volume_fallback"
        private const val KEY_IG_ENABLED = "ig_enabled"
        private const val KEY_YT_ENABLED = "yt_enabled"
        private const val KEY_TT_ENABLED = "tt_enabled"
        private const val KEY_ACTION_NEXT = "action_next"
        private const val KEY_ACTION_PREV = "action_prev"
        private const val KEY_ACTION_PLAY_PAUSE = "action_play_pause"
    }
}
