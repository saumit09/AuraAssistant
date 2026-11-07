# Aura Assistant 🌌

[![Download APK](https://img.shields.io/badge/Download_APK-Direct_Download-00F5FF?style=for-the-badge&logo=android&logoColor=black)](https://github.com/saumit09/AuraAssistant/releases/download/v1.0.0/AuraAssistant-latest.apk)
[![Latest Release](https://img.shields.io/github/v/release/saumit09/AuraAssistant?style=for-the-badge&color=8A2BE2)](https://github.com/saumit09/AuraAssistant/releases/latest)
[![Build Status](https://img.shields.io/github/actions/workflow/status/saumit09/AuraAssistant/build-apk.yml?branch=main&style=for-the-badge)](https://github.com/saumit09/AuraAssistant/actions)
[![Target SDK](https://img.shields.io/badge/Target_SDK-35_(Android_15)-3A86FF?style=for-the-badge&logo=android)](https://developer.android.com)

**Aura Assistant** is a native, hardware-integrated Android voice assistant built in **Kotlin** and **Jetpack Compose** tailored specifically for **Samsung Galaxy (One UI)** devices. Powered by **Google Gemini 2.0 Flash / 1.5 Flash**, it pairs low-latency streaming speech recognition with function-calling capabilities to execute system tasks across phone hardware, apps, messaging, and accessibility services.

---

## 📱 Direct One-Tap Mobile Download

Click below directly from your Samsung phone browser to download and install the latest compiled release:

👉 **[Download AuraAssistant-latest.apk (v1.0.0)](https://github.com/saumit09/AuraAssistant/releases/download/v1.0.0/AuraAssistant-latest.apk)**  
*(Alternative mirror: [Latest release download](https://github.com/saumit09/AuraAssistant/releases/latest/download/AuraAssistant-latest.apk))*

---

## 🏛️ 5-Layer Systems Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│ Layer 1: System Binding (VoiceInteractionService & Session)            │
│  - Default Digital Assistant hooks                                     │
│  - Samsung Side-Key long-press & gesture swipe-up triggers             │
├────────────────────────────────────────────────────────────────────────┤
│ Layer 2: Floating Jetpack Compose Overlay                              │
│  - Siri-style frosted-glass capsule anchored to bottom edge            │
│  - Dynamic multi-wave sine visualizer (RMS dB speech modulation)       │
│  - Samsung Edge Lighting aurora multi-colour glow                      │
├────────────────────────────────────────────────────────────────────────┤
│ Layer 3: Streaming Voice Pipeline                                      │
│  - Android SpeechRecognizer (EXTRA_PARTIAL_RESULTS = true)             │
│  - Low-latency word-by-word streaming transcriptions                   │
│  - Android TextToSpeech engine with automatic graceful dismissal       │
├────────────────────────────────────────────────────────────────────────┤
│ Layer 4: Gemini Function-Calling Brain                                 │
│  - Google Gemini 2.0 Flash REST API Client                             │
│  - Declarative Tools Schema for device control actions                 │
├────────────────────────────────────────────────────────────────────────┤
│ Layer 5: Android Task Executor                                         │
│  - Fuzzy app launcher (PackageManager)                                 │
│  - Direct phone calling & contact resolution (ContactsContract)        │
│  - WhatsApp & SMS messaging with Accessibility auto-send               │
│  - Alarms & countdown timers (EXTRA_SKIP_UI = true)                    │
│  - Hardware controls: Flashlight (CameraManager), volume, panels, DND  │
└────────────────────────────────────────────────────────────────────────┘
```

For full architectural diagrams and deep-dive documentation, see [SPEC.md](SPEC.md).

---

## 🛠️ Samsung One UI Setup Guide

To integrate Aura Assistant seamlessly on your Samsung Galaxy device (Galaxy S21 / S22 / S23 / S24 / S25, Z Fold, Z Flip, Galaxy A-series):

### 1. Install & Launch Setup Dashboard
1. Open the downloaded `AuraAssistant-latest.apk` and tap **Install** (allow *Install unknown apps* for your browser if prompted).
2. Open **Aura Assistant** from your app drawer.

### 2. Enter Gemini API Key
1. Get a free API key from [Google AI Studio](https://aistudio.google.com/).
2. Paste the key into the **Gemini API Key** card in the setup dashboard.
3. Tap **Save Key**. The key is stored in hardware-backed `EncryptedSharedPreferences` via Android Keystore.

### 3. Grant Permissions
- **Microphone & Telephony**: Tap **Grant** to allow audio recording and call placement.
- **Appear on Top**: Tap **Enable** -> toggle **Allow permission** for Aura Assistant.
- **Accessibility Service**: Tap **Turn On** -> navigate to **Installed apps** -> **Aura Assistant** -> toggle **On**. *(Enables hands-free auto-tapping of WhatsApp send buttons and screen actions)*.
- **Battery Optimization**: Tap **Optimize** -> select **Don't optimize / Unrestricted** so One UI never sleeps the assistant.

### 4. Set as Default Digital Assistant
1. Tap **Configure** under *Default Digital Assistant* in the dashboard, or go to:
   - `Settings` ➔ `Apps` ➔ `Choose default apps` ➔ `Digital assistant app` ➔ `Default digital assistant app` ➔ Select **Aura Assistant**.

### 5. Remap Samsung Side Button (Power Key)
1. Go to `Settings` ➔ `Advanced features` ➔ `Side button`.
2. Under **Press and hold**, select **Digital assistant** (or **Wake Bixby** if Side Button remapper is used).
3. Long-pressing the side button will now trigger Aura Assistant from anywhere, including lock screen and inside apps!

---

## 🗣️ Voice Commands & Tool Actions

| Voice Command | Action Executed | Underlying API |
| :--- | :--- | :--- |
| *"Turn on the flashlight"* | Toggles phone LED torch | `CameraManager.setTorchMode` |
| *"Open Spotify"* | Fuzzy matches & launches app | `PackageManager.getLaunchIntentForPackage` |
| *"Call Mom"* | Resolves contact phone number and dials | `ContactsContract` + `Intent.ACTION_CALL` |
| *"Send WhatsApp to Alex saying I am outside"* | Opens chat & auto-taps Send | `https://wa.me/` + `AuraAccessibilityService` |
| *"Set a timer for 12 minutes"* | Starts countdown without opening clock UI | `AlarmClock.ACTION_SET_TIMER` (`EXTRA_SKIP_UI = true`) |
| *"Set an alarm for 7:00 AM tomorrow"* | Sets system clock alarm | `AlarmClock.ACTION_SET_ALARM` |
| *"Raise the volume"* | Steps up media playback audio | `AudioManager.adjustStreamVolume` |
| *"Open Wi-Fi panel"* | Pops up connectivity quick sheet | `Settings.Panel.ACTION_WIFI` |
| *"Click on Submit"* | Taps target on-screen text | `AccessibilityNodeInfo.ACTION_CLICK` |

---

## 💻 Tech Stack & Dependencies

- **Language**: Kotlin 2.0.21
- **UI Toolkit**: Jetpack Compose (BOM 2024.10.01) + Material 3
- **Build System**: Gradle 8.11.1 (Kotlin DSL)
- **Min SDK**: API 29 (Android 10)
- **Compile / Target SDK**: API 35 (Android 15)
- **Networking**: OkHttp 4.12.0
- **Security**: AndroidX Security Crypto 1.1.0-alpha06 (`EncryptedSharedPreferences`)
- **Speech**: Android `SpeechRecognizer` + Android `TextToSpeech`
- **Automation**: Android `AccessibilityService`

---

## 🏗️ Building Locally

```bash
# Clone the repository
git clone https://github.com/saumit09/AuraAssistant.git
cd AuraAssistant

# Build debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

Apache License 2.0. Developed for the Android Open Source Community.
