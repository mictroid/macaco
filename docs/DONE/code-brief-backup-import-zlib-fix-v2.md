# Macaco — JournalBackup: Switch ZIP Extraction to ZipFile

The v1 fix (pre-downloading the SAF source to `source.zip`) is in place and correct. The
remaining "end of ZLIB input stream" error occurs because `ZipInputStream` is still used to
extract from the local file. If `source.zip` is truncated (Google Drive SAF returning premature
EOF before all bytes are transferred), `ZipInputStream` reads sequentially and only discovers
the problem mid-entry — producing an opaque ZLIB error. Switching to `java.util.zip.ZipFile`
fixes this: it reads the central directory from the *end* of the file on open, so a truncated
download fails immediately with a clear error before extraction even starts.

File touched: `data/sync/JournalBackup.kt`.
Context: builds on `code-brief-backup-import-zlib-fix.md` (v1) already in `docs/DONE/`.

---

## Change: replace ZipInputStream extraction with ZipFile

**Problem:** After `source.zip` is downloaded from the SAF stream, the code opens it with:
```kotlin
ZipInputStream(srcZip.inputStream().buffered(65_536)).use { zip -> ... }
```
`ZipInputStream` trusts the local data is complete. If the Drive SAF provider returned premature
EOF during the `copyTo()` phase (a known behaviour for large uncached Drive files), `source.zip`
is silently shorter than the original, and `ZipInputStream` throws "unexpected end of ZLIB input
stream" when it reaches the truncation point — an error indistinguishable from the original SAF
streaming bug.

`java.util.zip.ZipFile` opens the ZIP by seeking to the central directory at the end of the file.
A truncated file has no central directory, so `ZipFile` throws `ZipException: "End of central
directory record not found"` immediately on construction — before any entry data is read.
This lets us catch the truncation early and surface a user-friendly message.

**Fix:** Replace the `ZipInputStream` extraction block with `ZipFile`. The download phase
(`CountingInputStream` + `copyTo` to `source.zip`) is unchanged.

Find the extraction block in `importFrom` (the part that opens `srcZip` as a `ZipInputStream`):

```kotlin
// BEFORE (keep the download phase above this unchanged):
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
srcZip.delete()

// AFTER:
// ZipFile reads the central directory from the end of the file on open.
// A truncated download has no central directory → immediate, clear ZipException
// rather than a confusing ZLIB error discovered mid-stream.
try {
    java.util.zip.ZipFile(srcZip).use { zipFile ->
        val entries = zipFile.entries().toList()

        // Extract backup.json
        entries.find { it.name == "backup.json" }?.let { entry ->
            backupJson = zipFile.getInputStream(entry).use { it.readBytes().decodeToString() }
        }

        // Extract each photo entry to a temp file
        entries.filter { it.name.startsWith("photos/") }.forEach { entry ->
            File(tempDir, entry.name.replace("/", "_"))
                .outputStream()
                .buffered(65_536)
                .use { out -> zipFile.getInputStream(entry).copyTo(out, bufferSize = 65_536) }
        }
    }
} catch (e: java.util.zip.ZipException) {
    // Central directory missing → file was truncated during download.
    // Rethrow with a message the UI can show directly.
    error(
        "The backup file is incomplete — it may not have finished downloading from Drive. " +
        "Open the file in the Files app to force a full download, then try importing again."
    )
}
srcZip.delete()
```

The `try / catch (ZipException)` wraps only the `ZipFile` block. Any other exception (disk full,
JSON parse error, etc.) propagates normally as before.

**Required import** — `java.util.zip.ZipFile` is in the standard library; no new Gradle
dependency needed. The `import java.util.zip.ZipInputStream` at the top of the file can be
removed once this change is made (it is no longer used in `importFrom`; verify it is not used
elsewhere in the file before removing).

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Replace `ZipInputStream` extraction with `java.util.zip.ZipFile`; wrap in `try/catch(ZipException)` to detect truncated downloads with a user-friendly error | `data/sync/JournalBackup.kt` |
