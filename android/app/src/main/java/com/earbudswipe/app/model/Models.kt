package com.earbudswipe.app.model

enum class EarbudCommand(val label: String, val defaultKeycode: Int) {
    MEDIA_NEXT("Next Track (Triple Tap)", 87),
    MEDIA_PREVIOUS("Previous Track", 88),
    MEDIA_PLAY_PAUSE("Play / Pause (Double Tap)", 85),
    HEADSETHOOK("Headset Hook Button", 79),
    VOLUME_UP("Volume Up Key", 24),
    VOLUME_DOWN("Volume Down Key", 25)
}

enum class GestureAction(val label: String) {
    SWIPE_NEXT_REEL("Swipe Up (Next Reel)"),
    SWIPE_PREV_REEL("Swipe Down (Previous Reel)"),
    TOGGLE_PLAY_PAUSE("Toggle Reel Play/Pause"),
    NO_ACTION("No Action (Ignore)")
}

data class TargetApp(
    val id: String,
    val name: String,
    val packageName: String,
    val enabled: Boolean = true
)

data class GestureConfig(
    val startXPercent: Float = 50f,
    val startYPercent: Float = 80f,
    val endXPercent: Float = 50f,
    val endYPercent: Float = 25f,
    val durationMs: Long = 250L,
    val debounceCooldownMs: Long = 650L,
    val allowQueuing: Boolean = false
)

data class DiagnosticLog(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val event: String,
    val source: String,
    val details: String,
    val targetPackage: String,
    val accepted: Boolean,
    val rejectionReason: String? = null
)

data class AppSettings(
    val automationEnabled: Boolean = true,
    val targetApps: List<TargetApp> = listOf(
        TargetApp("instagram", "Instagram (Reels)", "com.instagram.android", true),
        TargetApp("youtube", "YouTube (Shorts)", "com.google.android.youtube", true),
        TargetApp("tiktok", "TikTok", "com.zhiliaoapp.musically", false)
    ),
    val mappings: Map<EarbudCommand, GestureAction> = mapOf(
        EarbudCommand.MEDIA_NEXT to GestureAction.SWIPE_NEXT_REEL,
        EarbudCommand.MEDIA_PREVIOUS to GestureAction.SWIPE_PREV_REEL,
        EarbudCommand.MEDIA_PLAY_PAUSE to GestureAction.NO_ACTION,
        EarbudCommand.HEADSETHOOK to GestureAction.SWIPE_NEXT_REEL,
        EarbudCommand.VOLUME_UP to GestureAction.NO_ACTION,
        EarbudCommand.VOLUME_DOWN to GestureAction.NO_ACTION
    ),
    val gestureConfig: GestureConfig = GestureConfig(),
    val debugLogging: Boolean = true,
    val startOnBoot: Boolean = false,
    val enableVolumeFallback: Boolean = false
)
