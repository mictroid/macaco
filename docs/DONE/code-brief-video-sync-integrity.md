# Macaco — Video: Drive Sync Integrity (merge race + Sync-now coverage)

Fixes QA V3/V4 (`docs/qa-video-review-2026-07-04.md`): the parallel photo/video upload
completions can revert each other's Drive IDs (→ duplicate uploads), and Settings "Sync now"
ignores videos entirely. Touches `JournalViewModel.kt`, `DrivePhotoSync.kt`.

---

## Change 1 — saveEntry: one sequential upload pass, one merged save

**Problem:** `saveEntry` launches TWO coroutines — photo upload and video upload — each ending
in `cloudEntrySync.save(latest.copy(<its>FileIds = …))`. Whichever completes second may read a
`latest` that predates the first one's save (the Firestore listener hasn't delivered it yet)
and revert the first one's IDs to `""`. Those files then re-upload on the next save →
duplicates in the user's Drive.

**Fix:** Upload photos then videos in ONE background coroutine and write ONE merged save.

```kotlin
// BEFORE (JournalViewModel.saveEntry, ~line 402)
            // Upload new photos to Drive in the background; persist updated IDs when done.
            if (entry.photoUris.isNotEmpty()) {
                launch {
                    val updatedIds = drivePhotoSync.uploadEntryPhotosOrReport(entry)
                    if (updatedIds != entry.driveFileIds) {
                        // Merge into the LATEST entry — the user may have edited it while the
                        // upload ran; writing the captured `entry` back would revert that edit.
                        val latest = entries.value.find { it.id == entry.id } ?: entry
                        // Ids are positional to the photo list captured at upload start; if the
                        // photos changed since, skip — the next save re-uploads correctly.
                        if (latest.photoUris == entry.photoUris) {
                            cloudEntrySync.save(latest.copy(driveFileIds = updatedIds))
                        }
                    }
                }
            }
            // Upload new videos to Drive in the background; persist updated IDs when done. Mirrors
            // the photo path — positional videoFileIds merged into the latest entry if unchanged.
            if (entry.videoUris.isNotEmpty()) {
                launch {
                    val updatedVideoIds = drivePhotoSync.uploadEntryVideosOrReport(entry)
                    if (updatedVideoIds != entry.videoFileIds) {
                        val latest = entries.value.find { it.id == entry.id } ?: entry
                        if (latest.videoUris == entry.videoUris) {
                            cloudEntrySync.save(latest.copy(videoFileIds = updatedVideoIds))
                        }
                    }
                }
            }

// AFTER
            // Upload new photos AND videos to Drive in ONE background pass with ONE merged save.
            // Two separate launches used to race: each saved latest.copy(<its>FileIds), and the
            // later writer could read a `latest` predating the earlier save, reverting its IDs to
            // "" — which re-uploaded those files on the next save (duplicates in Drive).
            if (entry.photoUris.isNotEmpty() || entry.videoUris.isNotEmpty()) {
                launch {
                    val newPhotoIds =
                        if (entry.photoUris.isNotEmpty()) drivePhotoSync.uploadEntryPhotosOrReport(entry)
                        else entry.driveFileIds
                    val newVideoIds =
                        if (entry.videoUris.isNotEmpty()) drivePhotoSync.uploadEntryVideosOrReport(entry)
                        else entry.videoFileIds
                    if (newPhotoIds != entry.driveFileIds || newVideoIds != entry.videoFileIds) {
                        // Merge into the LATEST entry — the user may have edited it while the
                        // upload ran. Ids are positional to the media lists captured at upload
                        // start; merge each list only if it's unchanged since then.
                        val latest = entries.value.find { it.id == entry.id } ?: entry
                        val photosUnchanged = latest.photoUris == entry.photoUris
                        val videosUnchanged = latest.videoUris == entry.videoUris
                        if ((photosUnchanged && newPhotoIds != latest.driveFileIds) ||
                            (videosUnchanged && newVideoIds != latest.videoFileIds)
                        ) {
                            cloudEntrySync.save(
                                latest.copy(
                                    driveFileIds = if (photosUnchanged) newPhotoIds else latest.driveFileIds,
                                    videoFileIds = if (videosUnchanged) newVideoIds else latest.videoFileIds
                                )
                            )
                        }
                    }
                }
            }
```

**File:** `JournalViewModel.kt`

---

## Change 2 — syncAll: include videos in pending work, progress, and uploads

**Problem:** `DrivePhotoSync.syncAll` computes pending work from `photoUris`/`driveFileIds`
only. Videos added while Drive was disconnected never upload via Settings → "Sync now", and
the card claims "All synced" while video uploads are pending.

**Fix:** Extend the pending predicate, the total count, and the per-entry loop to videos. The
progress unit stays "items" (photos + videos combined).

```kotlin
// BEFORE (syncAll, ~line 278)
        ioScope.launch {
            val pending = entries.filter { entry ->
                entry.photoUris.isNotEmpty() && (
                    entry.driveFileIds.size < entry.photoUris.size ||
                        entry.driveFileIds.any { it.isEmpty() }
                    )
            }
            if (pending.isEmpty()) {
                _syncState.value = DrivePhotoSyncState.Synced
                downloadMissingPhotos(entries)
                return@launch
            }

            val totalPhotos = pending.sumOf { e ->
                e.photoUris.size - e.driveFileIds.count { it.isNotEmpty() }
            }.coerceAtLeast(1)

// AFTER
        ioScope.launch {
            fun TravelEntry.pendingPhotos() =
                photoUris.isNotEmpty() &&
                    (driveFileIds.size < photoUris.size || driveFileIds.any { it.isEmpty() })
            fun TravelEntry.pendingVideos() =
                videoUris.isNotEmpty() &&
                    (videoFileIds.size < videoUris.size || videoFileIds.any { it.isEmpty() })

            val pending = entries.filter { it.pendingPhotos() || it.pendingVideos() }
            if (pending.isEmpty()) {
                _syncState.value = DrivePhotoSyncState.Synced
                downloadMissingPhotos(entries)
                return@launch
            }

            // "Photos" here counts photos + videos — one progress unit per media item.
            val totalPhotos = pending.sumOf { e ->
                (e.photoUris.size - e.driveFileIds.count { it.isNotEmpty() }) +
                    (e.videoUris.size - e.videoFileIds.count { it.isNotEmpty() })
            }.coerceAtLeast(1)
```

And the per-entry loop (~line 300):

```kotlin
// BEFORE
            pending.forEach { entry ->
                val prevSynced = entry.driveFileIds.count { it.isNotEmpty() }
                val newIds = runCatching { uploadEntryPhotos(entry) }
                    .onFailure { e ->
                        Log.e("DrivePhotoSync", "Upload failed for entry ${entry.id}", e)
                        val msg = friendlyDriveError(e)
                        if (firstError == null) firstError = msg
                        _errors.trySend(msg)
                        failed += entry.photoUris.size - prevSynced
                        return@forEach
                    }
                    .getOrThrow()

                val nowSynced = newIds.count { it.isNotEmpty() }
                uploaded += (nowSynced - prevSynced).coerceAtLeast(0)
                failed += (entry.photoUris.size - nowSynced).coerceAtLeast(0)

                if (newIds != entry.driveFileIds) {
                    runCatching { onEntryUpdated(entry.copy(driveFileIds = newIds)) }
                }
                _syncState.value = DrivePhotoSyncState.Syncing(uploaded, totalPhotos)
            }

// AFTER
            pending.forEach { entry ->
                val prevSynced = entry.driveFileIds.count { it.isNotEmpty() } +
                    entry.videoFileIds.count { it.isNotEmpty() }
                val result = runCatching {
                    val photoIds = if (entry.pendingPhotos()) uploadEntryPhotos(entry) else entry.driveFileIds
                    val videoIds = if (entry.pendingVideos()) uploadEntryVideos(entry) else entry.videoFileIds
                    photoIds to videoIds
                }.onFailure { e ->
                    Log.e("DrivePhotoSync", "Upload failed for entry ${entry.id}", e)
                    val msg = friendlyDriveError(e)
                    if (firstError == null) firstError = msg
                    _errors.trySend(msg)
                    failed += (entry.photoUris.size + entry.videoUris.size) - prevSynced
                    return@forEach
                }.getOrThrow()
                val (newIds, newVideoIds) = result

                val nowSynced = newIds.count { it.isNotEmpty() } + newVideoIds.count { it.isNotEmpty() }
                uploaded += (nowSynced - prevSynced).coerceAtLeast(0)
                failed += ((entry.photoUris.size + entry.videoUris.size) - nowSynced).coerceAtLeast(0)

                if (newIds != entry.driveFileIds || newVideoIds != entry.videoFileIds) {
                    runCatching {
                        onEntryUpdated(entry.copy(driveFileIds = newIds, videoFileIds = newVideoIds))
                    }
                }
                _syncState.value = DrivePhotoSyncState.Syncing(uploaded, totalPhotos)
            }
```

**File:** `DrivePhotoSync.kt`

---

## Change 3 — syncPhotosToGoogleDrive callback: merge videoFileIds too

**Problem:** The ViewModel's syncAll callback merges only `driveFileIds`; once Change 2 makes
syncAll report video IDs, they'd be dropped.

```kotlin
// BEFORE (JournalViewModel.syncPhotosToGoogleDrive, ~line 540)
        drivePhotoSync.syncAll(entries.value) { updated ->
            // `updated` was built from the entry list captured at sync start; merge only the
            // driveFileIds into the live entry so a mid-sync user edit isn't reverted.
            val latest = entries.value.find { it.id == updated.id } ?: updated
            if (latest.photoUris == updated.photoUris) {
                cloudEntrySync.save(latest.copy(driveFileIds = updated.driveFileIds))
            }
        }

// AFTER
        drivePhotoSync.syncAll(entries.value) { updated ->
            // `updated` was built from the entry list captured at sync start; merge only the
            // Drive-ID lists into the live entry so a mid-sync user edit isn't reverted. Each
            // list merges independently — ids are positional to the media list captured at start.
            val latest = entries.value.find { it.id == updated.id } ?: updated
            val photosUnchanged = latest.photoUris == updated.photoUris
            val videosUnchanged = latest.videoUris == updated.videoUris
            if (photosUnchanged || videosUnchanged) {
                cloudEntrySync.save(
                    latest.copy(
                        driveFileIds = if (photosUnchanged) updated.driveFileIds else latest.driveFileIds,
                        videoFileIds = if (videosUnchanged) updated.videoFileIds else latest.videoFileIds
                    )
                )
            }
        }
```

**File:** `JournalViewModel.kt`

---

## Scope notes

- `import com.houseofmmminq.macaco.data.model.TravelEntry` is already present in
  `DrivePhotoSync.kt` (needed for the local extension functions in Change 2).
- Settings copy ("N photos uploading") now counts videos as items too — acceptable; renaming
  the string to "items" across 11 locales is optional polish, not required here.

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Single sequential upload pass + one merged save in `saveEntry` | `JournalViewModel.kt` |
| 2 | `syncAll` covers videos (pending, progress, upload, callback payload) | `DrivePhotoSync.kt` |
| 3 | syncAll callback merges `videoFileIds` alongside `driveFileIds` | `JournalViewModel.kt` |
