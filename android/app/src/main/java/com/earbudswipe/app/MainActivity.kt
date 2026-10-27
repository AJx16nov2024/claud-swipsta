package com.earbudswipe.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.earbudswipe.app.service.EarbudSwipeAccessibilityService
import com.earbudswipe.app.service.MediaButtonForegroundService
import com.earbudswipe.app.ui.screens.DiagnosticsScreen
import com.earbudswipe.app.ui.screens.HomeScreen
import com.earbudswipe.app.ui.screens.SettingsScreen
import com.earbudswipe.app.ui.theme.EarbudSwipeTheme
import kotlinx.coroutines.delay

enum class Screen {
    HOME,
    SETTINGS,
    DIAGNOSTICS
}

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Start the media-session fallback whether or not the notification was allowed
            startMediaFallbackService()
        }

    private fun startMediaFallbackService() {
        try {
            MediaButtonForegroundService.start(this)
        } catch (_: Exception) {
            // Never crash the UI if the OS refuses to start the foreground service
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startMediaFallbackService()
        }

        val app = application as EarbudSwipeApp
        val repo = app.settingsRepository

        setContent {
            EarbudSwipeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val settings by repo.settings.collectAsState()
                    var currentScreen by remember { mutableStateOf(Screen.HOME) }
                    var isServiceActive by remember { mutableStateOf(isAccessibilityServiceEnabled(this)) }

                    // Periodically poll service enabled state
                    LaunchedEffect(Unit) {
                        while (true) {
                            isServiceActive = isAccessibilityServiceEnabled(this@MainActivity) ||
                                    EarbudSwipeAccessibilityService.isServiceRunning()
                            delay(1000)
                        }
                    }

                    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                        when (screen) {
                            Screen.HOME -> HomeScreen(
                                settings = settings,
                                isServiceActive = isServiceActive,
                                onToggleAutomation = { repo.setAutomationEnabled(it) },
                                onNavigateToSettings = { currentScreen = Screen.SETTINGS },
                                onNavigateToDiagnostics = { currentScreen = Screen.DIAGNOSTICS }
                            )

                            Screen.SETTINGS -> SettingsScreen(
                                settings = settings,
                                onNavigateBack = { currentScreen = Screen.HOME },
                                onToggleTargetApp = { id, enabled -> repo.setTargetAppEnabled(id, enabled) },
                                onUpdateGestureConfig = { repo.updateGestureConfig(it) },
                                onToggleVolumeFallback = { repo.setVolumeFallback(it) },
                                onResetDefaults = { repo.resetToDefaults() }
                            )

                            Screen.DIAGNOSTICS -> DiagnosticsScreen(
                                onNavigateBack = { currentScreen = Screen.HOME }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedServiceName = "${context.packageName}/${EarbudSwipeAccessibilityService::class.java.canonicalName}"
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            if (componentNameString.equals(expectedServiceName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
