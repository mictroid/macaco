# Macaco — JournalBackup: Fix Java Exception in Compact Export (writeCompressed)

Two bugs in `writeCompressed` introduced in v2: (1) `bitmap.compress` is called with the live
`ZipOutputStream` as its output — if the JPEG encoder's internal stream writes trigger an
`IOException` mid-entry, it corrupts the open zip entry and throws; (2) the result check uses
`.isSuccess` on a `Result<Boolean>` which is always `true` even when `bitmap.compress` returns
`false`, so silently-failed encodes are treated as successful. Fix: encode into a
`ByteArrayOutputStream` first, verify the result, then write the bytes to zip in one call.

Builds on `code-brief-backup-compact-export-v2.md` already in `docs/DONE/`.

File touched: `data/sync/JournalBackup.kt`.

---

## Change: encode to ByteArrayOutputStream before writing to zip

**Problem 1 (exception):** `bitmap.compress(JPEG, 80, out)` is called where `out` is the live
`ZipOutputStream`. The JPEG encoder writes bytes in chunks via `out.write(...)`. Each `write()`
call goes through the `ZipOutputStream`'s DEFLATE compressor. On some devices this interaction
throws a `java.io.IOException` or `java.util.zip.ZipException` mid-entry, corrupting the open
zip entry and bubbling up as an unhandled exception in the export coroutine.

**Problem 2 (silent failure):** The return value is:
```kotlin
runCatching {
    bitmap.compress(JPEG, 80, out)
}.also {
    bitmap.recycle()
}.isSuccess   // ← ALWAYS true — .isSuccess tests for "no exception", not for "compress == true"
```
`Result<Boolean>.isSuccess` returns `true` whenever the lambda completed without throwing —
regardless of whether `compress` returned `true` or `false`. A silently-failed encode (compress
returns `false`) is reported as success, the photo entry is included in the zip with zero bytes,
and the imported JPEG causes a decode error.

**Fix:** Encode into a separate `ByteArrayOutputStream`, check both that no exception occurred
AND that `compress` returned `true` AND that bytes were actually written, then write all bytes
to zip in one atomic call.

Replace the entire `writeCompressed` function body from the `val bitmap = …` line onwards:

```kotlin
// BEFORE (from val bitmap = … to end of function):
val bitmap = runCatching {
    resolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, decodeOpts)
    }
}.getOrNull() ?: return false

return runCatching {
    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
}.also {
    bitmap.recycle()
}.isSuccess

// AFTER:
val bitmap = runCatching {
    resolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, decodeOpts)
    }
}.getOrNull() ?: return false

// Encode into a ByteArrayOutputStream first — keeps the JPEG encoder's chunked writes
// away from the ZipOutputStream's DEFLATE state, preventing IOException mid-entry.
val baos = java.io.ByteArrayOutputStream()
val encodeOk = runCatching {
    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)
}.also {
    bitmap.recycle()
}.getOrDefault(false)

// Verify encode succeeded AND produced bytes (compress returning false = silent failure).
if (!encodeOk || baos.size() == 0) return false

// Write all JPEG bytes to the zip entry in one call — no chunked interaction with Deflater.
return runCatching { baos.writeTo(out) }.isSuccess
```

`java.io.ByteArrayOutputStream` needs no import (it's in `java.io` which is already used in this
file). No other changes.

**File:** `data/sync/JournalBackup.kt`

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Encode bitmap to `ByteArrayOutputStream` before writing to zip, preventing IOException from JPEG encoder interacting with ZipOutputStream's DEFLATE state | `data/sync/JournalBackup.kt` |
| 2 | Replace `.isSuccess` with `.getOrDefault(false)` + explicit `baos.size() > 0` check so silently-failed encodes don't produce empty zip entries | `data/sync/JournalBackup.kt` |
