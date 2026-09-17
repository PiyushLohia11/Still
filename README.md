# Still

**Music, with room to breathe.**

A native Android music player built with Kotlin and Jetpack Compose. Search for music, keep a personal library, and listen with background playback, offline downloads, and synced lyrics.

Android 8.0+ · Material 3 · No account required for YouTube search and playback

## What you can do

- **Discover music:** search songs and artists, explore mood mixes, and get recommendations based on recent listening and favourites.
- **Make it yours:** create, rename, and import playlists; browse cover collages; search within a playlist; play in order or shuffle.
- **Keep listening:** use the mini-player, background playback, system media controls, a queue of up to 300 tracks, repeat, and optional Auto-Radio.
- **Listen offline:** download individual songs or playlists and manage saved audio in Library → Downloads.
- **Follow along:** view synced or plain lyrics from LRCLIB and tap a synced line to seek.
- **Adjust playback:** use the equalizer, media volume controls, and optional next-track preloading.

Queues and playback position are saved between sessions. Playlists, favourites, and recent listening are stored locally on your device.

## Gestures

Buttons and menus remain available alongside gestures.

| Where | Gesture | Action |
| --- | --- | --- |
| Song rows in search, playlists, favourites, recent listening, and mixes | Swipe right / left | Add to queue / toggle favourite |
| Ready downloads | Swipe right / left | Add to queue / toggle favourite |
| Queue rows | Swipe right / left | Move up / down one place |
| Mini-player or full-player artwork | Swipe left / right | Next / previous track |
| Mini-player | Swipe up | Open Now Playing |
| Now Playing and Queue handles, other sheet headers | Swipe down | Close the sheet |

Horizontal discovery carousels keep their normal scrolling behavior. Song actions show a reveal label and haptic feedback when the swipe crosses its threshold.

## Build and run

### Requirements

- JDK 17 or newer; Android Studio’s bundled JBR works.
- Android SDK platform **37.1**, as configured in `app/build.gradle.kts`.
- Android SDK Platform-Tools for installation and device tests.
- Internet access for the first Gradle build and online music features.

Use the included Gradle wrapper. Set `sdk.dir` in your local `local.properties`, or configure the Android SDK through your environment. Do not commit machine-specific SDK paths or credentials.

### Windows / PowerShell

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

### macOS / Linux

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. This is a development build, not an optimized production release. Enable USB debugging and authorize your computer before installing to a phone.

YouTube playback does not require an API key. The legacy Audius provider optionally reads a public `AUDIUS_API_KEY` from the environment or a local `credentials.properties`; see `credentials.properties.example`. Never put private tokens into an APK.

## Tests and performance

```powershell
# Unit tests, Android lint, and debug APK
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

# Gesture tests on a connected device or emulator
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.still.music.SwipeActionsTest'
```

The full `connectedDebugAndroidTest` suite also includes live playback tests that need internet access and available provider tracks. Use a test device: instrumentation installs test packages and interacts with the app.

Playback progress is observed separately from screen and library state. Its timer runs only while a progress or lyrics view is observing it. Flat sliders avoid wave animations and sine-path drawing; paused visualizers stop their repeating animations. Regression tests cover clock subscription behavior, immediate seek updates, and gesture thresholds.

These changes reduce unnecessary UI work; they are not a claim of measured frame-rate improvement. Debug builds, cold startup, network conditions, and provider extraction can still affect responsiveness. To inspect frame timing after reproducing an issue:

```bash
adb shell dumpsys gfxinfo com.still.music
```

## Project layout

```text
app/src/main/java/com/still/music/
├── ui/         Compose screens, gestures, theme, and view model
├── playback/   Media3 service, stream resolution, and queue rules
├── provider/   YouTube/Audius providers, recommendations, imports
├── data/       Room library and DataStore preferences
├── offline/    Downloads, transfer, and local audio storage
├── lyrics/     LRCLIB lookup, caching, and synchronized lyrics
└── audio/      Equalizer management
```

Main dependencies: Jetpack Compose / Material 3, Media3, Room, DataStore, WorkManager, Coil, OkHttp, Kotlin coroutines, and NewPipeExtractor. Versions are pinned in the Gradle files.

## Known limits

- YouTube access uses unofficial extraction. Availability can change, and some tracks may fail or require a provider update. Still is not affiliated with YouTube or Spotify.
- Playlist imports can leave songs unmatched; already imported songs remain saved if an import stops.
- Lyrics depend on third-party coverage and matching accuracy.
- Library and downloads are local, with no account sync. Saved audio is removed when the app is uninstalled.
- The legacy provider and verification documents describe specific past test runs, not guarantees of current catalog availability.

## Further reading

- [YouTube provider notes](docs/YOUTUBE.md)
- [Offline audio and lyrics](docs/OFFLINE_LYRICS.md)
- [Audius provider notes](docs/PROVIDER.md)
- [Historical verification report](docs/VERIFICATION.md)
- [Third-party notices and NewPipeExtractor licensing](docs/THIRD_PARTY.md)

NewPipeExtractor is licensed under GPL-3.0-or-later; its license text is included in the APK. See the third-party notices before redistributing the app.
