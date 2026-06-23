# Macaco — DrivePhotoSync: Clear Disk Cache and Show Correct State on Account Switch

When the signed-in Google account changes, `onAccountChanged()` clears the in-memory
`_cachedPhotoUris` map but leaves the actual `cacheDir/drive_photos/*.jpg` files on disk, and
resets sync state to `Idle` instead of `NotConnected`. This causes two problems: stale cached
photo files from Account A persist into Account B's session, and the UI shows no "reconnect
Drive" prompt even though Drive isn't yet connected for the new account.

Touches `data/sync/DrivePhotoSync.kt` only.

---

## Fix 1: delete disk cache files in onAccountChanged()

**Problem:** `onAccountChanged()` empties `_cachedPhotoUris` (the in-memory map) but the backing
files in `context.cacheDir/drive_photos/` are never deleted. If Account A signed back in, their
photos would be skipped on re-download (the `if (!cacheFile.exists())` guard in
`downloadMissingPhotos` would find the file and serve it without re-validating against Drive).
More critically, if the `driveFileId` strings happened to collide between accounts (unlikely but
possible), Account B could be served Account A's cached photo bytes.

**Fix:** Delete all files inside `cacheDir/drive_photos/` as part of `onAccountChanged()`.
The directory itself is re-created on the next `downloadMissingPhotos` call (`mkdirs()` is
already called there).

Find `onAccountChanged()` in `DrivePhotoSync.kt`:

```kotlin
// BEFORE:
fun onAccountChanged() {
    driveFolderId = null
    _cachedPhotoUris.value = emptyMap()
    _syncState.value = DrivePhotoSyncState.Idle
}

// AFTER:
fun onAccountChanged() {
    driveFolderId = null
    _cachedPhotoUris.value = emptyMap()
    _syncState.value = DrivePhotoSyncState.Idle
    // Delete cached Drive photo files from the previous account. The directory is re-created
    // automatically when downloadMissingPhotos runs for the new account.
    JavaFile(context.cacheDir, "drive_photos").listFiles()?.forEach { it.delete() }
}
```

`JavaFile` is already imported in this file as `java.io.File as JavaFile`.

**File:** `data/sync/DrivePhotoSync.kt`

---

## Fix 2: emit NotConnected state immediately after account switch

**Problem:** After `onAccountChanged()` sets `_syncState` to `Idle`, the SettingsScreen Drive
section shows its default "idle" UI — no reconnect prompt, no indication that Drive needs to be
re-linked for the new account. The user only discovers Drive isn't connected when they manually
open Settings or trigger a sync that silently fails.

**Fix:** Check `isDriveConnected()` inside `onAccountChanged()` and emit `NotConnected` if Drive
isn't available for the current GMS account. `isDriveConnected()` is a cheap synchronous call
(no network) — it just checks `GoogleSignIn.getLastSignedInAccount` + `hasPermissions`.

```kotlin
// AFTER (builds on Fix 1 above):
fun onAccountChanged() {
    driveFolderId = null
    _cachedPhotoUris.value = emptyMap()
    // Delete cached Drive photo files from the previous account.
    JavaFile(context.cacheDir, "drive_photos").listFiles()?.forEach { it.delete() }
    // Show NotConnected immediately if the new account hasn't granted Drive scope yet,
    // so SettingsScreen shows the reconnect prompt rather than a stale Idle state.
    _syncState.value = if (isDriveConnected()) DrivePhotoSyncState.Idle
                       else DrivePhotoSyncState.NotConnected
}
```

Note: `JournalViewModel.resetDriveSyncState()` calls `onAccountChanged()` — after this fix it
will also correctly emit `NotConnected` when the reconnect flow is triggered but Drive scope is
not yet granted, so the error banner stays visible until a real sync succeeds.

**File:** `data/sync/DrivePhotoSync.kt`

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Delete `cacheDir/drive_photos/*.jpg` files inside `onAccountChanged()` so stale cached photos from the previous account don't persist on disk | `data/sync/DrivePhotoSync.kt` |
| 2 | Emit `NotConnected` (not `Idle`) in `onAccountChanged()` when the current GMS account lacks Drive scope, so SettingsScreen shows the reconnect prompt immediately | `data/sync/DrivePhotoSync.kt` |
