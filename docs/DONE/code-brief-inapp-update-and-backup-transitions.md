# Macaco — MainActivity / SettingsScreen: In-App Update & Backup Transition Fixes

Two related bugs: (1) the flexible in-app update sometimes requires tapping "Restart" twice and
(2) a black screen appears during the in-app update AND during export/import zip. Both are caused
by the same pattern: a system activity launches (Play installer, SAF picker) with no guard or
visual state change on the app side.

Files touched: `MainActivity.kt`, `SettingsScreen.kt`.

---

## 1. Fix: double-trigger of the flexible update flow

**Problem:** `checkForUpdate()` is called every `onResume`. The flexible update confirmation
dialog itself causes an `onPause` → `onResume` cycle (the dialog is a separate activity).
This means `startUpdateFlowForResult` is called a second time while the first flow is still
active, which can result in two overlapping flows and the user seeing the "Restart" prompt
repeated.

**Fix:** Add a `private var updateFlowStarted = false` flag to `MainActivity`. Only call
`startUpdateFlowForResult` when the flag is false. Set it to true immediately when starting the
flow. Reset it in `onStop()` so a fresh session picks up again.

**File:** `MainActivity.kt`

```kotlin
// Add at the class level, near updateReady:
private var updateFlowStarted = false

// In onStop(), reset the flag so next session re-checks:
override fun onStop() {
    super.onStop()
    updateFlowStarted = false
}

// In checkForUpdate(), guard the start call:
private fun checkForUpdate() {
    appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
        when {
            info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) &&
                !updateFlowStarted -> {        // ← ADD THIS GUARD
                updateFlowStarted = true       // ← MARK AS STARTED
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                )
            }
            info.installStatus() == InstallStatus.DOWNLOADED -> updateReady = true
        }
    }
}
```

---

## 2. Fix: reset `updateReady` after the snackbar is acted on

**Problem:** `LaunchedEffect(updateReady)` only re-fires when `updateReady` changes value. If
`completeUpdate()` triggers an `onPause` / `onResume` cycle before the activity actually
restarts, `checkForUpdate()` runs again and re-sets `updateReady = true` — but since the value
didn't change (it was already `true`), the LaunchedEffect does NOT re-run and `completeUpdate()`
is never called a second time. The snackbar sits there requiring a second tap.

Fix by resetting `updateReady = false` immediately after the snackbar is resolved (whether
action or dismiss), before calling `completeUpdate()`. If the install hasn't kicked in yet,
`checkForUpdate()` on the next `onResume` will set `updateReady = true` again and show a
fresh snackbar.

**File:** `MainActivity.kt`

```kotlin
LaunchedEffect(updateReady) {
    if (updateReady) {
        val result = snackbarHostState.showSnackbar(
            message = updateMessage,
            actionLabel = updateAction,
            duration = SnackbarDuration.Indefinite
        )
        updateReady = false   // ← RESET regardless of action/dismiss
        if (result == SnackbarResult.ActionPerformed) {
            appUpdateManager.completeUpdate()
        }
    }
}
```

---

## 3. Fix: black screen during in-app update install

**Problem:** `completeUpdate()` launches the Play installer as a foreground activity. On Samsung
devices this manifests as a noticeable black screen. Because the app's activity is still "alive"
behind it, there is no clean visual hand-off.

**Fix:** Call `finish()` just before `completeUpdate()`. This closes the current activity
cleanly so there is no black app window behind the Play installer. Play restarts the app with
the new version when it's ready, showing the splash screen normally.

**File:** `MainActivity.kt`

```kotlin
if (result == SnackbarResult.ActionPerformed) {
    finish()                           // ← close activity cleanly first
    appUpdateManager.completeUpdate()
}
```

Note: `finish()` here is safe because `completeUpdate()` for a flexible update immediately
starts the install process and will relaunch the app. If `completeUpdate()` fails (rare), the
user simply reopens the app and the "Restart" snackbar reappears on the next `onResume` (because
`installStatus()` will still be `DOWNLOADED`).

---

## 4. Fix: black screen during backup export / import (SAF picker)

**Problem:** `backupBusy` is set to `true` only *inside* the launcher result callback — i.e.,
after the SAF file picker closes and returns a URI. During the time the picker is open (and
during the transition from app → picker), `backupBusy` is `false` and no loading indicator is
shown. On Samsung devices the activity transition to the SAF picker produces a brief black frame.

**Fix:** Set `backupBusy = true` *before* calling `launcher.launch(...)`. Reset to `false` in
the callback if the user cancelled (uri == null) in addition to the existing reset after the
operation finishes.

**File:** `SettingsScreen.kt`

```kotlin
// EXPORT — update the button click handler:
onClick = {
    showExportDialog = true  // (existing dialog trigger — keep as-is)
}

// After the export dialog is confirmed (where backupExportLauncher.launch is called):
// Change from:
//   backupExportLauncher.launch("Macaco-Backup-${...}.zip")
// To:
backupBusy = true                                         // ← SET BUSY BEFORE LAUNCH
pendingCompact = compact
backupExportLauncher.launch("Macaco-Backup-${...}.zip")

// In the launcher result callback, add the cancellation reset:
val backupExportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/zip")
) { uri ->
    if (uri == null) {
        backupBusy = false   // ← RESET ON CANCEL
        return@rememberLauncherForActivityResult
    }
    val isCompact = pendingCompact
    // backupBusy is already true — leave it set until the operation finishes
    backupScope.launch {
        val result = viewModel.exportBackup(uri, compact = isCompact)
        backupBusy = false
        Toast.makeText(
            context,
            result.fold(
                { context.getString(R.string.settings_backup_export_done, it) },
                { it.message ?: context.getString(R.string.settings_backup_failed) }
            ),
            Toast.LENGTH_LONG
        ).show()
    }
}

// IMPORT — same pattern:
// Before launch:
backupBusy = true                                         // ← SET BUSY BEFORE LAUNCH
backupImportLauncher.launch(arrayOf("application/zip"))

// In the callback:
val backupImportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
) { uri ->
    if (uri == null) {
        backupBusy = false   // ← RESET ON CANCEL
        return@rememberLauncherForActivityResult
    }
    // backupBusy already true
    backupScope.launch {
        val result = viewModel.importBackup(uri)
        backupBusy = false
        Toast.makeText(
            context,
            result.fold(
                { context.getString(R.string.settings_backup_import_done, it) },
                { it.message ?: context.getString(R.string.settings_backup_failed) }
            ),
            Toast.LENGTH_LONG
        ).show()
    }
}
```

Note: The existing `backupBusy` loading state in the UI (disabled buttons / spinner) already
covers the visual feedback. This change just moves the moment it activates from *after* the
picker closes to *before* it opens, so there is a visible in-progress state during the
transition. Code should verify where in `SettingsScreen` the launcher `.launch()` is called and
apply the `backupBusy = true` line there.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `updateFlowStarted` guard to prevent `startUpdateFlowForResult` being called on repeated `onResume` cycles | `MainActivity.kt` |
| 2 | Reset `updateReady = false` after snackbar resolves (before `completeUpdate()`) | `MainActivity.kt` |
| 3 | Call `finish()` before `completeUpdate()` to close the activity cleanly before Play installer takes over | `MainActivity.kt` |
| 4 | Set `backupBusy = true` before launching the SAF export/import picker; reset to `false` on cancel | `SettingsScreen.kt` |
