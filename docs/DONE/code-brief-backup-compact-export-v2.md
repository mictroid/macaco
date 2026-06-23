# Macaco — JournalBackup: Fix Compact Export Silently Dropping Photos (OOM)

`writeCompressed` decodes photos at full resolution with no memory guard, causing
`OutOfMemoryError` on large phone-camera photos; `runCatching` silently swallows the error and
the photo is excluded from the backup. A 681 MB full backup compacting to 35 MB is the symptom —
almost all photos were OOM'd and dropped, not actually compressed. Fix is a two-pass decode with
`inSampleSize` downscaling to stay within a safe memory budget.

File touched: `data/sync/JournalBackup.kt`.
Context: builds on `code-brief-backup-compact-export.md` already in `docs/DONE/`.

---

## Change: two-pass decode with inSampleSize in writeCompressed

**Problem:** `writeCompressed` calls `BitmapFactory.decodeStream(it)` with no `Options`. This
decodes the full bitmap into `ARGB_8888` memory (4 bytes/pixel). A 12 MP photo (4000 × 3000)
requires 48 MB of heap just for one bitmap. Modern phone cameras routinely produce 12–50 MP
shots. With many large photos in a journal, `decodeStream` throws `OutOfMemoryError`; the
`runCatching { }.getOrNull()` silently returns `null`; `writeCompressed` returns `false`; the
photo is excluded from the ZIP without any warning. The compact backup looks valid but is missing
the vast majority of its photos.

**Fix:** Replace the single-pass `decodeStream` with a two-pass approach:

1. **Pass 1 — bounds only:** decode with `inJustDecodeBounds = true` to read image dimensions
   without allocating a bitmap.
2. **Compute `inSampleSize`:** find the smallest power-of-2 that brings the longest edge within
   `MAX_COMPACT_DIMENSION` (2048 px). This caps decoded memory at ~8 MB for any photo.
3. **Pass 2 — subsampled decode:** decode with `inSampleSize` and `inPreferredConfig = RGB_565`
   (2 bytes/pixel instead of 4 — halves memory again, imperceptible at 80% JPEG quality).
4. Compress and write as before.

A 12 MP photo with `inSampleSize = 2` → 2000 × 1500 at `RGB_565` = 6 MB RAM. With 80 photos
processed one at a time (each bitmap recycled before the next), peak RSS stays under 30 MB.
Output JPEG at 80% for a 2000 × 1500 image is ~400–800 KB, so a 681 MB full backup should
compact to roughly 40–80 MB — not 35 MB (almost nothing) as seen with the dropped-photo bug.

```kotlin
// Replace the entire writeCompressed function:

private companion object {
    // Longest edge of decoded bitmap for compact export. At 2048 px and JPEG 80% the output
    // is indistinguishable from the original at phone-screen sizes, and fits comfortably in
    // heap even on low-RAM devices (2048 × 1536 × RGB_565 = ~6 MB).
    private const val MAX_COMPACT_DIMENSION = 2048
}

/**
 * Decodes the photo at [uriString] at a memory-safe resolution, re-encodes at JPEG 80%
 * directly into [out], and recycles the bitmap. Returns false only if the photo is
 * genuinely unreadable (revoked URI, deleted media) — OOM is prevented by subsampling.
 */
private fun writeCompressed(uriString: String, out: java.io.OutputStream): Boolean {
    val uri = Uri.parse(uriString)
    val resolver = context.contentResolver

    // Pass 1: read dimensions only — no bitmap allocation.
    val boundsOpts = android.graphics.BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    resolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, boundsOpts)
    } ?: return false  // URI not readable

    val rawW = boundsOpts.outWidth
    val rawH = boundsOpts.outHeight
    if (rawW <= 0 || rawH <= 0) return false  // unrecognised format

    // Compute smallest power-of-2 sample that brings longest edge within MAX_COMPACT_DIMENSION.
    var sampleSize = 1
    var longestEdge = maxOf(rawW, rawH)
    while (longestEdge > MAX_COMPACT_DIMENSION) {
        sampleSize *= 2
        longestEdge /= 2
    }

    // Pass 2: decode at subsampled size with RGB_565 (2 bytes/px instead of 4).
    val decodeOpts = android.graphics.BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
    }
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
}
```

The `companion object` with `MAX_COMPACT_DIMENSION` should be placed inside the `JournalBackup`
class body (alongside the existing `json` property). If a `companion object` already exists,
add the constant to it.

**No changes to `exportTo()` or `importFrom()` are needed.** The calling code already handles
`writeCompressed` returning `false` correctly (closes the ZIP entry, excludes the path).

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Replace single-pass full-resolution `decodeStream` in `writeCompressed` with two-pass `inJustDecodeBounds` + `inSampleSize` + `RGB_565` decode to prevent OOM on large photos | `data/sync/JournalBackup.kt` |
