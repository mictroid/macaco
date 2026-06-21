# Macaco — JournalBackup + SettingsScreen: Compact Backup Export

Adds a "Compact" export option that re-encodes photos at 80% JPEG quality before zipping,
reducing a typical backup by 40–60% with minimal visible quality loss. Full-quality export
remains the default. Touches `data/sync/JournalBackup.kt`, `ui/viewmodel/JournalViewModel.kt`,
and `ui/screens/SettingsScreen.kt`.

---

## 1. Add compact mode to JournalBackup

**Problem:** `exportTo()` writes raw photo bytes with no re-encoding. DEFLATE compression on
already-compressed JPEGs achieves <3% reduction. A 681 MB backup stays 681 MB.

**Fix:** Add a `compact: Boolean = false` parameter. When `true`, decode each photo into a
`Bitmap` and re-compress at JPEG quality 80 directly into the zip output stream — no
intermediate `ByteArray` needed. When `false`, the existing `readBytes()` + `zip.write()`
path is unchanged.

In `JournalBackup.kt`, replace `exportTo()`:

```kotlin
fun exportTo(dest: Uri, entries: List<TravelEntry>, compact: Boolean = false): Result<Int> = runCatching {
    val resolver = context.contentResolver
    resolver.openOutputStream(dest)?.use { os ->
        ZipOutputStream(BufferedOutputStream(os)).use { zip ->
            val exported = entries.map { entry ->
                val paths = entry.photoUris.mapIndexedNotNull { i, uriString ->
                    val path = "photos/${entry.id}_$i.jpg"
                    zip.putNextEntry(ZipEntry(path))
                    val wrote = if (compact) {
                        writeCompressed(uriString, zip)
                    } else {
                        val bytes = readBytes(uriString) ?: return@mapIndexedNotNull null
                        zip.write(bytes)
                        true
                    }
                    zip.closeEntry()
                    if (wrote) path else null
                }
                entry.copy(photoUris = paths, driveFileIds = emptyList())
            }
            val backup = BackupFile(exportedAt = System.currentTimeMillis(), entries = exported)
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(json.encodeToString(backup).toByteArray())
            zip.closeEntry()
        }
    } ?: error("Couldn't open the destination file.")
    entries.size
}

/**
 * Decodes the photo at [uriString] into a Bitmap, re-encodes at JPEG 80% quality directly
 * into [out] (no intermediate ByteArray), then recycles the Bitmap. Returns false if the
 * photo can't be read.
 */
private fun writeCompressed(uriString: String, out: java.io.OutputStream): Boolean {
    val bitmap = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uriString))
            ?.use { android.graphics.BitmapFactory.decodeStream(it) }
    }.getOrNull() ?: return false
    return runCatching {
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
    }.also {
        bitmap.recycle()
    }.isSuccess
}
```

No new imports needed — `BitmapFactory` and `Bitmap` are in `android.graphics`.

---

## 2. Thread compact flag through JournalViewModel

In `JournalViewModel.kt`, add the `compact` parameter:

```kotlin
// BEFORE:
suspend fun exportBackup(dest: android.net.Uri): Result<Int> =
    journalBackup.exportTo(dest, entries.value)

// AFTER:
suspend fun exportBackup(dest: android.net.Uri, compact: Boolean = false): Result<Int> =
    journalBackup.exportTo(dest, entries.value, compact)
```

---

## 3. Add export quality dialog to SettingsScreen

**Problem:** Export launches the SAF file picker immediately. There's no point to ask the
quality question after the file is picked — it needs to happen before.

**Fix:** Tapping Export opens a two-button `AlertDialog`. The chosen quality is stored in
`pendingCompact` state. The existing `backupExportLauncher` reads it when the SAF result
arrives.

### Add state variables (near existing `backupBusy`):

```kotlin
var showExportDialog by remember { mutableStateOf(false) }
var pendingCompact by remember { mutableStateOf(false) }
```

### Change the Export button's onClick:

```kotlin
// BEFORE:
if (isPurchased == true) backupExportLauncher.launch("macaco-backup.zip")

// AFTER:
if (isPurchased == true) showExportDialog = true
```

### Update backupExportLauncher to pass compact flag:

```kotlin
val backupExportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/zip")
) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    val isCompact = pendingCompact          // capture before scope.launch
    backupBusy = true
    backupScope.launch {
        val result = viewModel.exportBackup(uri, compact = isCompact)
        backupBusy = false
        // ... existing result Toast handling unchanged ...
    }
}
```

### Add the dialog (place near other dialogs / bottom of composable body):

```kotlin
if (showExportDialog) {
    AlertDialog(
        onDismissRequest = { showExportDialog = false },
        title = { Text(stringResource(R.string.settings_backup_export_quality_title)) },
        text = { Text(stringResource(R.string.settings_backup_export_quality_body)) },
        confirmButton = {
            // Compact — smaller file
            TextButton(onClick = {
                showExportDialog = false
                pendingCompact = true
                backupExportLauncher.launch("macaco-backup-compact.zip")
            }) {
                Text(stringResource(R.string.settings_backup_export_compact))
            }
        },
        dismissButton = {
            // Full quality — original behaviour
            TextButton(onClick = {
                showExportDialog = false
                pendingCompact = false
                backupExportLauncher.launch("macaco-backup.zip")
            }) {
                Text(stringResource(R.string.settings_backup_export_full))
            }
        }
    )
}
```

---

## New string resources

| Key | EN value |
|-----|----------|
| `settings_backup_export_quality_title` | Export quality |
| `settings_backup_export_quality_body` | Compact re-encodes photos at 80% quality — typically 40–60% smaller with minimal visible difference. Full quality preserves the original photos exactly. |
| `settings_backup_export_compact` | Compact |
| `settings_backup_export_full` | Full quality |

Add to `strings.xml` ×11 languages.

---

## Scope notes

- Import (`importFrom`) is **unchanged** — compact and full-quality backups are the same zip
  format; the only difference is the photo JPEG quality.
- `writeCompressed()` processes one photo at a time. Peak memory per photo is roughly
  (original file size) + (decoded Bitmap ≈ width × height × 4 bytes). For a 24 MP photo
  that's ~8 MB JPEG + ~96 MB Bitmap — manageable for one photo sequentially.
- EXIF metadata (orientation, GPS, date) is **not preserved** in compact mode — `Bitmap.compress`
  strips it. This is acceptable for a backup that re-imports into Macaco (which stores metadata
  in Firestore, not in EXIF).
- The compact export is lossy. The dialog body copy makes this clear.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `compact` param + `writeCompressed()` helper to `exportTo()` | `data/sync/JournalBackup.kt` |
| 2 | Add `compact` param to `exportBackup()` | `ui/viewmodel/JournalViewModel.kt` |
| 3 | Add `showExportDialog` + `pendingCompact` state, dialog, update launcher | `ui/screens/SettingsScreen.kt` |
| 4 | Add 4 new strings | `strings.xml` ×11 |
