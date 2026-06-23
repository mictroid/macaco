# Macaco — JournalBackup: ZLIB Error During Import

`importFrom` opens a `ZipInputStream` directly over a live SAF-backed stream. When the SAF
provider is Google Drive, it delivers data in chunks. `ZipInputStream.closeEntry()` internally
drains remaining ZLIB-compressed bytes from the current entry — if the underlying stream stalls
or returns a short read mid-entry, the ZLIB decompressor desynchronises and throws
`ZipException: ZLIB inflate error` or `unexpected end of ZLIB input stream`. This happens even
on small backups because the error is about stream delivery, not file size.

File touched: `JournalBackup.kt`.

---

## Fix: pre-download the SAF source to a temp file before opening as ZIP

**Problem:** `ZipInputStream` wraps a live SAF / Drive stream. ZLIB decompression requires a
reliable, contiguous byte stream. SAF providers — especially Drive — deliver data in network
chunks and can return short reads. `closeEntry()` tries to drain any remaining ZLIB data from
the current entry; if the stream stalls mid-drain, the ZLIB state machine throws.

**Fix:** Replace the single-pass streaming approach with an explicit two-phase approach:

- **Phase 1 (DOWNLOADING):** Copy the raw SAF stream to a single local temp file
  (`tempDir/source.zip`) using a `CountingInputStream` for progress. Once complete, the ZIP is
  fully on device — no more network dependency.
- **Phase 2 (RESTORING):** Open the local temp file as the `ZipInputStream`. Local file reads
  are synchronous and never return short reads, so ZLIB decompression works perfectly.

The `ImportPhase`, `onProgress` callback, and progress UI added by the large-backup-import
brief remain unchanged — only the internal stream handling changes.

```kotlin
// JournalBackup.kt — replace the importFrom body with:

suspend fun importFrom(
    src: Uri,
    onEntry: suspend (TravelEntry) -> Unit,
    onProgress: (phase: ImportPhase, current: Int, total: Int) -> Unit = { _, _, _ -> }
): Result<Int> = runCatching {
    val resolver = context.contentResolver

    val tempDir = File(context.cacheDir, "backup_import_${System.currentTimeMillis()}")
    tempDir.mkdirs()

    val totalMb: Int = runCatching {
        resolver.openAssetFileDescriptor(src, "r")?.use { it.length }
    }.getOrNull().let { if (it == null || it <= 0L) -1 else (it / 1024 / 1024).toInt() }

    try {
        // ── Phase 1: download the entire SAF source to a local temp file ─────────────────────────
        // Opening ZipInputStream directly over a Drive-backed SAF stream causes ZLIB errors:
        // closeEntry() tries to drain remaining compressed bytes, but Drive delivers data in
        // network chunks and can stall mid-drain. Copying to a local file first avoids this
        // entirely — local file reads are always contiguous and never stall.
        onProgress(ImportPhase.DOWNLOADING, 0, totalMb)

        val srcZip = File(tempDir, "source.zip")
        resolver.openInputStream(src)?.use { rawIns ->
            var lastReportedMb = -1
            val counting = CountingInputStream(rawIns) { totalBytes ->
                val mb = (totalBytes / 1024 / 1024).toInt()
                if (mb != lastReportedMb) {
                    lastReportedMb = mb
                    onProgress(ImportPhase.DOWNLOADING, mb, totalMb)
                }
            }
            srcZip.outputStream().buffered(65_536).use { out ->
                counting.copyTo(out, bufferSize = 65_536)
            }
        } ?: error("Couldn't open the selected file.")

        // ── Phase 2: extract from the local zip ───────────────────────────────────────────────────
        var backupJson: String? = null

        ZipInputStream(srcZip.inputStream().buffered(65_536)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "backup.json" -> backupJson = zip.readBytes().decodeToString()
                    name.startsWith("photos/") -> {
                        File(tempDir, name.replace("/", "_"))
                            .outputStream()
                            .buffered()
                            .use { out -> zip.copyTo(out, bufferSize = 65_536) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        srcZip.delete() // no longer needed; photos are in tempDir

        val backup = json.decodeFromString<BackupFile>(
            backupJson ?: error("This doesn't look like a Macaco backup.")
        )

        onProgress(ImportPhase.RESTORING, 0, backup.entries.size)

        backup.entries.forEachIndexed { index, entry ->
            onProgress(ImportPhase.RESTORING, index + 1, backup.entries.size)
            val newUris = entry.photoUris.mapNotNull { path ->
                val tempFile = File(tempDir, path.replace("/", "_"))
                if (!tempFile.exists()) return@mapNotNull null
                val uri = ImageStorage.persistBytesToGallery(context, tempFile.readBytes())
                tempFile.delete()
                uri
            }
            onEntry(entry.copy(photoUris = newUris, driveFileIds = emptyList()))
        }
        backup.entries.size
    } finally {
        tempDir.deleteRecursively()
    }
}
```

The `CountingInputStream` and `ImportPhase` classes are unchanged from the previous brief
(`code-brief-large-backup-import-progress`). Code should keep those in place and only replace
the `importFrom` body.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Pre-download SAF source to `source.zip` temp file; open that as `ZipInputStream` instead of the raw SAF stream | `JournalBackup.kt` |
