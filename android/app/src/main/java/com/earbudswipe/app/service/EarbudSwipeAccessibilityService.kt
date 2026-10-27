package com.earbudswipe.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.earbudswipe.app.EarbudSwipeApp
import com.earbudswipe.app.model.GestureAction
import com.earbudswipe.app.util.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EarbudSwipeAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var commandDetector: MediaCommandDetector
    private lateinit var gestureController: SwipeGestureController
    private lateinit var appDetector: ForegroundAppDetector

    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        val repo = (application as EarbudSwipeApp).settingsRepository
        val currentSettings = repo.settings.value

        commandDetector = MediaCommandDetector(
            mappings = currentSettings.mappings,
            debounceCooldownMs = currentSettings.gestureConfig.debounceCooldownMs,
            volumeFallbackEnabled = currentSettings.enableVolumeFallback
        )

        gestureController = SwipeGestureController(currentSettings.gestureConfig)
        appDetector = ForegroundAppDetector(currentSettings.targetApps)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        // Collect dynamic setting updates
        serviceScope.launch {
            repo.settings.collectLatest { settings ->
                commandDetector.updateConfig(
                    mappings = settings.mappings,
                    debounceCooldownMs = settings.gestureConfig.debounceCooldownMs,
                    volumeFallbackEnabled = settings.enableVolumeFallback
                )
                gestureController.updateConfig(settings.gestureConfig)
                appDetector.updateTargetApps(settings.targetApps)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val pkg = event.packageName?.toString()
                if (!pkg.isNullOrBlank() && pkg != "com.android.systemui") {
                    currentForegroundPackage = pkg
                }
            }
        }
    }

    /**
     * Reads the package of the currently active window (package name only, no content is read).
     * Falls back to the last window-change event if the live lookup fails.
     */
    private fun resolveForegroundPackage(): String {
        try {
            val live = rootInActiveWindow?.packageName?.toString()
            if (!live.isNullOrBlank() && live != "com.android.systemui") {
                currentForegroundPackage = live
            }
        } catch (_: Exception) {
        }
        return currentForegroundPackage
    }

    override fun onInterrupt() {
        EventLogger.log(
            event = "SERVICE_INTERRUPTED",
            source = "ACCESSIBILITY_SERVICE",
            details = "Service was interrupted by system",
            targetPackage = currentForegroundPackage,
            accepted = false
        )
    }

    /**
     * Intercepts hardware and Bluetooth key events when flagRequestFilterKeyEvents is enabled.
     * Consuming the event (return true) prevents Instagram or music apps from receiving the keycode.
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        val repo = (application as EarbudSwipeApp).settingsRepository
        val settings = repo.settings.value

        if (!settings.automationEnabled) {
            return false // Automation is disabled by user, pass through
        }

        val decision = commandDetector.processKeyEvent(event)
        if (!decision.accepted) {
            if (decision.isDuplicateSuppressed && settings.debugLogging) {
                EventLogger.log(
                    event = "KEY_DUPLICATE_IGNORED",
                    source = "ACCESSIBILITY_KEY_EVENT",
                    details = "Keycode: ${event.keyCode} (${decision.reason})",
                    targetPackage = currentForegroundPackage,
                    accepted = false,
                    rejectionReason = decision.reason
                )
            }
            return false
        }

        // Evaluate foreground app
        val appEval = appDetector.evaluate(resolveForegroundPackage())
        if (!appEval.isTargetActive) {
            if (settings.debugLogging) {
                EventLogger.log(
                    event = "COMMAND_IGNORED_NON_TARGET",
                    source = "ACCESSIBILITY_KEY_EVENT",
                    details = "Command ${decision.command} ignored. Foreground: $currentForegroundPackage",
                    targetPackage = currentForegroundPackage,
                    accepted = false,
                    rejectionReason = appEval.reason
                )
            }
            return false // Let other apps handle the key if Instagram is not foreground
        }

        // Execute gesture
        triggerGestureForAction(decision.mappedAction, "ACCESSIBILITY_KEY_EVENT", decision.command?.name ?: "UNKNOWN")
        return true // Consume event so music track is not accidentally skipped
    }

    fun handleExternalMediaKeyEvent(event: KeyEvent) {
        val repo = (application as EarbudSwipeApp).settingsRepository
        val settings = repo.settings.value

        if (!settings.automationEnabled) return

        val decision = commandDetector.processKeyEvent(event)
        if (!decision.accepted) return

        val appEval = appDetector.evaluate(resolveForegroundPackage())
        if (!appEval.isTargetActive) {
            EventLogger.log(
                event = "MEDIA_SESSION_IGNORED_NON_TARGET",
                source = "MEDIA_SESSION_CALLBACK",
                details = "Foreground: $currentForegroundPackage (${appEval.reason})",
                targetPackage = currentForegroundPackage,
                accepted = false,
                rejectionReason = appEval.reason
            )
            return
        }

        triggerGestureForAction(decision.mappedAction, "MEDIA_SESSION_CALLBACK", decision.command?.name ?: "AVRCP")
    }

    fun triggerManualTest(action: GestureAction = GestureAction.SWIPE_NEXT_REEL) {
        triggerGestureForAction(action, "MANUAL_TEST", "TEST_BUTTON")
    }

    private fun triggerGestureForAction(action: GestureAction, source: String, triggerName: String) {
        val displayMetrics = resources.displayMetrics

        gestureController.dispatchSwipe(this, displayMetrics, action) { success, message ->
            vibrateFeedback()
            EventLogger.log(
                event = if (success) "SWIPE_DISPATCH_SUCCESS" else "SWIPE_DISPATCH_FAILED",
                source = source,
                details = "Trigger: $triggerName -> $action ($message)",
                targetPackage = currentForegroundPackage,
                accepted = success,
                rejectionReason = if (!success) message else null
            )
        }
    }

    private fun vibrateFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(30L)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: EarbudSwipeAccessibilityService? = null
            private set

        @Volatile
        var currentForegroundPackage: String = "unknown"
            private set

        fun isServiceRunning(): Boolean = instance != null
    }
}
