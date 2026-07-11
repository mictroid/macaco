# Macaco — Drive Sync: Fix video-only entries never downloading

Fixes a data-loss bug where an entry that has videos but no photos-needing-download never pulls
its videos back from Drive — so the clip is missing on any device other than the one that added
it, and after any reinstall. One-line fix in `DrivePhotoSync.kt`.

**Background (read first):** `DrivePhotoSync.downloadMissing()` is the *only* path that pulls
Drive-backed media onto a device that lacks it locally. It is called by `downloadMissingPhotos()`
(fire-and-forget, run whenever the entry list changes) and by `ensurePhotosCached()` (awaited,
used by backup export). Despite the "photos" naming, it is responsible for **both** photos and
videos — the video block is the second half of the same function.

---

## Change 1 — Remove the premature early return that skips videos

**Problem:** The function computes `needed` (photos to fetch), then returns immediately if that
list is empty — *before* it computes or checks `neededVideos`. So any entry with videos to fetch
but no photos to fetch (a video-only entry, or one whose photos are already cached/accessible)
never reaches the video-download block. Confirmed reachable: a video-only entry created on phone A
syncs to phone B via Firestore, but its video's local URI is dead on B; the Drive download that
would fix it is skipped, and the clip shows nothing on B and after any reinstall. The same path
feeds `ensurePhotosCached()`, so such an entry is also silently dropped from backup export.

The correct guard already exists four lines below — the combined
`if (needed.isEmpty() && neededVideos.isEmpty()) return@withContext`. The earlier single-list
return is simply wrong and must be deleted.

```kotlin
// BEFORE — DrivePhotoSync.kt, downloadMissing(), ~lines 242–262
    private suspend fun downloadMissing(entries: List<TravelEntry>) = withContext(Dispatchers.IO) {
        val needed = entries.flatMap { entry ->
            entry.driveFileIds.filterIndexed { i, id ->
                id.isNotEmpty() &&
                    !_cachedPhotoUris.value.containsKey(id) &&
                    (entry.photoUris.getOrNull(i)?.let { !isUriAccessible(it) } ?: true)
            }
        }.distinct()
        if (needed.isEmpty()) return@withContext

        val neededVideos = entries.flatMap { entry ->
            entry.videoFileIds.filterIndexed { i, id ->
                id.isNotEmpty() &&
                    !_cachedPhotoUris.value.containsKey(id) &&
                    (entry.videoUris.getOrNull(i)?.let { !isUriAccessible(it) } ?: true)
            }
        }.distinct()

        if (needed.isEmpty() && neededVideos.isEmpty()) return@withContext

        val drive = runCatching { getDriveService() }.getOrNull() ?: return@withContext

// AFTER — delete the single-list early return; keep the combined guard as the sole gate
    private suspend fun downloadMissing(entries: List<TravelEntry>) = withContext(Dispatchers.IO) {
        val needed = entries.flatMap { entry ->
            entry.driveFileIds.filterIndexed { i, id ->
                id.isNotEmpty() &&
                    !_cachedPhotoUris.value.containsKey(id) &&
                    (entry.photoUris.getOrNull(i)?.let { !isUriAccessible(it) } ?: true)
            }
        }.distinct()

        val neededVideos = entries.flatMap { entry ->
            entry.videoFileIds.filterIndexed { i, id ->
                id.isNotEmpty() &&
                    !_cachedPhotoUris.value.containsKey(id) &&
                    (entry.videoUris.getOrNull(i)?.let { !isUriAccessible(it) } ?: true)
            }
        }.distinct()

        if (needed.isEmpty() && neededVideos.isEmpty()) return@withContext

        val drive = runCatching { getDriveService() }.getOrNull() ?: return@withContext
```

The rest of the function is unchanged — the `if (needed.isNotEmpty())` photo block and the
`if (neededVideos.isNotEmpty())` video block below already guard themselves, so with the premature
return gone, a video-only pass now correctly skips the photo block and runs the video block.

**File:** `DrivePhotoSync.kt`

---

## Scope notes

- Pure deletion of one statement (and the now-orphaned blank line). No signature, no new state,
  no behavioural change for entries that *do* have photos to fetch.
- While here, fix the two misleading comments in `JournalBackup.kt` (~lines 98 and 320) that refer
  to a non-existent `downloadMissingVideos` — the function is `downloadMissing`. Comment-only; the
  wrong name is what disguised this bug. Optional but recommended.

## Verification (on device)

1. On phone A, create an entry with **only** a video (no photos). Let it back up to Drive.
2. On phone B signed into the same account (or phone A after an uninstall + reinstall + media
   permission grant), open that entry → the video should now download and play.
3. Backup export from phone B → the exported zip should include the video-only entry's clip
   (full export) rather than dropping it.

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Delete the premature `if (needed.isEmpty()) return` so video-only entries download from Drive | `DrivePhotoSync.kt` |
| 2 | (optional) Fix stale `downloadMissingVideos` comments | `JournalBackup.kt` |
