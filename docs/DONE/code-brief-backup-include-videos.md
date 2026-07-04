# Macaco — Backup: Bundle Videos in the Export Zip

Implements the approved design for QA V11 (`docs/qa-video-review-2026-07-04.md`): video bytes
join the backup zip so a restore works without Drive. **Full quality = photos + videos;
Compact = compact photos, no videos** (unchanged from today's compact behaviour, giving
Compact a clear identity). Clips are 720p/2 Mbps ≤ 15 s (~4 MB each, max 3/entry), and the
import pipeline already streams hundreds-of-MB zips through temp files — no new
infrastructure. Touches `JournalBackup.kt`, `JournalViewModel.kt`, `SettingsScreen.kt`,
`strings.xml` ×11.

New zip layout (Full export):

```
backup.json           ← BackupFile v2
photos/<id>_<i>.jpg
videos/<id>_<i>.mp4   ← NEW: entry video bytes, original transcoded quality
```

---

## Change 1 — JournalBackup.exportTo: write video entries (Full only)

**Problem:** Videos are excluded; a restore without Drive silently has no videos.

**Fix:** When `compact` is false, stream each readable video into `videos/<id>_<i>.mp4`,
rewrite `videoUris` to zip paths and clear `videoFileIds` (mirroring photos/driveFileIds —
import re-uploads fresh). Compact keeps today's behaviour (no video bytes; keep
`videoUris` + `videoFileIds` for Drive re-fetch). Extend `ExportResult` so the UI can report
skipped videos. Stream video bytes (`copyTo`), don't `readBytes()` — clips are multi-MB.

```kotlin
// BEFORE (ExportResult, ~line 45)
    data class ExportResult(
        val entries: Int,
        val photosWritten: Int,
        val photosSkipped: Int
    )

// AFTER
    data class ExportResult(
        val entries: Int,
        val photosWritten: Int,
        val photosSkipped: Int,
        val videosWritten: Int = 0,
        val videosSkipped: Int = 0
    )
```

```kotlin
// BEFORE (exportTo, ~line 66)
    fun exportTo(dest: Uri, entries: List<TravelEntry>, compact: Boolean = false): Result<ExportResult> = runCatching {
        var photosWritten = 0
        var photosSkipped = 0

// AFTER
    fun exportTo(dest: Uri, entries: List<TravelEntry>, compact: Boolean = false): Result<ExportResult> = runCatching {
        var photosWritten = 0
        var photosSkipped = 0
        var videosWritten = 0
        var videosSkipped = 0
```

The per-entry mapping — videos are written after the photo loop, and the entry copy branches
on `compact`:

```kotlin
// BEFORE (end of the per-entry map, ~line 92)
                    // driveFileIds are device/account-specific; clear them so import re-uploads fresh.
                    // Videos are NOT bundled in the zip (too large) — keep videoUris + videoFileIds so
                    // downloadMissingVideos can re-fetch them from Drive on the next sync. Clear
                    // mediaOrder: it holds this device's photo content-URIs, which are rewritten on
                    // import, so a preserved order would reference stale URIs (displayMedia then falls
                    // back to photos-then-videos).
                    entry.copy(photoUris = paths, driveFileIds = emptyList(), mediaOrder = emptyList())

// AFTER
                    // Full export bundles video bytes too (720p/2Mbps ≤15s ≈ 4 MB each), streamed —
                    // never readBytes() a multi-MB clip into heap. Compact skips videos to stay
                    // small; their Drive IDs survive for downloadMissingVideos re-fetch.
                    val videoPaths = if (compact) entry.videoUris else {
                        entry.videoUris.mapIndexedNotNull { i, uriString ->
                            val path = "videos/${entry.id}_$i.mp4"
                            val ok = runCatching {
                                resolver.openInputStream(Uri.parse(uriString))?.use { input ->
                                    zip.putNextEntry(ZipEntry(path))
                                    input.copyTo(zip, bufferSize = 65_536)
                                    zip.closeEntry()
                                    true
                                } ?: false
                            }.getOrDefault(false)
                            if (ok) { videosWritten++; path } else { videosSkipped++; null }
                        }
                    }
                    // driveFileIds are device/account-specific; clear them so import re-uploads
                    // fresh. Full export does the same for videoFileIds (bytes are in the zip);
                    // Compact keeps them so Drive can re-fetch the missing videos after import.
                    // mediaOrder always cleared: it holds this device's content-URIs, which are
                    // rewritten on import (displayMedia falls back to photos-then-videos).
                    entry.copy(
                        photoUris = paths,
                        driveFileIds = emptyList(),
                        videoUris = videoPaths,
                        videoFileIds = if (compact) entry.videoFileIds else emptyList(),
                        mediaOrder = emptyList()
                    )
```

Result construction (~line 107): pass the two new counts. Also bump the format marker:
`data class BackupFile(val version: Int = 2, …)` — informational only, `ignoreUnknownKeys`
keeps both directions compatible. Update the zip-layout KDoc at the top of the class.

**Caveat for Code:** a failed video *stream copy* after `putNextEntry` leaves a truncated zip
entry — unlike photos, bytes can't be resolved first without heap risk. Acceptable: the entry
is still dropped from `videoPaths` (counted as skipped) so `backup.json` never references it;
import ignores unreferenced zip entries.

**File:** `JournalBackup.kt`

---

## Change 2 — importFrom: extract and restore videos

**Problem:** Import only handles `photos/`; bundled videos would be ignored.

**Fix:** Extract `videos/` entries alongside photos (the extraction filter and temp-file
flattening are shared), then restore each via `persistVideoToGallery` — which already takes a
`File`, so the temp file is handed over directly.

Extraction filter (~line 252 — one-line change):

```kotlin
// BEFORE
                    entries.filter { it.name.startsWith("photos/") }.forEach { entry ->
// AFTER
                    entries.filter { it.name.startsWith("photos/") || it.name.startsWith("videos/") }.forEach { entry ->
```

Restore loop:

```kotlin
// BEFORE (~line 275)
                val newUris = entry.photoUris.mapNotNull { path ->
                    val tempFile = File(tempDir, path.replace("/", "_"))
                    if (!tempFile.exists()) return@mapNotNull null
                    val uri = ImageStorage.persistBytesToGallery(context, tempFile.readBytes())
                    tempFile.delete() // free disk immediately after writing to the gallery
                    uri
                }
                // videoFileIds intentionally NOT cleared (unlike driveFileIds) — videos aren't in the
                // zip, so their Drive IDs must survive for downloadMissingVideos to re-fetch them.
                // mediaOrder cleared (stale photo URIs after the rewrite → photos-then-videos fallback).
                onEntry(entry.copy(photoUris = newUris, driveFileIds = emptyList(), mediaOrder = emptyList()))

// AFTER
                val newUris = entry.photoUris.mapNotNull { path ->
                    val tempFile = File(tempDir, path.replace("/", "_"))
                    if (!tempFile.exists()) return@mapNotNull null
                    val uri = ImageStorage.persistBytesToGallery(context, tempFile.readBytes())
                    tempFile.delete() // free disk immediately after writing to the gallery
                    uri
                }
                // Bundled videos (Full backups, videoUris = "videos/…" zip paths) re-materialize
                // into Movies/Macaco; persistVideoToGallery takes the temp File directly, no heap
                // copy. Compact/legacy backups carry real URIs (no "videos/" prefix) — keep them
                // AND their videoFileIds so downloadMissingVideos re-fetches from Drive.
                val videosBundled = entry.videoUris.any { it.startsWith("videos/") }
                val newVideoUris = if (!videosBundled) entry.videoUris else {
                    entry.videoUris.mapNotNull { path ->
                        val tempFile = File(tempDir, path.replace("/", "_"))
                        if (!tempFile.exists()) return@mapNotNull null
                        val uri = ImageStorage.persistVideoToGallery(context, tempFile)
                        tempFile.delete()
                        uri
                    }
                }
                // mediaOrder cleared (stale URIs after the rewrite → photos-then-videos fallback).
                onEntry(
                    entry.copy(
                        photoUris = newUris,
                        driveFileIds = emptyList(),
                        videoUris = newVideoUris,
                        videoFileIds = if (videosBundled) emptyList() else entry.videoFileIds,
                        mediaOrder = emptyList()
                    )
                )
```

**File:** `JournalBackup.kt`

---

## Change 3 — ViewModel.exportBackup: substitute Drive-cached videos too

**Problem:** The pre-export Drive-cache substitution only rewrites `photoUris`; a Drive-only
video (local URI dead after reinstall) would be skipped even though its bytes sit in the
cache. `ensurePhotosCached` → `downloadMissing` already fetches videos into the shared cache
map, so only the substitution needs extending.

```kotlin
// BEFORE (~line in exportBackup)
        val resolved = if (cached.isEmpty()) all else all.map { entry ->
            val newUris = entry.photoUris.mapIndexed { i, uri ->
                val driveId = entry.driveFileIds.getOrNull(i)
                if (!driveId.isNullOrEmpty()) cached[driveId] ?: uri else uri
            }
            entry.copy(photoUris = newUris)
        }

// AFTER
        val resolved = if (cached.isEmpty()) all else all.map { entry ->
            val newUris = entry.photoUris.mapIndexed { i, uri ->
                val driveId = entry.driveFileIds.getOrNull(i)
                if (!driveId.isNullOrEmpty()) cached[driveId] ?: uri else uri
            }
            // Same substitution for videos — downloadMissing caches them in the same map.
            val newVideoUris = entry.videoUris.mapIndexed { i, uri ->
                val driveId = entry.videoFileIds.getOrNull(i)
                if (!driveId.isNullOrEmpty()) cached[driveId] ?: uri else uri
            }
            entry.copy(photoUris = newUris, videoUris = newVideoUris)
        }
```

**File:** `JournalViewModel.kt`

---

## Change 4 — Settings: dialog copy + result toast

Update the export-quality dialog body to state the video difference, and extend the result
toast to mention skipped videos. In the export-done handler (`exportBackupInBackground`'s
`onDone` in `SettingsScreen`), build the message:

```kotlin
// AFTER (message-building fold, replacing the current success branch)
                    { r ->
                        buildString {
                            append(
                                if (r.photosSkipped > 0) appContext.getString(
                                    R.string.settings_backup_export_done_warn, r.entries, r.photosSkipped
                                ) else appContext.getString(R.string.settings_backup_export_done, r.entries)
                            )
                            if (r.videosSkipped > 0) {
                                append(" ")
                                append(appContext.getString(
                                    R.string.settings_backup_export_videos_warn, r.videosSkipped
                                ))
                            }
                        }
                    },
```

Strings — one revised, one new (×11 languages):

| Key | EN value |
|-----|----------|
| `settings_backup_export_quality_body` (revised) | Full quality preserves original photos and includes your videos. Compact re-encodes photos at 80% quality — typically 40–60% smaller — and leaves videos out (they restore from Google Drive instead). |
| `settings_backup_export_videos_warn` (new) | %1$d video(s) couldn't be included. |

**Files:** `SettingsScreen.kt`, `res/values*/strings.xml`

---

## Scope notes

- No new export progress UI — the busy overlay already covers the (now somewhat longer) Full
  export; per-item export progress is a possible follow-up, not required.
- `mediaOrder` stays cleared on both export paths (restored entries fall back to
  photos-then-videos display) — preserving interleaved order across a backup would need
  index-based ordering instead of URI-based; out of scope.
- Depends on nothing, but pairs naturally with `code-brief-video-sync-integrity.md` (re-upload
  of cleared `videoFileIds` after import rides the fixed saveEntry path).

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Full export streams `videos/<id>_<i>.mp4`, clears `videoFileIds`; ExportResult counts videos; BackupFile v2 | `JournalBackup.kt` |
| 2 | Import extracts `videos/` and restores via `persistVideoToGallery(File)`; legacy/compact zips unchanged | `JournalBackup.kt` |
| 3 | Drive-cache substitution extended to `videoUris` before export | `JournalViewModel.kt` |
| 4 | Dialog copy + skipped-videos toast; 1 revised + 1 new key ×11 | `SettingsScreen.kt`, `strings.xml` |
