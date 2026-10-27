package com.earbudswipe.app.service

import android.view.KeyEvent
import com.earbudswipe.app.model.EarbudCommand
import com.earbudswipe.app.model.GestureAction

data class CommandDecision(
    val accepted: Boolean,
    val command: EarbudCommand?,
    val mappedAction: GestureAction,
    val reason: String,
    val isDuplicateSuppressed: Boolean = false
)

class MediaCommandDetector(
    private var mappings: Map<EarbudCommand, GestureAction>,
    private var debounceCooldownMs: Long = 650L,
    private var volumeFallbackEnabled: Boolean = false
) {
    private var lastTriggerTimestamp: Long = 0L

    fun updateConfig(
        mappings: Map<EarbudCommand, GestureAction>,
        debounceCooldownMs: Long,
        volumeFallbackEnabled: Boolean
    ) {
        this.mappings = mappings
        this.debounceCooldownMs = debounceCooldownMs
        this.volumeFallbackEnabled = volumeFallbackEnabled
    }

    /**
     * Resolves an Android keycode to an EarbudCommand.
     * realme Buds Air 5 handles the physical triple tap inside earbud firmware,
     * transmitting a single AVRCP Next Track event (KEYCODE_MEDIA_NEXT = 87).
     */
    fun parseKeyCode(keyCode: Int): EarbudCommand? {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> EarbudCommand.MEDIA_NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> EarbudCommand.MEDIA_PREVIOUS
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> EarbudCommand.MEDIA_PLAY_PAUSE
            KeyEvent.KEYCODE_HEADSETHOOK -> EarbudCommand.HEADSETHOOK
            KeyEvent.KEYCODE_VOLUME_UP -> if (volumeFallbackEnabled) EarbudCommand.VOLUME_UP else null
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (volumeFallbackEnabled) EarbudCommand.VOLUME_DOWN else null
            else -> null
        }
    }

    /**
     * Evaluates whether an incoming KeyEvent should trigger a gesture.
     */
    fun processKeyEvent(
        event: KeyEvent,
        currentTimeMs: Long = System.currentTimeMillis()
    ): CommandDecision {
        // Only process ACTION_DOWN to prevent double dispatch on key up
        if (event.action != KeyEvent.ACTION_DOWN) {
            return CommandDecision(
                accepted = false,
                command = null,
                mappedAction = GestureAction.NO_ACTION,
                reason = "Ignored ACTION_UP or non-down event."
            )
        }

        val command = parseKeyCode(event.keyCode)
            ?: return CommandDecision(
                accepted = false,
                command = null,
                mappedAction = GestureAction.NO_ACTION,
                reason = "Unrecognized keycode: ${event.keyCode}"
            )

        val elapsed = currentTimeMs - lastTriggerTimestamp
        if (lastTriggerTimestamp > 0L && elapsed < debounceCooldownMs) {
            return CommandDecision(
                accepted = false,
                command = command,
                mappedAction = mappings[command] ?: GestureAction.NO_ACTION,
                reason = "Duplicate suppressed: ${elapsed}ms elapsed < ${debounceCooldownMs}ms cooldown.",
                isDuplicateSuppressed = true
            )
        }

        val action = mappings[command] ?: GestureAction.NO_ACTION
        if (action == GestureAction.NO_ACTION) {
            return CommandDecision(
                accepted = false,
                command = command,
                mappedAction = action,
                reason = "Command $command is mapped to NO_ACTION."
            )
        }

        lastTriggerTimestamp = currentTimeMs
        return CommandDecision(
            accepted = true,
            command = command,
            mappedAction = action,
            reason = "Accepted command $command -> $action"
        )
    }

    fun resetCooldown() {
        lastTriggerTimestamp = 0L
    }
}
