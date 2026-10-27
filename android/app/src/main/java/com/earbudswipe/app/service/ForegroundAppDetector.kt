package com.earbudswipe.app.service

import com.earbudswipe.app.model.TargetApp

data class ForegroundEvaluation(
    val isTargetActive: Boolean,
    val matchedTarget: TargetApp?,
    val isBlacklisted: Boolean,
    val reason: String
)

class ForegroundAppDetector(
    private var targetApps: List<TargetApp>
) {
    private val blacklistedPackages = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "android"
    )

    fun updateTargetApps(apps: List<TargetApp>) {
        this.targetApps = apps
    }

    fun evaluate(packageName: CharSequence?): ForegroundEvaluation {
        if (packageName.isNullOrBlank()) {
            return ForegroundEvaluation(
                isTargetActive = false,
                matchedTarget = null,
                isBlacklisted = false,
                reason = "Foreground package is null or blank."
            )
        }

        val pkgString = packageName.toString().trim().lowercase()

        if (blacklistedPackages.contains(pkgString)) {
            return ForegroundEvaluation(
                isTargetActive = false,
                matchedTarget = null,
                isBlacklisted = true,
                reason = "Sensitive system UI active ($pkgString). Touches blocked."
            )
        }

        val target = targetApps.find { it.enabled && it.packageName.lowercase() == pkgString }
        if (target != null) {
            return ForegroundEvaluation(
                isTargetActive = true,
                matchedTarget = target,
                isBlacklisted = false,
                reason = "Active target detected: ${target.name} ($pkgString)"
            )
        }

        val disabledTarget = targetApps.find { !it.enabled && it.packageName.lowercase() == pkgString }
        if (disabledTarget != null) {
            return ForegroundEvaluation(
                isTargetActive = false,
                matchedTarget = null,
                isBlacklisted = false,
                reason = "App is disabled in settings: ${disabledTarget.name}"
            )
        }

        return ForegroundEvaluation(
            isTargetActive = false,
            matchedTarget = null,
            isBlacklisted = false,
            reason = "Unrelated foreground package ($pkgString). Ignoring."
        )
    }
}
