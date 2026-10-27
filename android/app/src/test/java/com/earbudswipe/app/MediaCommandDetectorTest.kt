package com.earbudswipe.app

import android.view.KeyEvent
import com.earbudswipe.app.model.EarbudCommand
import com.earbudswipe.app.model.GestureAction
import com.earbudswipe.app.service.MediaCommandDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MediaCommandDetectorTest {

    private lateinit var detector: MediaCommandDetector

    @Before
    fun setup() {
        val mappings = mapOf(
            EarbudCommand.MEDIA_NEXT to GestureAction.SWIPE_NEXT_REEL,
            EarbudCommand.MEDIA_PREVIOUS to GestureAction.SWIPE_PREV_REEL,
            EarbudCommand.MEDIA_PLAY_PAUSE to GestureAction.NO_ACTION,
            EarbudCommand.HEADSETHOOK to GestureAction.SWIPE_NEXT_REEL
        )
        detector = MediaCommandDetector(mappings, debounceCooldownMs = 650L, volumeFallbackEnabled = true)
    }

    @Test
    fun testParseKeyCodeMediaNext() {
        val command = detector.parseKeyCode(KeyEvent.KEYCODE_MEDIA_NEXT)
        assertEquals(EarbudCommand.MEDIA_NEXT, command)
    }

    @Test
    fun testParseKeyCodeVolumeFallback() {
        val command = detector.parseKeyCode(KeyEvent.KEYCODE_VOLUME_DOWN)
        assertEquals(EarbudCommand.VOLUME_DOWN, command)
    }

    @Test
    fun testProcessKeyEventActionDownAccepted() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
        val decision = detector.processKeyEvent(event, currentTimeMs = 1000L)

        assertTrue(decision.accepted)
        assertEquals(EarbudCommand.MEDIA_NEXT, decision.command)
        assertEquals(GestureAction.SWIPE_NEXT_REEL, decision.mappedAction)
    }

    @Test
    fun testProcessKeyEventActionUpIgnored() {
        val event = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)
        val decision = detector.processKeyEvent(event, currentTimeMs = 1000L)

        assertFalse(decision.accepted)
        assertTrue(decision.reason.contains("ACTION_UP"))
    }

    @Test
    fun testDebounceSuppression() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)

        // First click accepted
        val first = detector.processKeyEvent(event, currentTimeMs = 1000L)
        assertTrue(first.accepted)

        // Duplicate click 200ms later suppressed (< 650ms cooldown)
        val second = detector.processKeyEvent(event, currentTimeMs = 1200L)
        assertFalse(second.accepted)
        assertTrue(second.isDuplicateSuppressed)

        // Click after 700ms accepted (> 650ms cooldown)
        val third = detector.processKeyEvent(event, currentTimeMs = 1750L)
        assertTrue(third.accepted)
    }
}
