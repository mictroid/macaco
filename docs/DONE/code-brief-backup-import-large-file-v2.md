# Macaco — JournalBackup / SettingsScreen: Large Backup Import Resilience + Keep Screen On

Two remaining issues with large (600+ MB) backup imports: (1) a single corrupt ZIP entry aborts
the entire import with a misleading "incomplete download" error, and (2) the Android screen
auto-dims and goes black mid-import because a large Drive download takes longer than the device
screen timeout. Touches `data/sync/JournalBackup.kt` and `ui/screens/SettingsScreen.kt`.

Context: builds on `code-brief-backup-import-zlib-fix-v2.md` already in `docs/DONE/`.

---

## Fix 1: per-entry resilience — skip corrupt photo entries instead of aborting

**Problem:** The `catch (e: java.util.zip.ZipException)` block wraps the entire
`ZipFile(srcZip).use { }` block. This is correct for catching a truncated `source.zip` (no
central directory), but it also catches any `ZipException` thrown during per-photo extraction —
e.g. if a single photo entry's compressed data is corrupt in an otherwise valid ZIP. When that
happens, the entire import aborts with the misleading "incomplete download — open in Files app"
error, even though the file downloaded completely and all other entries are fine.

**Fix:** Wrap each *photo* entry extraction in its own `runCatching`. If a photo entry fails
to decompress, log it and continue to the next entry. Its temp file simply won't exist, and the
existing `if (!tempFile.exists()) return@mapNotNull null` check in the RESTORING phase already
handles missing photos gracefully. `backup.json` extraction is NOT wrapped — if the JSON entry
is unreadable the import cannot proceed, so a failure there should still abort.

Find the `ZipFile.use { }` block in `importFrom` (`JournalBackup.kt`):

```kotlin
// BEFORE:
try {
    java.util.zip.ZipFile(srcZip).use { zipFile ->
        val entries = zipFile.entries().toList()

        entries.find { it.name == "backup.json" }?.let { entry ->
            backupJson = zipFile.getInputStream(entry).use { it.readBytes().decodeToString() }
        }

        entries.filter { it.name.startsWith("photos/") }.forEach { entry ->
            File(tempDir, entry.name.replace("/", "_"))
                .outputStream()
                .buffered(65_536)
                .use { out -> zipFile.getInputStream(entry).copyTo(out, bufferSize = 65_536) }
        }
    }
} catch (e: java.util.zip.ZipException) {
    error(
        "The backup file is incomplete — it may not have finished downloading from Drive. " +
        "Open the file in the Files app to force a full download, then try importing again."
    )
}

// AFTER:
try {
    java.util.zip.ZipFile(srcZip).use { zipFile ->
        val entries = zipFile.entries().toList()

        // backup.json is not wrapped — if this fails the import cannot proceed.
        entries.find { it.name == "backup.json" }?.let { entry ->
            backupJson = zipFile.getInputStream(entry).use { it.readBytes().decodeToString() }
        }

        // Each photo entry is extracted independently. A corrupt entry is skipped rather
        // than aborting the whole import. The RESTORING phase already handles a missing
        // temp file with: if (!tempFile.exists()) return@mapNotNull null
        entries.filter { it.name.startsWith("photos/") }.forEach { entry ->
            runCatching {
                File(tempDir, entry.name.replace("/", "_"))
                    .outputStream()
                    .buffered(65_536)
                    .use { out -> zipFile.getInputStream(entry).copyTo(out, bufferSize = 65_536) }
            }
            // Silently skip unreadable entries — the photo will be absent from the
            // restored entry rather than blocking restoration of every other entry.
        }
    }
} catch (e: java.util.zip.ZipException) {
    // Central directory missing or unreadable → file is truncated / incomplete download.
    error(
        "The backup file is incomplete — it may not have finished downloading from Drive. " +
        "Open the file in the Files app to force a full download, then try importing again."
    )
}
```

**File:** `data/sync/JournalBackup.kt`

---

## Fix 2: keep screen on while backup import is running

**Problem:** Downloading a 681 MB ZIP from Google Drive to the local temp file takes 1–2 minutes
on a typical home WiFi connection. The Android default screen timeout is 30–60 seconds. Midway
through the download, the display turns off and the screen appears black — not an app crash, but
the device screen timeout firing. The user has no visual feedback that the import is still in
progress behind the dark screen.

**Fix:** Add a `DisposableEffect` in `SettingsScreen` that sets
`WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` on the activity window while `backupBusy` is
true (import or export in progress), and clears it as soon as `backupBusy` goes false. The flag
only fires during the backup operation — it does not permanently change the screen timeout.

Find the composable body of the backup section in `SettingsScreen.kt` (near where `backupBusy`
is declared, around line 248). Add the `DisposableEffect` immediately after the `backupBusy`
declaration:

```kotlin
// Existing declaration (keep as-is):
var backupBusy by remember { mutableStateOf(false) }

// ADD immediately below:
// Keep the screen on while a backup export or import is running. Large imports take several
// minutes to download; without this the device screen timeout fires mid-operation.
val activity = context as? android.app.Activity
DisposableEffect(backupBusy) {
    if (backupBusy) {
        activity?.window?.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }
    onDispose {
        // Always clear the flag when backupBusy becomes false or the composable leaves.
        activity?.window?.clearFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }
}
```

`context` is already available in `SettingsScreen` (passed as a parameter or via
`LocalContext.current` — use whichever is already in scope). No new imports needed:
`android.app.Activity`, `android.view.WindowManager`, and `DisposableEffect` are all in the
standard Android / Compose libraries already used in this file. Verify the import for
`DisposableEffect` exists (`import androidx.compose.runtime.DisposableEffect`); add if missing.

**File:** `ui/screens/SettingsScreen.kt`

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Wrap each photo-entry extraction in `runCatching` so a single corrupt entry is skipped rather than aborting the whole import | `data/sync/JournalBackup.kt` |
| 2 | `DisposableEffect(backupBusy)` sets `FLAG_KEEP_SCREEN_ON` while import/export is active, clears it on completion | `ui/screens/SettingsScreen.kt` |
