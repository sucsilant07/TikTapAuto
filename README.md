# TikTap Auto

![Build](https://github.com/sucsilant07/TikTapAuto/actions/workflows/build.yml/badge.svg)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?logo=kotlin&logoColor=white)
![AGP](https://img.shields.io/badge/AGP-8.2.2-orange)
![License](https://img.shields.io/badge/License-MIT-blue)

An Android automation tool that simulates double-tap gestures on TikTok Lives using the Android **Accessibility Service API** — with randomized position and timing jitter to mimic natural human interaction.

---

## Features

- **Automated double-tap** on TikTok Live streams at a configurable rate
- **Randomized tap position** — ±25 px jitter on X and Y axes per tap
- **Randomized timing** — ±20% variation on the tap interval to avoid pattern detection
- **Floating overlay button** — stop the automation from any screen without leaving TikTok
- **Adjustable speed** — interval slider from 200 ms to 2000 ms
- **Auto-launch TikTok** — opens the app automatically when the tap session starts
- **Foreground service** — keeps the process alive while the screen is active
- **Secure IPC** — internal broadcasts protected with a `signature`-level custom permission

---

## How It Works

```
┌─────────────────┐     Broadcast (signed)     ┌──────────────────────────────┐
│  MainActivity   │ ─────────────────────────► │  AutoTapAccessibilityService │
│                 │                             │                              │
│  · Speed slider │ ◄───────────────────────── │  · GestureDescription API    │
│  · Start / Stop │     Status broadcast        │  · Randomized X/Y jitter     │
└─────────────────┘                             │  · Randomized interval       │
        │                                       └──────────────────────────────┘
        │ startService()
        ▼
┌─────────────────────┐
│ FloatingControlService │
│                        │
│  · TYPE_APPLICATION_  │
│    OVERLAY window      │
│  · Drag-to-reposition │
│  · Tap to stop        │
└────────────────────────┘
```

The core of the automation is the Android [GestureDescription API](https://developer.android.com/reference/android/accessibilityservice/GestureDescription), which lets an AccessibilityService inject synthetic touch events at the OS level — no root required.

Each tap cycle:
1. Computes a base coordinate (screen center)
2. Applies a random offset: `baseX ± rand(25)`, `baseY ± rand(25)`
3. Dispatches a `StrokeDescription` (50 ms press) as the first tap
4. After 120 ms, dispatches the second tap via a `Handler` callback
5. Waits `interval ± 20%` before repeating

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 1.9.22 |
| Build system | Gradle 8.6 + Android Gradle Plugin 8.2.2 |
| Min SDK | API 26 (Android 8.0 Oreo) |
| Target SDK | API 34 (Android 14) |
| Touch injection | `AccessibilityService` + `GestureDescription` |
| Overlay UI | `WindowManager` + `TYPE_APPLICATION_OVERLAY` |
| Background execution | `ForegroundService` + `NotificationChannel` |
| Inter-process comms | `BroadcastReceiver` with `signature` permission |
| CI/CD | GitHub Actions → automated APK build & GitHub Release |

---

## Project Structure

```
TikTapAuto/
├── .github/
│   └── workflows/
│       └── build.yml               # CI/CD: build + publish APK release
├── app/
│   └── src/main/
│       ├── java/com/autotapper/tiktok/
│       │   ├── MainActivity.kt              # UI: speed control, start/stop
│       │   ├── AutoTapAccessibilityService.kt  # Core: gesture injection + jitter
│       │   └── FloatingControlService.kt    # Overlay: draggable stop button
│       ├── res/
│       │   ├── layout/
│       │   │   ├── activity_main.xml
│       │   │   └── floating_control.xml
│       │   └── xml/
│       │       └── accessibility_service_config.xml
│       └── AndroidManifest.xml
├── build.gradle
└── settings.gradle
```

---

## CI/CD Pipeline

Every push to `main` triggers a GitHub Actions workflow that:

1. Checks out the source code
2. Sets up JDK 17 (Temurin distribution)
3. Provisions Gradle 8.6
4. Runs `gradle assembleDebug`
5. Publishes the compiled APK as a **GitHub Release** — ready to download

```yaml
# Simplified workflow
on:
  push:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
      - uses: gradle/actions/setup-gradle@v3
      - run: gradle assembleDebug
      - uses: softprops/action-gh-release@v2   # publish APK
```

---

## Installation

### Download pre-built APK
1. Go to [Releases](https://github.com/sucsilant07/TikTapAuto/releases/latest)
2. Download `app-debug.apk`
3. On your Android device: **Settings → Apps → TikTap Auto → ⋮ → Allow restricted settings**
4. Enable the **Accessibility Service** at **Settings → Accessibility → Installed services**
5. Grant the **Display over other apps** permission when prompted

### Build from source
```bash
git clone https://github.com/sucsilant07/TikTapAuto.git
cd TikTapAuto
gradle assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

---

## Permissions

| Permission | Purpose |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Render the floating stop button over TikTok |
| `FOREGROUND_SERVICE` | Keep the tap service alive |
| `BIND_ACCESSIBILITY_SERVICE` | Inject touch gestures via the OS |
| `com.autotapper.tiktok.CONTROL` | Signature-protected broadcast channel between components |

---

## Key Design Decisions

**Why AccessibilityService instead of ADB / root?**
The Accessibility API is the only sanctioned way to inject gestures on a non-rooted Android device. It is designed for assistive technology but is equally valid for automation tools.

**Why a Foreground Service for the overlay?**
`TYPE_APPLICATION_OVERLAY` windows require a running service to remain on screen when the host activity is not in the foreground. A foreground service with a persistent notification satisfies this requirement on Android 8+.

**Why signature-level permissions on broadcasts?**
Without them, any app on the device could send a spoofed `ACTION_START` broadcast and trigger the gesture injector. The `protectionLevel="signature"` ensures only components signed with the same key can communicate.

---

## License

MIT © [sucsilant07](https://github.com/sucsilant07)
