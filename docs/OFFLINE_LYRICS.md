# Downloads, lyrics and playback performance

## Use
- Choose **Download for offline** in a song's options menu, or **Download** in Now Playing.
- Open **Library > Downloads** for progress, retry/cancel, local playback and removal.
- Downloads are stored in Still's private local storage, not the public Downloads folder. No storage permission is needed. Uninstalling Still removes them.
- Downloads can use Wi-Fi or mobile data. Android may defer queued work until connectivity/storage constraints allow it.
- Open Now Playing and scroll to **Lyrics**. Timed lyrics highlight with playback and support tap-to-seek. Untimed lyrics display as text. LRCLIB attribution is shown.
- Successfully fetched lyrics are stored locally. Download completion also attempts a lyrics fetch; missing lyrics do not fail an audio download.
- Preferences shows this playback session's buffer waits and audio underruns. Buffer waits include seeking. Host/emulator audio glitches may occur outside these counters.

## Implementation
- WorkManager 2.11.2 persists jobs. A single transfer slot limits simultaneous network transfers. Interrupted jobs restart the file transfer; byte-range resume across process death is not implemented.
- Track metadata includes the current worker ID, avoiding stale retry statuses.
- SHA-256 filenames avoid interpreting track IDs as filesystem paths. Metadata is replaced atomically.
- A partial audio file is never exposed to playback. The received length is checked before an atomic rename to completed audio.
- Downloads are capped at 256 MB per track. Low storage and server errors are visible. Removal cancels the worker and deletes only that track's local metadata/audio.
- Playback checks completed local files first. Streaming resolutions are cached in memory for up to two minutes (shorter if the URL expires), bounded to 20 tracks and invalidated after playback errors.
- Playback now requests 30-90 seconds of buffer, 2.5 seconds before initial start and 5 seconds after rebuffering.
- Queue metadata is rebuilt on timeline/metadata changes rather than every 500 ms. Position updates still run every 500 ms.
- LRCLIB lookup uses track title, artist and known duration; no approximate unrelated lyrics fallback is used.
- LRC parsing supports repeated timestamps, fractional seconds, signed offsets and backward seeking.
- No database migration, commit rewriting or changes to existing library entries.

## Verification
Final results will be recorded after the live offline checks complete.

## Limits
YouTube access remains unofficial and may need updates. Track availability and lyrics coverage vary. Downloads store the extracted audio as delivered, without transcoding or quality enhancement. Testing the silent emulator cannot establish audible improvement through the host's wired headphones.

References:
- https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- https://developer.android.com/reference/androidx/media3/exoplayer/DefaultLoadControl
- https://lrclib.net/docs
