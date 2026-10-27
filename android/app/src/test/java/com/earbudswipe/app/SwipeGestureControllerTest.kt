package com.earbudswipe.app

import com.earbudswipe.app.model.GestureAction
import com.earbudswipe.app.model.GestureConfig
import com.earbudswipe.app.service.SwipeGestureController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeGestureControllerTest {

    @Test
    fun testCoordinateCalculationPercentages() {
        val config = GestureConfig(
            startXPercent = 50f,
            startYPercent = 80f,
            endXPercent = 50f,
            endYPercent = 25f,
            durationMs = 250L
        )
        val controller = SwipeGestureController(config)
        val screenWidth = 1080
        val screenHeight = 2400

        val points = controller.calculateCoordinates(screenWidth, screenHeight, GestureAction.SWIPE_NEXT_REEL)

        assertEquals(540f, points.startX, 0.01f)
        assertEquals(540f, points.endX, 0.01f)
        assertEquals(1920f, points.startY, 0.01f) // 80% of 2400
        assertEquals(600f, points.endY, 0.01f)  // 25% of 2400
        assertEquals(250L, points.durationMs)
    }

    @Test
    fun testPreviousReelInversion() {
        val config = GestureConfig(
            startXPercent = 50f,
            startYPercent = 80f,
            endXPercent = 50f,
            endYPercent = 25f,
            durationMs = 250L
        )
        val controller = SwipeGestureController(config)
        val points = controller.calculateCoordinates(1080, 2400, GestureAction.SWIPE_PREV_REEL)

        // For previous reel, swipe starts at 25% (600) and ends at 80% (1920)
        assertEquals(600f, points.startY, 0.01f)
        assertEquals(1920f, points.endY, 0.01f)
    }

    @Test
    fun testMarginClamping() {
        // Test extreme inputs to ensure system bar safety
        val extremeConfig = GestureConfig(
            startXPercent = -10f,
            startYPercent = 99f,
            endXPercent = 150f,
            endYPercent = 1f
        )
        val controller = SwipeGestureController(extremeConfig)
        val points = controller.calculateCoordinates(1000, 1000, GestureAction.SWIPE_NEXT_REEL)

        // startX clamped to 5% (50px)
        assertTrue(points.startX >= 50f)
        // startY clamped to 90% (900px)
        assertTrue(points.startY <= 900f)
        // endX clamped to 95% (950px)
        assertTrue(points.endX <= 950f)
        // endY clamped to 8% (80px)
        assertTrue(points.endY >= 80f)
    }
}
