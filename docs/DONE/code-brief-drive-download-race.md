# Macaco — DrivePhotoSync: Serialize Overlapping Download Passes

Fixes a bug where large-library first syncs (200+ photos on a device that's never had them
locally) silently fail to load most photos even though only a handful of failures are reported.
Touches `data/sync/DrivePhotoSync.kt` only.

---

## Bug: unsynchronized `downloadMissing()` passes race each other on large first-time syncs

**Problem:** `CloudEntrySync`'s Firestore `addSnapshotListener` (in `CloudEntrySync.kt`) can fire
more than once while a large collection loads on a device that has no local cache yet — once for
an initial/partial snapshot and again as the full entry set arrives. `JournalViewModel.init`
re-triggers a download pass on every emission:

```kotlin
viewModelScope.launch {
    cloudEntrySync.entries.collect { entryList ->
        drivePhotoSync.downloadMissingPhotos(entryList)
        ...
    }
}
```

`downloadMissingPhotos()` has no synchronization — every call spawns an independent
`ioScope.launch { downloadMissing(entries) }`:

```kotlin
fun downloadMissingPhotos(entries: List<TravelEntry>) {
    ioScope.launch { downloadMissing(entries) }
}
```

On a small library this is a harmless redundant no-op. At 200+ photos on a first sync, multiple
overlapping passes each compute their own "needed" list from a `_cachedPhotoUris.value` snapshot
taken at their own start time, and each creates its own fresh `Semaphore(DRIVE_DOWNLOAD_CONCURRENCY)`
(6 slots). Several passes running at once push aggregate concurrent Drive API requests well past
6, which is exactly the range where Drive starts returning `403 userRateLimitExceeded` — and two
passes can even write to the same `cacheDir/drive_photos/$fileId.jpg` path at once. Each pass also
tracks its *own* `downloadFailures` counter and fires its *own* "X items couldn't be downloaded"
snackbar via `_errors.trySend(...)`, so the single banner a user sees only reflects one pass's
count, not the true total across every overlapping pass — undercounting the real failure rate.

**Fix:** Serialize the whole download pass with a `Mutex`, mirroring the existing
`driveUploadMutex` pattern used for uploads. A second overlapping call simply waits for the lock;
by the time it acquires it, most (often all) of the work is already reflected in
`_cachedPhotoUris`, so it computes a much smaller (frequently empty) "needed" list instead of
racing the first pass.

### Step 1 — add `driveDownloadMutex` next to the existing `driveUploadMutex`

Find (around line 67-72):

```kotlin
// BEFORE:
private var driveFolderId: String? = null
// Serializes folder lookup/creation AND the upload loops below. Two concurrent upload passes
// (e.g. auto-upload-on-save racing the manual "Sync Now" button) used to both see
// driveFolderId == null / driveFileIds[i] == "" and both create a folder / upload the same
// photo — this lock makes the whole find-or-create-and-upload sequence atomic.
private val driveUploadMutex = Mutex()

private companion object {
```

```kotlin
// AFTER:
private var driveFolderId: String? = null
// Serializes folder lookup/creation AND the upload loops below. Two concurrent upload passes
// (e.g. auto-upload-on-save racing the manual "Sync Now" button) used to both see
// driveFolderId == null / driveFileIds[i] == "" and both create a folder / upload the same
// photo — this lock makes the whole find-or-create-and-upload sequence atomic.
private val driveUploadMutex = Mutex()

// Serializes downloadMissing() passes. The Firestore entries listener can emit more than once
// while a large collection loads on a device with no local cache yet (initial/partial snapshot,
// then the full server snapshot) — without this lock, each emission spawns an independent
// download pass with its own "needed" list (computed from a stale cachedPhotoUris snapshot) and
// its own DRIVE_DOWNLOAD_CONCURRENCY-slot semaphore. Several passes racing at once can push
// aggregate concurrent Drive requests well past that limit, tripping Drive's rate limiting on
// large (200+ photo) libraries, while each pass reports its failure count separately — so the
// single error banner shown to the user undercounts the true failure total. A second overlapping
// call now waits for the lock and finds most of the work already reflected in cachedPhotoUris.
private val driveDownloadMutex = Mutex()

private companion object {
```

### Step 2 — wrap `downloadMissing()`'s body in `driveDownloadMutex.withLock { ... }`

Find the function (around line 318):

```kotlin
// BEFORE:
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
    // ... rest of the existing body unchanged ...
}
```

```kotlin
// AFTER: wrap the entire existing body in driveDownloadMutex.withLock { }. Every existing line
// stays exactly the same — just re-indented one level inside the lock. Do not change any of the
// download/semaphore/error-reporting logic itself, only add the lock around it.
private suspend fun downloadMissing(entries: List<TravelEntry>) = withContext(Dispatchers.IO) {
    driveDownloadMutex.withLock {
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

        if (needed.isEmpty() && neededVideos.isEmpty()) return@withLock

        val drive = runCatching { getDriveService() }.getOrNull() ?: return@withLock
        // ... rest of the existing body unchanged, just re-indented ...
        // NOTE: change every `return@withContext` in the original body to `return@withLock`
        // (there are two: the early-exit above, and none elsewhere in the current source — but
        // double check before editing in case the body has changed since this brief was written).
    }
}
```

**Important implementation note for Code:** `Mutex` is already imported in this file
(`kotlinx.coroutines.sync.Mutex`, used by `driveUploadMutex`), as is `withLock`
(`kotlinx.coroutines.sync.withLock`) — no new imports needed. When wrapping the body, every
`return@withContext` inside the function must become `return@withLock` since the lambda changes
from the `withContext` block to the `withLock` block. Read the current file before editing in
case the body has drifted from what's quoted above.

**File:** `data/sync/DrivePhotoSync.kt`

---

## What this does NOT fix

This does not add retry/backoff for genuine Drive rate-limit errors (a 403 that occurs even with
serialized passes, e.g. from a truly enormous library) — it only removes the *self-inflicted*
concurrency this app was creating by racing its own overlapping passes. If large-library syncs
still show failures after this fix, that's a separate follow-up (exponential backoff on 403s).

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `driveDownloadMutex` next to the existing `driveUploadMutex` | `data/sync/DrivePhotoSync.kt` |
| 2 | Wrap `downloadMissing()`'s body in `driveDownloadMutex.withLock { }` so overlapping entries-flow emissions can't spawn racing download passes | `data/sync/DrivePhotoSync.kt` |
