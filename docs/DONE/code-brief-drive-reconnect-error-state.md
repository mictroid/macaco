# Macaco — SettingsScreen / JournalViewModel: Drive Error Persists After Reconnect

After a Google Drive 401 expiry, the red error banner "Your Google sign-in expired" stays visible
even after the user successfully reconnects. Files touched: `DrivePhotoSync.kt`,
`JournalViewModel.kt`, `SettingsScreen.kt`.

---

## Fix: clear the Drive error state on reconnect

**Problem:** When Drive fails with a 401/invalid_grant, `DrivePhotoSync._syncState` is set to
`DrivePhotoSyncState.Error("Your Google sign-in expired...")`. On the next app open (or after
the GMS token check) `isDriveConnected()` returns `false`, so `SettingsScreen` shows "Not
connected" AND the red error text simultaneously — because `DriveBackupCard` renders the error
from `syncState` regardless of the `connected` flag.

When the user taps "Connect Google Drive", picks their account, and the `driveSignInLauncher`
callback fires with `driveConnected = true`, the error text does NOT disappear. `syncPhotosToGoogleDrive()` is triggered but if the sync itself re-fails (token not yet fresh) the error
is re-set and persists indefinitely. Even when the sync succeeds, there is a visible window
between the reconnect and the sync completion where the error banner is still shown.

**Fix:** expose `drivePhotoSync.onAccountChanged()` from the ViewModel under a new public
function `resetDriveSyncState()`. `onAccountChanged()` already exists and does exactly what is
needed: clears the folder ID cache and resets `_syncState` to `Idle`. Call it from
`SettingsScreen` immediately when the sign-in result arrives (before the sync is triggered), so
the error clears the instant the user reconnects.

### Step 1 — add `resetDriveSyncState()` to `JournalViewModel.kt`

The function should be placed near `isDriveConnected()` (around line 299):

```kotlin
/** Resets Drive sync state to Idle and clears the per-account folder cache.
 *  Call after the user reconnects their Google account so the error banner
 *  clears immediately rather than waiting for the next successful sync. */
fun resetDriveSyncState() {
    drivePhotoSync.onAccountChanged()
}
```

`onAccountChanged()` in `DrivePhotoSync` already does:
```kotlin
fun onAccountChanged() {
    driveFolderId = null
    _cachedPhotoUris.value = emptyMap()
    _syncState.value = DrivePhotoSyncState.Idle   // ← clears the error
}
```

No changes needed to `DrivePhotoSync.kt` itself.

### Step 2 — call `resetDriveSyncState()` in `SettingsScreen.kt`

In the `driveSignInLauncher` result callback (around line 197), add the reset call before
triggering the sync:

```kotlin
val driveSignInLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) {
    driveConnected = viewModel.isDriveConnected()
    if (driveConnected) {
        viewModel.resetDriveSyncState()      // ← ADD: clears error banner immediately
        viewModel.refreshDriveDownloads()
        viewModel.syncPhotosToGoogleDrive()
    }
}
```

### What the user sees

Before fix:
```
┌─────────────────────────────────────────┐
│ ☁ Connected to Google Drive            │
│   Connected as michaelmtromp@gmail.com  │
│                                         │
│ ⚠ Your Google sign-in expired.         │  ← red text still visible after reconnect
│   Disconnect and reconnect to back up.  │
│                                         │
│ [ Sync Now ]  [ Disconnect ]            │
└─────────────────────────────────────────┘
```

After fix:
```
┌─────────────────────────────────────────┐
│ ☁ Connected to Google Drive            │
│   Connected as michaelmtromp@gmail.com  │
│                                         │
│ [ Sync Now ]  [ Disconnect ]            │  ← clean state, no error
└─────────────────────────────────────────┘
```

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `fun resetDriveSyncState()` that delegates to `drivePhotoSync.onAccountChanged()` | `JournalViewModel.kt` |
| 2 | Call `viewModel.resetDriveSyncState()` in `driveSignInLauncher` callback on successful reconnect | `SettingsScreen.kt` |
