# Keep models and data classes for serialization/reflection
-keepclassmembers class com.earbudswipe.app.model.** { *; }

# Keep AccessibilityService subclass
-keep class com.earbudswipe.app.service.EarbudSwipeAccessibilityService { *; }
-keep class com.earbudswipe.app.service.MediaButtonForegroundService { *; }
