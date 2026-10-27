package com.earbudswipe.app

import com.earbudswipe.app.model.TargetApp
import com.earbudswipe.app.service.ForegroundAppDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ForegroundAppDetectorTest {

    private lateinit var detector: ForegroundAppDetector

    @Before
    fun setup() {
        val targets = listOf(
            TargetApp("instagram", "Instagram (Reels)", "com.instagram.android", enabled = true),
            TargetApp("youtube", "YouTube (Shorts)", "com.google.android.youtube", enabled = true),
            TargetApp("tiktok", "TikTok", "com.zhiliaoapp.musically", enabled = false)
        )
        detector = ForegroundAppDetector(targets)
    }

    @Test
    fun testInstagramTargetActive() {
        val eval = detector.evaluate("com.instagram.android")
        assertTrue(eval.isTargetActive)
        assertEquals("instagram", eval.matchedTarget?.id)
        assertFalse(eval.isBlacklisted)
    }

    @Test
    fun testYouTubeTargetActive() {
        val eval = detector.evaluate("com.google.android.youtube")
        assertTrue(eval.isTargetActive)
        assertEquals("youtube", eval.matchedTarget?.id)
    }

    @Test
    fun testDisabledAppIgnored() {
        val eval = detector.evaluate("com.zhiliaoapp.musically")
        assertFalse(eval.isTargetActive)
        assertTrue(eval.reason.contains("disabled"))
    }

    @Test
    fun testBlacklistedSystemUiBlocked() {
        val eval = detector.evaluate("com.android.systemui")
        assertFalse(eval.isTargetActive)
        assertTrue(eval.isBlacklisted)

        val evalSettings = detector.evaluate("com.android.settings")
        assertFalse(evalSettings.isTargetActive)
        assertTrue(evalSettings.isBlacklisted)
    }

    @Test
    fun testUnrelatedAppIgnored() {
        val eval = detector.evaluate("com.spotify.music")
        assertFalse(eval.isTargetActive)
        assertFalse(eval.isBlacklisted)
        assertTrue(eval.reason.contains("Unrelated"))
    }
}
