<div align="center">

# 🎵 Still

**Music, with room to breathe.**  
*A native, ad-free, lightweight Android music player with YouTube Music streaming & library management.*

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/)
[![Android API](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0%2B)-blue?style=for-the-badge)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack-Compose%20M3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-GPL--3.0-orange?style=for-the-badge)](docs/THIRD_PARTY.md)

</div>

---

## ✨ Features

### 🎧 Seamless Streaming & Playback
- **YouTube Music Engine**: Fast, ad-free search and playback powered by unofficial extraction without login, tracking, or accounts.
- **Background Playback**: Continues playing smoothly in the background, with lock-screen system media controls and notification actions.
- **Smart Queue Management**: Reorder, jump, remove tracks, shuffle, and cycle repeat modes with up to 300 entries.
- **Preloading & Low Latency**: Preloads upcoming tracks in memory for instant transitions.
- **Audio State Preservation**: Remembers your queue, playback position, and repeat settings across app restarts.

### 📚 Library & Organization
- **Local Playlists**: Create, rename, delete, and organize playlists locally.
- **Favorites & Recents**: Quick-access favorites and automatic tracking of your latest 100 listened tracks.
- **Audius Legacy Support**: Provider-qualified playback architecture supporting Audius catalog tracks.

### 📜 Lyrics & Offline
- **Synchronized Lyrics**: Real-time scrolling lyrics powered by LRCLIB.
- **Offline Caching**: Download songs directly to local storage for offline playback.

---

## 🛠️ Tech Stack

| Layer | Technologies |
|---|---|
| **UI & Theming** | Jetpack Compose, Material 3, Dark Theme, Coil 3 (Async Image Loading) |
| **Media & Audio Engine** | AndroidX Media3 (ExoPlayer + MediaSessionService) |
| **Local Persistence** | Room Database (Metadata, Playlists, Recents), Preferences DataStore |
| **Extraction & Network** | NewPipeExtractor, OkHttp 3, Kotlinx Serialization |
| **Async & Reactive** | Kotlin Coroutines, StateFlow, Lifecycle-aware UI collectors |

---

## 🚀 Getting Started

### Requirements
- **JDK**: Version 21 (bundled with recent Android Studio) or JDK 17 minimum.
- **Android SDK**: Platform 37.1 (API 35+) and Build Tools 36.0.0.
- **Target Device**: Android 8.0 (API 26) or newer with an active internet connection.

---

### 💻 Build & Install

#### 🪟 Windows (PowerShell)
```powershell
# 1. Set JDK path (if using Android Studio JBR)
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

# 2. Run unit tests & build debug APK
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

# 3. Install to connected device (via ADB)
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

#### 🍏 macOS & 🐧 Linux
```bash
# Set JAVA_HOME if needed, then build & test
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

# Install via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🏗️ Architecture Overview

```
StillApp
 ├── UI Layer (Jetpack Compose)
 │    ├── StillScreen & FeaturePanels (Material 3 UI, Now Playing, Mini-Player)
 │    └── StillViewModel (Exposes StateFlows to composables)
 │
 ├── Playback Layer (Media3)
 │    ├── PlaybackService (ExoPlayer + MediaSession, background lifecycle)
 │    └── PlaybackResolver (Resolves fresh stream URLs on background threads)
 │
 ├── Data & Storage Layer
 │    ├── LibraryRepository (Room database: Tracks, Playlists, Recents, Favourites)
 │    ├── PreferencesDataStore (User settings, queue position snapshots)
 │    └── OfflineStore (Local audio cache & file manager)
 │
 └── Provider Layer
      ├── YouTubeProvider (NewPipeExtractor search & audio streams)
      └── AudiusProvider (REST API provider)
```

---

## 🧪 Verification & Tests

To execute provider parsing, queue logic, Room database tests, and Media3 timeline tests:

```bash
# Unit Tests
./gradlew :app:testDebugUnitTest

# Connected Device Instrumentation Tests
./gradlew :app:connectedDebugAndroidTest
```

> For full test history and verified providers, see [YouTube Verification](docs/YOUTUBE.md), [Audius Verification](docs/PROVIDER.md), and [Offline & Lyrics Verification](docs/OFFLINE_LYRICS.md).

---

## ⚖️ Third-Party Notices & Licenses

- **NewPipeExtractor** is licensed under **GPL-3.0-or-later**. Its license is bundled directly in app assets (see [Third-Party Notices](docs/THIRD_PARTY.md)).
- This project is an independent client and is **not affiliated with, endorsed by, or sponsored by YouTube, Google LLC, or Audius**.
- No credentials or private API keys are required to build or run this application.

---

<div align="center">
Made with ❤️ for clean, uncluttered music listening.
</div>
