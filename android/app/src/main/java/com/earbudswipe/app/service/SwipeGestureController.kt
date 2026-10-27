package com.earbudswipe.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import android.util.DisplayMetrics
import com.earbudswipe.app.model.GestureAction
import com.earbudswipe.app.model.GestureConfig
import kotlin.math.max
import kotlin.math.min

data class CalculatedPoints(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val durationMs: Long
)

class SwipeGestureController(
    private var config: GestureConfig
) {
    private var isDispatching = false
    private var dispatchEndTimestamp: Long = 0L

    fun updateConfig(config: GestureConfig) {
        this.config = config
    }

    fun calculateCoordinates(
        width: Int,
        height: Int,
        action: GestureAction
    ): CalculatedPoints {
        val clamp = { value: Float, minVal: Float, maxVal: Float -> max(minVal, min(maxVal, value)) }

        var startXPercent = clamp(config.startXPercent, 5f, 95f)
        var startYPercent = clamp(config.startYPercent, 10f, 90f)
        var endXPercent = clamp(config.endXPercent, 5f, 95f)
        var endYPercent = clamp(config.endYPercent, 8f, 90f)

        // For previous reel, swipe downward (top to bottom)
        if (action == GestureAction.SWIPE_PREV_REEL) {
            val temp = startYPercent
            startYPercent = endYPercent
            endYPercent = temp
        }

        val startX = (startXPercent / 100f) * width
        val startY = (startYPercent / 100f) * height
        val endX = (endXPercent / 100f) * width
        val endY = (endYPercent / 100f) * height

        return CalculatedPoints(
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            durationMs = clamp(config.durationMs.toFloat(), 100f, 1000f).toLong()
        )
    }

    fun dispatchSwipe(
        service: AccessibilityService,
        displayMetrics: DisplayMetrics,
        action: GestureAction,
        onResult: (Boolean, String) -> Unit
    ) {
        val now = SystemClock.uptimeMillis()

        if (isDispatching && now < dispatchEndTimestamp) {
            if (!config.allowQueuing) {
                onResult(false, "Previous gesture still running. Queuing disabled.")
                return
            }
        }

        val points = calculateCoordinates(displayMetrics.widthPixels, displayMetrics.heightPixels, action)

        val swipePath = Path().apply {
            moveTo(points.startX, points.startY)
            lineTo(points.endX, points.endY)
        }

        val stroke = GestureDescription.StrokeDescription(swipePath, 0L, points.durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        isDispatching = true
        dispatchEndTimestamp = now + points.durationMs + 60L

        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                isDispatching = false
                onResult(true, "Gesture executed successfully (${points.durationMs}ms)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                isDispatching = false
                onResult(false, "Gesture cancelled by Android framework or window change.")
            }
        }

        val dispatched = service.dispatchGesture(gesture, callback, null)
        if (!dispatched) {
            isDispatching = false
            onResult(false, "dispatchGesture() was rejected by Accessibility framework.")
        }
    }
}
