# Experimental YouTube Music provider

Verified September 12, 2026 on the Pixel_9a Android 17 emulator.

## Implementation
- YouTubeProvider uses NewPipeExtractor v0.26.5 and the music_songs search filter.
- Search results retain provider-qualified YouTube video IDs. Continuation results are adapted to the existing 30-result offset contract with bounded page traversal.
- Audio-only HTTPS streams are resolved on Media3's loader thread at playback time. URLs are not persisted.
- Existing Audius tracks, favourites, playlists and queue snapshots remain supported through ProviderRegistry.
- No YouTube account credentials, subscription, external extraction server or Python runtime is configured.
- NewPipeExtractor's GPL text is packaged in assets/licenses; see THIRD_PARTY.md.
- Android API 26 support uses desugar_jdk_libs_nio 2.1.5, as required by the extractor for platforms below API 33.

## Actual checks
- :app:assembleDebug :app:testDebugUnitTest :app:lintDebug: passed.
- Follow-up :app:testDebugUnitTest :app:connectedDebugAndroidTest: passed, including the two newly added provider tests.
- New unit tests verify identifier validation and request/response fidelity (POST body, extractor headers, HTTP 429 and Retry-After).
- Live instrumentation: 1 test, 0 failures, 0 errors, 0 skipped, approximately 65 seconds.
- Searched Daft Punk Get Lucky through the provider and Compose search UI; selected a real result.
- Confirmed playback position advancement, pause, seek, background and screen-locked playback, system media keys, transient audio-focus recovery, automatic transition to the second track and recent-listening persistence.
- No fake audio or substituted stream was used.

## APK
app/build/outputs/apk/debug/app-debug.apk

SHA-256: B7EEB4F59CD17D937C9F4E818FB889C5A1B6A6619282F749514333D44120CE57

This is a debug APK. The emulator ran without audio output, so the test proves player operation, not subjective sound quality. Testing covered one search and two playback items on this network; it does not establish every song's availability, every region, physical-device behavior, or future service compatibility. Android API 26 runtime behavior was not device-tested.

## Maintenance
YouTube extraction is unofficial and can break after upstream changes. Playback failures remain visible with retry UI; Still does not silently substitute an unrelated song. Search pagination currently re-fetches earlier pages when loading more, so deep searches cost additional requests. No accounts, downloads or remote-library synchronization were added.

References:
- https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5
- https://github.com/TeamNewPipe/NewPipeExtractor/releases/tag/v0.26.5