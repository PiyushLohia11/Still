# Audius verification (2026-09-12)
Official reference: https://api.audius.co/v1 and https://api.audius.co/v1/swagger.yaml
SDK credential guidance: https://docs.audius.co/sdk/
Restricted release access: https://docs.audius.co/developers/guides/gate-release-access/

- Search: GET /v1/tracks/search?query=ambient&limit=2&app_name=Still returned real metadata (including audius:mWB22, Ambient Snow by Blop).
- Details: GET /v1/tracks/{id}.
- Streaming: GET /v1/tracks/{id}/stream supports Range and redirects to provider-issued MP3 access. no_redirect=true returns a JSON data URL.
- An unauthenticated Range 0-2047 request through the official stream endpoint returned HTTP 206, audio/mpeg, 2048 bytes. Probe bytes were discarded, not stored.
- Read endpoints and public streaming worked without a key. A public application API key is optional; some tracks require specifically allowed keys. Bearer tokens/private keys are server-only.
- Still refuses deleted, unavailable, unstreamable, gated, access-authority-protected and disallowed-key tracks. It never fabricates signatures, skips checks, extracts CIDs, or falls back to preview audio.
- Stream access is freshly resolved as a data source opens; expiring URLs are not persisted. Provider HTTP errors remain authoritative.
- Library mutations are local Room operations, not Audius account writes.
- No audio downloads or persistent audio cache. Next-item preloading is bounded, in-memory streaming only; it can be disabled in preferences.
- Audius coverage is limited to its catalog. A free app does not imply universal catalog access or guaranteed free provider quotas.

No API keys or bearer tokens were used in the verification. HTTP byte delivery is not a device playback test.
