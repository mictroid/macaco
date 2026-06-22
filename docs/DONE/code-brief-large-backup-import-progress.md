# Macaco — SettingsScreen / JournalBackup / JournalViewModel: Large Backup Import Progress

Importing a large backup (631 MB) from Google Drive via SAF shows only an indeterminate spinner
with no phase feedback. The user has no way to know if the app is working or frozen. Files
touched: `JournalBackup.kt`, `JournalViewModel.kt`, `SettingsScreen.kt`.

---

## Background

`JournalBackup.importFrom()` does two passes:

- **Pass 1**: Streams the zip from the SAF URI to temp files on disk. For a 631 MB Drive file
  this means Google Drive downloads 631 MB through the SAF provider — this can take several
  minutes with no visible progress.
- **Pass 2**: Reads each temp photo file, writes it to the MediaStore gallery, then deletes
  the temp file.

Currently `backupBusy = true` shows an indeterminate `LinearProgressIndicator` for the entire
duration. There is no indication of which phase is running, no byte/entry count, and no way for
the user to tell whether the app is still working.

---

## Fix 1: add progress reporting to `JournalBackup.importFrom`

**Problem:** `importFrom` has no mechanism to report progress. Pass 1 is a blind stream; Pass 2
loops over entries silently.

**Fix:** add an `onProgress` suspend lambda parameter. Call it at the start of Pass 1 (with
total = -1, meaning "unknown / downloading"), during Pass 1 every ~5 MB of bytes streamed, and
at the start of each entry in Pass 2.

```kotlin
// JournalBackup.kt
// Replace the existing importFrom signature with:

suspend fun importFrom(
    src: Uri,
    onEntry: suspend (TravelEntry) -> Unit,
    onProgress: suspend (phase: ImportPhase, current: Int, total: Int) -> Unit = { _, _, _ -> }
): Result<Int> = runCatching {

    // ... existing setup ...

    var backupJson: String? = null
    var bytesRead = 0L
    // Get file size for pass-1 progress (may be -1 if provider doesn't report it).
    val fileSize: Long = runCatching {
        context.contentResolver.openAssetFileDescriptor(src, "r")?.use { it.length } ?: -1L
    }.getOrDefault(-1L)

    onProgress(ImportPhase.DOWNLOADING, 0, fileSize.toProgressTotal())

    try {
        resolver.openInputStream(src)?.use { ins ->
            // Wrap with a counting stream so we can report byte-level progress.
            val counting = CountingInputStream(ins) { delta ->
                bytesRead += delta
                // Report every ~5 MB to avoid flooding the UI.
                if (bytesRead % (5 * 1024 * 1024) < delta) {
                    onProgress(
                        ImportPhase.DOWNLOADING,
                        bytesRead.toProgressCurrent(),
                        fileSize.toProgressTotal()
                    )
                }
            }
            ZipInputStream(BufferedInputStream(counting)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    // ... existing pass-1 logic unchanged ...
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("Couldn't open the selected file.")

        val backup = json.decodeFromString<BackupFile>(
            backupJson ?: error("This doesn't look like a Macaco backup.")
        )

        onProgress(ImportPhase.RESTORING, 0, backup.entries.size)

        backup.entries.forEachIndexed { index, entry ->
            onProgress(ImportPhase.RESTORING, index + 1, backup.entries.size)
            val newUris = entry.photoUris.mapNotNull { path ->
                // ... existing pass-2 logic unchanged ...
            }
            onEntry(entry.copy(photoUris = newUris, driveFileIds = emptyList()))
        }
        backup.entries.size
    } finally {
        tempDir.deleteRecursively()
    }
}

// Add these helpers at the bottom of JournalBackup.kt (or as top-level in the same file):

enum class ImportPhase { DOWNLOADING, RESTORING }

private fun Long.toProgressTotal(): Int = if (this <= 0L) -1 else (this / 1024 / 1024).toInt()
private fun Long.toProgressCurrent(): Int = (this / 1024 / 1024).toInt()

/** Wraps an InputStream and calls [onRead] with the number of bytes read each time. */
private class CountingInputStream(
    private val wrapped: InputStream,
    private val onRead: (Long) -> Unit
) : InputStream() {
    override fun read(): Int = wrapped.read().also { if (it >= 0) onRead(1L) }
    override fun read(b: ByteArray, off: Int, len: Int): Int =
        wrapped.read(b, off, len).also { if (it > 0) onRead(it.toLong()) }
    override fun close() = wrapped.close()
}
```

---

## Fix 2: expose import progress as `StateFlow` in `JournalViewModel.kt`

**Problem:** `importBackup` is a fire-and-forget coroutine with no progress surface.

**Fix:** add a `_importProgress` `MutableStateFlow` and expose it. Update `importBackup` to
wire `onProgress` to the flow.

```kotlin
// JournalViewModel.kt — add near the other StateFlow declarations:

data class ImportProgress(
    val phase: JournalBackup.ImportPhase,
    val current: Int,   // MB downloaded or entry number
    val total: Int      // total MB (or -1 if unknown) or total entries
)

private val _importProgress = MutableStateFlow<ImportProgress?>(null)
val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

// Replace the existing importBackup function:
suspend fun importBackup(src: Uri): Result<Int> {
    _importProgress.value = null
    return journalBackup.importFrom(
        src = src,
        onEntry = { entry -> cloudEntrySync.save(entry) },
        onProgress = { phase, current, total ->
            _importProgress.value = ImportProgress(phase, current, total)
        }
    ).also {
        _importProgress.value = null
    }
}
```

> Note: `journalBackup` is the `JournalBackup` instance already held by the ViewModel. If it
> is not yet a field, add `private val journalBackup = JournalBackup(applicationContext)` near
> the other dependencies. Check the existing `importBackup` call site to confirm the current
> pattern before refactoring.

---

## Fix 3: show phase-aware progress in `BackupFileCard` in `SettingsScreen.kt`

**Problem:** The card shows only an indeterminate `LinearProgressIndicator` with no text.

**Fix:** Collect `importProgress` and show a two-line status: a label for the phase ("Downloading
backup…" or "Restoring entries…") and a determinate bar where possible.

```
┌─────────────────────────────────────────────┐
│ 📂 Backup to file                           │
│    Save all entries and photos to a .zip    │
│                                             │
│  Downloading backup… 312 MB / 631 MB        │
│  ████████░░░░░░░░░░░░░░░░░  49%            │
│                                             │
│  [ Export ]  [ Import ]   (disabled)        │
└─────────────────────────────────────────────┘

When restoring:

│  Restoring entries… 45 / 120               │
│  ████████████░░░░░░░░░░░░░  37%            │
```

In `SettingsScreen`, collect the new flow and pass it to `BackupFileCard`:

```kotlin
// In SettingsScreen composable, add near the other collectAsState calls:
val importProgress by viewModel.importProgress.collectAsState()

// Pass to BackupFileCard:
BackupFileCard(
    premium = isPurchased == true,
    busy = backupBusy,
    importProgress = importProgress,
    onExport = { ... },
    onImport = { ... }
)
```

Update `BackupFileCard` signature and progress section:

```kotlin
@Composable
private fun BackupFileCard(
    premium: Boolean,
    busy: Boolean,
    importProgress: JournalViewModel.ImportProgress?,  // ← add
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    // ... existing card structure ...

    if (busy) {
        if (importProgress != null) {
            val label = when (importProgress.phase) {
                JournalBackup.ImportPhase.DOWNLOADING ->
                    if (importProgress.total > 0)
                        stringResource(R.string.settings_import_downloading_mb,
                            importProgress.current, importProgress.total)
                    else
                        stringResource(R.string.settings_import_downloading)
                JournalBackup.ImportPhase.RESTORING ->
                    stringResource(R.string.settings_import_restoring,
                        importProgress.current, importProgress.total)
            }
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val progress = if (importProgress.total > 0)
                importProgress.current.toFloat() / importProgress.total
            else -1f   // -1 → indeterminate
            if (progress >= 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        } else {
            // Export or pre-progress: plain indeterminate bar
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
    // ... rest of card ...
}
```

---

## New strings (`strings.xml` × 11 languages)

| Key | EN value |
|-----|----------|
| `settings_import_downloading` | `Downloading backup…` |
| `settings_import_downloading_mb` | `Downloading backup… %1$d MB / %2$d MB` |
| `settings_import_restoring` | `Restoring entries… %1$d / %2$d` |

All 11 supported languages need translations added: en, de, fr, es, it, nl, pt, pl, sv, ja, zh.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `ImportPhase` enum, `onProgress` callback, `CountingInputStream` to `importFrom` | `JournalBackup.kt` |
| 2 | Add `ImportProgress` data class, `importProgress` StateFlow, wire `onProgress` in `importBackup` | `JournalViewModel.kt` |
| 3 | Collect `importProgress`, pass to `BackupFileCard`, show phase label + determinate bar | `SettingsScreen.kt` |
| 4 | Add 3 new string keys (×11 languages) | `strings.xml` |
