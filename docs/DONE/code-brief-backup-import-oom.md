# Macaco — JournalBackup: Fix Import OOM on Large Backups

`importFrom()` in `JournalBackup.kt` loads every photo in the zip into a single in-memory
`Map<String, ByteArray>` before processing any entries. A 681 MB backup exhausts the Android
heap (~256–512 MB limit) and crashes silently. Fix: stream photos to temp files in `cacheDir`
during the zip pass, then process one photo at a time. Touches only
`data/sync/JournalBackup.kt`.

---

## Change: replace in-memory photo map with temp-file streaming

**Problem:** The current import flow is:
1. Open zip, read ALL entries — every photo `ByteArray` accumulates in `photoBytes` map
2. Only then decode `backup.json` and write photos to the gallery

For a 681 MB backup this means ~681 MB on the heap simultaneously → `OutOfMemoryError`.

**Fix:** Replace the `Map<String, ByteArray>` with a temp directory. During the zip pass,
stream each photo entry to a small temp file (using `copyTo`, not `readBytes`). After the pass,
process entries one photo at a time — `readBytes()` on a single 1–5 MB file is fine — and
delete each temp file immediately after writing to the gallery. Clean up the temp directory in
a `finally` block regardless of success or failure.

### Replace `importFrom()` with:

```kotlin
suspend fun importFrom(src: Uri, onEntry: suspend (TravelEntry) -> Unit): Result<Int> = runCatching {
    val resolver = context.contentResolver

    // Temp directory for photo bytes — avoids holding the entire zip in memory at once.
    val tempDir = java.io.File(context.cacheDir, "backup_import_${System.currentTimeMillis()}")
    tempDir.mkdirs()

    var backupJson: String? = null

    try {
        // Pass 1: stream zip entries to disk rather than into memory.
        resolver.openInputStream(src)?.use { ins ->
            ZipInputStream(BufferedInputStream(ins)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "backup.json" ->
                            backupJson = zip.readBytes().decodeToString()

                        entry.name.startsWith("photos/") -> {
                            // Use a filesystem-safe flat name (replace "/" with "_").
                            val safeName = entry.name.replace("/", "_")
                            java.io.File(tempDir, safeName)
                                .outputStream()
                                .buffered()
                                .use { out -> zip.copyTo(out) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("Couldn't open the selected file.")

        val backup = json.decodeFromString<BackupFile>(
            backupJson ?: error("This doesn't look like a Macaco backup.")
        )

        // Pass 2: process one entry at a time, reading each photo from its temp file.
        backup.entries.forEach { entry ->
            val newUris = entry.photoUris.mapNotNull { path ->
                val safeName = path.replace("/", "_")
                val tempFile = java.io.File(tempDir, safeName)
                if (!tempFile.exists()) return@mapNotNull null
                val uri = ImageStorage.persistBytesToGallery(context, tempFile.readBytes())
                tempFile.delete() // free space immediately after writing to gallery
                uri
            }
            onEntry(entry.copy(photoUris = newUris, driveFileIds = emptyList()))
        }

        backup.entries.size

    } finally {
        // Always clean up temp files, even on failure.
        tempDir.deleteRecursively()
    }
}
```

No changes to the function signature, the callers, or any other method. `exportTo()` is
unchanged — export was never the problem.

### Key differences from current code

| | Current | Fixed |
|---|---------|-------|
| Photo storage during zip pass | `Map<String, ByteArray>` (all in RAM) | Temp files in `cacheDir` |
| Peak memory during import | ~zip file size (681 MB → OOM) | One photo at a time (~1–5 MB) |
| Temp file cleanup | N/A | `finally { tempDir.deleteRecursively() }` |
| Photo streaming | `zip.readBytes()` per photo entry | `zip.copyTo(outputStream)` |

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Replace `photoBytes: Map<String, ByteArray>` with temp-file streaming in `importFrom()` | `data/sync/JournalBackup.kt` |
