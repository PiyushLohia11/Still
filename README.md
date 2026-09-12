# Still
A native, free, ad-free Android music player for public Audius tracks. Kotlin, Compose Material 3, Media3, Room and DataStore. Requires Android 8.0 (API 26) or newer and an internet connection.

## Open and build
Open this directory in a recent stable Android Studio supporting AGP 9.3. Use the bundled JDK 21 (JDK 17 is the documented minimum) and install Android SDK Platform 37.1 and Build Tools 36.0.0. Android Studio creates the ignored local.properties file.

Windows PowerShell:
```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```
macOS/Linux: set JAVA_HOME to a compatible JDK and run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.

The Gradle wrapper and its pinned distribution checksum are included. Generated outputs, credentials and machine-specific configuration are excluded from Git.

## Audius configuration
Public search and public streaming work without credentials as verified on September 12, 2026. See [provider verification](docs/PROVIDER.md) for official references and the actual HTTP probe.

For an optional public application API key, visit https://api.audius.co/plans, sign in and create an API key. Set the AUDIUS_API_KEY environment variable before building, or create an ignored credentials.properties file:
```properties
AUDIUS_API_KEY=YOUR_PUBLIC_APPLICATION_KEY
```
Rebuild after changing it. The key is visible in the APK. Never put an API secret, bearer token, wallet private key or user credential in this file or app. Server-backed authorization would be a separate future integration. No credentials are required to build this MVP.

Audius does not carry every song. Gated, deleted, key-restricted and unavailable tracks cannot be played here. No access checks are bypassed and previews are not substituted for full tracks. Local favourites/playlists do not modify an Audius account. Audius may change quotas, catalog availability or API access.

## Use
- Search Audius; selecting a track queues the playable results already loaded.
- The mini player opens Now Playing. Seek, pause, skip, favourite, shuffle and cycle repeat off/all/one.
- Queue lets you jump, move tracks up/down and remove entries, including the current track. Maximum 300 entries.
- Track options add a favourite, append to queue or add to a playlist.
- Library includes favourites, named playlists (create, rename, delete, remove tracks) and the latest 100 distinct tracks listened to.
- Preferences can disable next-track preloading.
- Playback continues when backgrounded or locked. The media notification exposes system controls.
- On restart the last queue, position, shuffle permutation and repeat mode restore paused. Resume explicitly. Snapshots are written on player events and every three seconds.

## Architecture
One app module. `MusicProvider` defines search, track details and playback access; identifiers are provider-qualified, e.g. audius:mWB22. `AudiusProvider` contains REST parsing and access policy. Register supported alternatives in `ProviderRegistry`.

`PlaybackService` owns ExoPlayer and MediaSession. A resolving data source requests fresh official access on the loader thread. No expiring audio URL is persisted. Media3 manages advancement, shuffle/repeat, audio focus, noisy-output handling, media buttons and notification lifecycle. The UI controls it through MediaController.

`LibraryRepository` wraps Room metadata, favourites, playlists and recents. DataStore stores preferences and queue metadata/position. Neither stores audio. `StillViewModel` exposes StateFlows to Compose; the provider and player are outside composables.

Upcoming audio can buffer up to five seconds in memory using Media3 playlist preloading. Gapless output depends on source encoding and network conditions; no crossfade or zero-buffering guarantee. Temporary load failures retry with one/two/four-second backoff, then stop with retry UI. Access refusals do not retry.

## Verification
`testDebugUnitTest` covers provider parsing/access/HTTP handling, queue edits and Media3 repeat/shuffle timeline behavior, snapshot restoration, retry bounds and Room persistence across database reopen.

With an emulator/device and internet:
```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```
The live instrumentation test searches Audius, selects a real result, verifies position advancement and pause/seek, backgrounds/locks the device, sends system media keys, exercises transient audio focus, and seeks near the end to check automatic advancement. It depends on live provider availability and uses no fake audio. Test-only synthetic metadata/URLs are confined to unit tests.

See [verification report](docs/VERIFICATION.md) for checks actually run and remaining limitations.

## GitHub
Feature milestones are committed locally. To authenticate and create the requested private repository when no remote exists:
```powershell
gh auth login
gh repo create still --private --source=. --remote=origin --push
```
If origin already exists: `git push -u origin main`. Do not upload credentials or generated APKs through Git; share the local APK separately.
