# Macaco — Drive sync: accurate "Synced" state + surface download failures

Two small honesty fixes in `DrivePhotoSync.kt`: `syncAll` currently reports `Synced` before its
trailing download pass has finished, and `downloadMissing` swallows every per-file download
failure silently (upload failures get snackbars; downloads never do). One file touched.

**Background (read first):** `syncAll` runs uploads with progress, then kicks off downloads via
`downloadMissingPhotos(entries)` — a fire-and-forget wrapper that launches `downloadMissing()` on
`ioScope`. Both `syncState` (Settings shows it) and `_errors` (snackbars) already exist; this
brief only re-orders/report-wires what's already there. `downloadMissing` is also called from
`ensurePhotosCached` (backup/print export) and the ViewModel's auto-download — those callers'
behavior must not change (they tolerate partial results by design; export counts skipped media
itself).

## Change 1 — `syncAll` awaits downloads before declaring `Synced`

**Problem:** both completion paths flip state to `Synced` and *then* start downloads:

```kotlin
// BEFORE — syncAll(), the no-pending-uploads path
            if (pending.isEmpty()) {
                _syncState.value = DrivePhotoSyncState.Synced
                downloadMissingPhotos(entries)
                return@launch
            }
```
```kotlin
// BEFORE — syncAll(), after the upload loop
            _syncState.value = when {
                failed == 0 -> DrivePhotoSyncState.Synced
                ...
            }
            if (failed == 0) downloadMissingPhotos(entries)
```

A user on a fresh device taps "Sync now", sees "Synced" immediately, and the photos are still
trickling in. We're already inside `ioScope.launch`, so the suspend body can simply be awaited.

**Fix:**

```kotlin
// AFTER — no-pending-uploads path: download first, then report
            if (pending.isEmpty()) {
                downloadMissing(entries)
                _syncState.value = DrivePhotoSyncState.Synced
                return@launch
            }
```
```kotlin
// AFTER — after the upload loop
            if (failed == 0) downloadMissing(entries)
            _syncState.value = when {
                failed == 0 -> DrivePhotoSyncState.Synced
                uploaded == 0 -> DrivePhotoSyncState.Error(
                    firstError ?: "Backup failed. Check your Google Drive connection."
                )
                else -> DrivePhotoSyncState.Error("$failed ${if (failed == 1) "photo" else "photos"} couldn't be backed up")
            }
```

(Optional polish, implementer's call: keep the last `Syncing(uploaded, totalPhotos)` state showing
while the download pass runs, so the spinner doesn't stall on the final upload count.)

**File:** `app/src/main/java/com/houseofmmminq/macaco/data/sync/DrivePhotoSync.kt`

## Change 2 — count download failures and surface one snackbar

**Problem:** in `downloadMissing`, both the photo and video loops wrap the Drive call in
`runCatching { ... }` with no `onFailure` reporting (the video loop's `onFailure` only deletes the
partial file). A device that can't pull its media just shows empty slots with no explanation.

**Fix:** count failures across both loops and send one aggregate error through the existing
`_errors` channel. Failure = the Drive call threw AND the cache file still doesn't exist (a
pre-existing cached file is success). Use an `AtomicInteger` — the loops run concurrently under
the semaphore.

```kotlin
// BEFORE — photo loop body
                        downloadSemaphore.withPermit {
                            val cacheFile = JavaFile(cacheDir, "$fileId.jpg")
                            if (!cacheFile.exists()) {
                                runCatching {
                                    val out = ByteArrayOutputStream()
                                    drive.files().get(fileId).executeMediaAndDownloadTo(out)
                                    cacheFile.writeBytes(out.toByteArray())
                                }
                            }
                            if (cacheFile.exists()) newEntries[fileId] = Uri.fromFile(cacheFile).toString()
                        }
```

```kotlin
// AFTER — declare next to `newEntries`:
        val downloadFailures = java.util.concurrent.atomic.AtomicInteger(0)

// photo loop body:
                        downloadSemaphore.withPermit {
                            val cacheFile = JavaFile(cacheDir, "$fileId.jpg")
                            if (!cacheFile.exists()) {
                                runCatching {
                                    val out = ByteArrayOutputStream()
                                    drive.files().get(fileId).executeMediaAndDownloadTo(out)
                                    cacheFile.writeBytes(out.toByteArray())
                                }.onFailure { e ->
                                    Log.e("DrivePhotoSync", "Download failed for $fileId", e)
                                    downloadFailures.incrementAndGet()
                                }
                            }
                            if (cacheFile.exists()) newEntries[fileId] = Uri.fromFile(cacheFile).toString()
                        }
```

Mirror the same `.onFailure { ... }` addition in the video loop (append the log + increment inside
its existing `.onFailure { cacheFile.delete() }` block). Then, after the `newEntries` fold at the
end of `downloadMissing`:

```kotlin
        if (downloadFailures.get() > 0) {
            _errors.trySend(
                "${downloadFailures.get()} ${if (downloadFailures.get() == 1) "item" else "items"} couldn't be downloaded from Google Drive. Pull to refresh to retry."
            )
        }
```

**Scope note:** the ViewModel's auto-download runs on every entry-list change; to avoid repeated
snackbars while offline, the aggregate error is acceptable as-is (Firestore's offline path doesn't
reach here — `getDriveService()` fails first and returns silently, unchanged). If repeated
snackbars show up in testing, gate the `trySend` to once per process with a simple `@Volatile`
flag reset by `onAccountChanged()` — implementer's call.

**File:** `app/src/main/java/com/houseofmmminq/macaco/data/sync/DrivePhotoSync.kt`

## Verification

`assembleDebug`. On-device: fresh sign-in on a journal with Drive-backed media → Settings shows
Syncing until photos are actually visible, then Synced. Airplane-mode variant: "Sync now" with
uncached Drive media → error snackbar instead of a silent Synced with empty slots. Backup export
and Print Book still complete with partial media (counts reported, unchanged).

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Await `downloadMissing` before setting `Synced` (both paths) | `data/sync/DrivePhotoSync.kt` |
| 2 | Count download failures (AtomicInteger) + one aggregate `_errors` snackbar | `data/sync/DrivePhotoSync.kt` |
