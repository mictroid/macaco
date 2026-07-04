# Macaco — Video Feature Code QA, 2026-07-04

Follow-up to `qa-code-review-2026-07-03.md`, covering the video-clips feature landed after
vc53 (commits `d2c2146`, `77d00e5`, `9b985c4`; vc54). Reviewed: `VideoTranscoder.kt`,
`ImageStorage` video additions, `TravelEntry`/`CloudEntrySync` round-trip, `DrivePhotoSync`
video upload/download, `JournalViewModel` save/reel paths, `NewEditEntryScreen` media row,
`EntryDetailScreen` video strip + player, `JournalBackup`, manifest. Items marked **[BRIEF]**
have an implementation brief queued.

The overall architecture is sound — parallel `videoUris`/`videoFileIds` mirrors the photo
pattern (including the padded-ids and merge-latest lessons from the last QA round), the
save-time media reconciliation is correct, and the backup design decision (Drive IDs survive,
bytes not zipped) is coherent.

---

## HIGH

### V1. Videos unreadable after reinstall on Android 13+ **[BRIEF: code-brief-video-permissions.md]**
`MainActivity` requests only `READ_MEDIA_IMAGES`. Videos persist to shared `Movies/Macaco`
precisely so they survive uninstall, but after a reinstall the app can't read them back on
API 33+ without `READ_MEDIA_VIDEO` — the whole survival mechanism silently fails for videos
(every clip falls back to a Drive re-download, or shows nothing without Drive).

### V2. Transcode-failure fallback stores the FULL original clip **[BRIEF: code-brief-video-transcode-guards.md]**
When transcoding fails, `VideoTranscoder` falls back to `copyOriginalToCache` — the unmodified
source, with no duration or size cap. On the S8-class devices where transcode is *known* to
fail (the reason the fallback exists), a user picking a 10-minute 4K clip from the gallery
stores hundreds of MB into MediaStore and uploads it all to Drive. The 15-second product cap
is enforced only by the transcoder that just failed.

---

## MEDIUM

### V3. Photo and video Drive uploads race each other's merge **[BRIEF: code-brief-video-sync-integrity.md]**
`saveEntry` launches two independent coroutines (photo upload, video upload); each completes
with `cloudEntrySync.save(latest.copy(<its>FileIds = …))`. If one completes and saves before
the Firestore listener delivers that write, the other's `latest` is stale and its save reverts
the first one's IDs to `""` — those files then re-upload on the next save, duplicating them in
Drive.

### V4. Settings "Sync now" ignores videos **[BRIEF: code-brief-video-sync-integrity.md]**
`syncAll` computes pending work from `photoUris`/`driveFileIds` only. Videos added while Drive
was disconnected never upload via Sync now, and the card reports "All synced" while video
uploads are pending. (Videos currently upload only on the save that created them.)

### V5. Transcoding overlay doesn't block input **[BRIEF: code-brief-video-transcode-guards.md]**
The full-screen scrim in `NewEditEntryScreen` has no click consumer, and Save isn't disabled
while `transcodingProgress != null`. The user can tap Save (or back) mid-transcode — the entry
saves without the video and the in-flight work is dropped silently.

### V6. Video thumbnails extracted on the main thread **[BRIEF: code-brief-video-thumbnail-perf.md]**
`VideoEntryTile` runs `VideoTranscoder.getFirstFrame` (MediaMetadataRetriever, typically
100 ms–1 s+) inside `remember(uri) { … }` — synchronously during composition. Three tiles in
the detail strip jank the entry-open animation and re-extract on every scroll-back; a slow
Drive-cached file can ANR. Same pattern in the edit screen's `VideoThumbnailTile`.

### V7. Multi-select edge cases in the video picker **[BRIEF: code-brief-video-transcode-guards.md]**
`videoToTrim` is a single slot: picking two >15 s videos in one go shows the trim dialog only
for the last; the others are silently dropped. Multiple short videos transcode in parallel
sharing one `transcodingProgress` — the first completion hides the overlay while others still
run (and V5 then lets the user save half-done).

---

## LOW / decisions

| # | Finding | Where |
|---|---------|-------|
| V8 | `RECORD_AUDIO` is declared but the app never records audio itself — the system camera app does (it holds its own grant). An unused dangerous permission invites Play-review questions. Remove (covered in the permissions brief). | `AndroidManifest.xml` |
| V9 | `android:minSdkVersion="33"` on `READ_MEDIA_VIDEO` — same invalid attribute as the images one (QA L3). Harmless; fix both in the permissions brief. | `AndroidManifest.xml` |
| V10 | `uploadVideo` / the fallback path hardcode `.mp4` + `video/mp4` even when the fallback stored a non-MP4 container (e.g. a picked `.webm`). ExoPlayer sniffs content so playback works; Drive preview may not. Cosmetic. | `DrivePhotoSync.kt`, `VideoTranscoder.kt` |
| V11 | Backups exclude video bytes by design — restoring on a device without Drive connected silently has no videos. Sensible trade-off; add a Help/FAQ line and consider a note in the export-done toast. | `JournalBackup.kt`, docs |
| V12 | Picker allows selecting up to 3 videos even when slots are taken; overflow is dropped with no feedback (`uris.take(canAdd)`). A toast would help. | `NewEditEntryScreen.kt` |

---

## Checked and sound

`TravelEntry` back-compat defaults + Firestore round-trip (fixed in `9b985c4`), the
ClipDataSource trim math (start/tail semantics, negative-trim guard), save-time reconciliation
of all four media lists to display order, `mediaOrder` leftover-append (media never dropped),
ExoPlayer release on dialog dispose, `MediaMetadataRetriever.release()` via try/finally for
API 28, temp-frame cleanup in the reel path, video deletion on entry delete and account switch,
and the video merge-latest guard mirroring the photo fix.
