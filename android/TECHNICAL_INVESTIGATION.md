# EarbudSwipe: Technical Investigation & Architecture Dossier

## Subject Hardware & Platform Analysis
- **Earbuds:** realme Buds Air 5 (BES2600 chipset series, Bluetooth 5.3)
- **Target OS:** Android 15 (Vanilla Android & realme UI 5.0 / 6.0 based on ColorOS)
- **Target Host Application:** Instagram Reels (`com.instagram.android`)

---

## 1. Earbud Tap Event Generation (Hardware & Bluetooth Stack)

### Does a physical triple-tap generate three events or one?
- **Finding:** The physical triple-tap is **processed entirely on-chip within the earbud MCU/DSP firmware**. The earbud does **NOT** stream three rapid tap pulses over the Bluetooth link.
- **Protocol:** Standard Bluetooth Audio/Video Remote Control Profile (**AVRCP** v1.6).
- **Operation:** When a user triple-taps the realme Buds Air 5 (configured in realme Link for "Next Track"), the earbud transmits an AVRCP Passthrough command:
  - `AVRCP_OP_ID_FORWARD` (`0x4B`) with `state = BUTTON_PRESSED`, followed by `BUTTON_RELEASED`.
- **Android Translation:** The Android Bluetooth stack (`com.android.bluetooth.avrcp.AvrcpTargetService`) receives the AVRCP packet and converts it into a standard Android `KeyEvent`:
  - `KeyEvent.KEYCODE_MEDIA_NEXT` (integer keycode `87`).
- **Conclusion:** There is **no need or mechanism to detect three individual taps** over Bluetooth. Detecting `KEYCODE_MEDIA_NEXT` represents the intended triple-tap action directly.

---

## 2. Event Observability While Instagram Is in Foreground

### Can a standard Android application observe the media event?
- **The Media Session Routing Problem:**
  1. In standard Android media routing, when Instagram is playing video audio, Instagram holds audio focus or creates active audio tracks.
  2. If an active media session (or active playback client) has focus, Android's `MediaSessionManager` routes incoming AVRCP media buttons **directly to that active player's callback**.
  3. Consequently, a passive background `BroadcastReceiver` listening for `android.intent.action.MEDIA_BUTTON` **is completely bypassed and starves** whenever another app has audio playback.
  4. In Android 8.0+ through Android 15, background execution limits also prevent arbitrary apps from intercepting media buttons globally without explicit system privilege or an active foreground media session.

### Can AccessibilityService observe the event?
- **The Solution:** **YES.** Android's `AccessibilityService` provides a legitimate, authorized input interception hook:
  - By declaring `android:canRequestFilterKeyEvents="true"` in `res/xml/accessibility_service_config.xml` and setting `AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS`.
  - When enabled, `AccessibilityService.onKeyEvent(event: KeyEvent): Boolean` receives physical, virtual, and Bluetooth input events **before** they reach the foreground window or application focus tree!
  - If `onKeyEvent()` returns `true`, the event is consumed by `EarbudSwipe` and will not trigger unwanted music skips in background players or Instagram.

### What if a vendor ROM routes AVRCP directly to AudioService without HID translation?
- Some vendor Bluetooth stacks route AVRCP solely to `AudioService` / `MediaSessionService` rather than injecting an `InputDevice` key event.
- To guarantee 100% reliability, `EarbudSwipe` implements a **Dual-Channel Architecture**:
  1. **Channel 1 (Primary):** `AccessibilityService.onKeyEvent()` with `FLAG_REQUEST_FILTER_KEY_EVENTS`.
  2. **Channel 2 (Secondary / Fallback):** `MediaButtonForegroundService` running with `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` holding an active `MediaSessionCompat`.
  3. **Channel 3 (Hardware Fallback):** Optional Volume Down button interception. Because volume buttons are physical phone buttons registered as input devices, `AccessibilityService.onKeyEvent` receives them with 100% determinism on every Android device.

---

## 3. Android 15 Restrictions & Google Play Compliance

1. **Foreground Service Types (Android 14 & 15 enforcement):**
   - Android 14/15 mandates explicit `foregroundServiceType` in the manifest and at `startForeground()`.
   - `EarbudSwipe` declares `android:foregroundServiceType="mediaPlayback"`.
   - Notification channels require `POST_NOTIFICATIONS` runtime permission on Android 13+.

2. **Accessibility API Policy Compliance:**
   - `EarbudSwipe` sets `android:canRetrieveWindowContent="false"`.
   - The app **never reads screen text, accessibility node hierarchies, user passwords, messages, or credentials**.
   - It only monitors window package transitions (`TYPE_WINDOW_STATE_CHANGED`) to verify if `com.instagram.android` is in the foreground, and invokes `dispatchGesture()` to simulate a touch swipe upon user demand.

3. **Background Gestures:**
   - Android 15 strictly prohibits `dispatchGesture()` if the screen is locked, the display is off, or the app is in the background while sensitive dialogs (e.g., permissions dialogs or system settings) are displayed.
   - `EarbudSwipe` checks `currentForegroundPackage` against a strict blacklist (`com.android.systemui`, `com.android.settings`, `com.android.permissioncontroller`) and validates display state before issuing `dispatchGesture()`.

---

## 4. realme Buds Air 5 Firmware & realme Link App

- **Firmware Customization API:**
  - realme uses proprietary BLE vendor packets for the **realme Link** companion app. There is no public, third-party Android SDK to communicate directly with realme's proprietary BLE GATT service without reverse-engineering proprietary protocol signatures.
  - However, **no proprietary SDK is needed**: realme Link allows the user to map:
    - **Double Tap:** Play/Pause (`KEYCODE_MEDIA_PLAY_PAUSE`)
    - **Triple Tap:** Next Track (`KEYCODE_MEDIA_NEXT`) or Previous Track (`KEYCODE_MEDIA_PREVIOUS`)
  - Once configured in realme Link, the earbud MCU stores this setting in non-volatile flash memory and emits standard Bluetooth SIG AVRCP commands natively.
