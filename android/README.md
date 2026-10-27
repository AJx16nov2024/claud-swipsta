# EarbudSwipe: Hands-Free Instagram Reels Navigator for Android 15

An installable Android application built with Kotlin and Jetpack Compose that allows users to control Instagram Reels hands-free using Bluetooth earbuds, specifically optimized for **realme Buds Air 5**.

---

## Key Features

- **Hands-Free Reels Navigation:** Triple-tap your realme Buds Air 5 to swipe up to the next Instagram Reel or YouTube Short.
- **Dual-Channel Event Detection:** Intercepts Bluetooth AVRCP media keys via Android's `AccessibilityService` (`FLAG_REQUEST_FILTER_KEY_EVENTS`) and secondary `MediaSessionCompat` foreground session.
- **Precision Gesture Dispatch:** Uses Android Accessibility `dispatchGesture()` with smooth configurable swipe trajectories (custom start/end percentages, durations from 100ms to 800ms).
- **Smart Debounce Engine:** Eliminates accidental duplicate skips (configurable cooldown window, default 650ms).
- **Foreground App Protection:** Strictly activates swipes only when target apps (Instagram or YouTube) are visible on screen. Never interferes with music players, system settings, or sensitive security dialogs.
- **Diagnostics & Live Signal Stream:** Real-time event log with keycodes, timestamps, rejection reasons, and manual gesture calibration triggers.
- **Hardware Fallback:** Optional volume button long-press or tap support when physical earbud touches are inconvenient (e.g., during sweaty workouts or cooking).

---

## Project Structure

```
android/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── TECHNICAL_INVESTIGATION.md
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/earbudswipe/app/
│       │   │   ├── EarbudSwipeApp.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── model/Models.kt
│       │   │   ├── data/SettingsRepository.kt
│       │   │   ├── service/EarbudSwipeAccessibilityService.kt
│       │   │   ├── service/MediaCommandDetector.kt
│       │   │   ├── service/SwipeGestureController.kt
│       │   │   ├── service/ForegroundAppDetector.kt
│       │   │   ├── service/MediaButtonForegroundService.kt
│       │   │   ├── util/EventLogger.kt
│       │   │   └── ui/
│       │   │       ├── theme/ (Color.kt, Theme.kt, Type.kt)
│       │   │       └── screens/ (HomeScreen.kt, SettingsScreen.kt, DiagnosticsScreen.kt)
│       │   └── res/
│       │       ├── values/ (colors.xml, strings.xml, themes.xml)
│       │       ├── xml/ (accessibility_service_config.xml, backup_rules.xml, data_extraction_rules.xml)
│       │       └── drawable/ (launcher icons)
│       └── test/java/com/earbudswipe/app/
│           ├── MediaCommandDetectorTest.kt
│           ├── SwipeGestureControllerTest.kt
│           └── ForegroundAppDetectorTest.kt
```

---

## How to Build the APK

### Prerequisites
- JDK 17 or higher
- Android SDK 35 (Android 15) with platform-tools and build-tools installed
- Gradle 8.5+

### Build Commands
To compile and assemble the debug APK:

```bash
cd android
./gradlew assembleDebug
```

The compiled APK will be generated at:
```
android/app/build/outputs/apk/debug/app-debug.apk
```

To run unit tests:
```bash
./gradlew testDebugUnitTest
```

---

## Installation & Setup on realme Phone (Android 15)

### Step 1: Install APK via ADB
```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

### Step 2: Configure realme Buds Air 5 in realme Link
1. Open the **realme Link** app on your phone.
2. Select your connected **realme Buds Air 5**.
3. Under **Button Settings**:
   - Set **Triple Tap** (Left Earbud) -> **Previous Track**.
   - Set **Triple Tap** (Right Earbud) -> **Next Track**.
4. Confirm changes (the earbud firmware saves these settings permanently).

### Step 3: Grant Accessibility Permission in Android Settings
1. Open **EarbudSwipe**.
2. Tap **"Enable Accessibility Service"**.
3. In Android Settings -> **Accessibility** -> **Downloaded Apps**, select **EarbudSwipe Automation Service** and turn the switch **ON**.
4. Tap **Allow** on the system confirmation dialog.

### Step 4: realme UI Battery Optimization Whitelist (Crucial)
realme UI / ColorOS terminates background services aggressively:
1. Go to **Settings** -> **Battery** -> **More Settings** -> **Optimize Battery Use**.
2. Find **EarbudSwipe** and select **"Don't optimize"**.
3. Go to **App Info** -> **EarbudSwipe** -> **Battery usage** -> Enable **"Allow background activity"** and **"Allow auto-launch"**.

---

## Manual Hardware Test Procedure

Perform this exact test sequence on your physical device:

1. **Verify Music Player Baseline:**
   - Open Spotify or YouTube Music.
   - Triple-tap the right earbud. Verify music skips to the next track. (Confirms earbud hardware is transmitting AVRCP Next Track).
2. **Verify Diagnostics in EarbudSwipe:**
   - Open EarbudSwipe -> tap **"Logs"**.
   - Triple-tap the earbud.
   - Verify that an event appears: `KEY_DUPLICATE_IGNORED` or `COMMAND_IGNORED_NON_TARGET` with keycode 87 (`KEYCODE_MEDIA_NEXT`). (Confirms Android input layer sees the event).
3. **Verify Instagram Automation:**
   - Ensure Automation toggle is ON.
   - Open **Instagram Reels**.
   - Triple-tap the right earbud.
   - Verify that exactly one smooth upward swipe is performed and the Reel advances.
4. **Verify App Guard (Non-Target Rejection):**
   - Switch to WhatsApp or Chrome.
   - Triple-tap the earbud.
   - Verify that NO swipe is executed on screen.
5. **Verify Graceful Fallback:**
   - Disable Accessibility Service in Settings.
   - Return to Instagram. Verify no crash occurs and earbud behaves as normal.

---

## Privacy & Safety

- **No Screen Reading:** `android:canRetrieveWindowContent="false"` is enforced. The app cannot read messages, passwords, or personal data.
- **Zero Internet Traffic:** No internet permission (`android.permission.INTERNET`) is requested. No analytics, tracking, or telemetry libraries are bundled.
- **System Guard:** Automation is strictly disabled on `com.android.systemui`, `com.android.settings`, and `com.android.permissioncontroller`.
