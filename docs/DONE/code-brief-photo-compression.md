# Macaco — ImageStorage: Compress photos on save

Adds WhatsApp-style compress-on-save to `ImageStorage.persistToGallery`.
Touches `ImageStorage.kt` only.

---

## Background

`persistToGallery` currently does a raw byte-for-byte stream copy. A 12 MP camera photo
(4000 × 3000 px, 5–10 MB) is written to `Pictures/Macaco` at full resolution. That same file
is later uploaded to Google Drive and included in backups. Every downstream consumer carries
the full weight of the original.

Target after this change:
| Source photo        | Before      | After      | Reduction |
|---------------------|-------------|------------|-----------|
| 12 MP camera shot   | 5–10 MB     | 300–600 KB | ~90%      |
| 4 K phone photo     | 8–15 MB     | 400–800 KB | ~90%      |
| 1080 px web image   | 80–150 KB   | 70–120 KB  | ~15%      |

Parameters: **max 1920 px on the longest side, JPEG quality 85**.
These match the upper end of WhatsApp's quality tier — visually lossless on any phone screen.

---

## Change 1 — Add `compressForStorage` helper

Add this private function at the bottom of the `ImageStorage` object, above the constants.

```kotlin
// NEW — add to ImageStorage object, before the constants block

/**
 * Decodes [bytes] as a bitmap, scales it down so neither dimension exceeds [maxDim],
 * and re-encodes at [quality]% JPEG. Returns null if the input is not a recognised bitmap
 * format (the caller falls back to the original bytes in that case).
 *
 * Uses a two-pass decode (bounds only → inSampleSize) so the full-resolution pixel data
 * is never loaded into RAM: a 12 MP photo decoded with inSampleSize=4 uses ~2 MB of heap
 * instead of ~36 MB.
 */
private fun compressForStorage(
    bytes: ByteArray,
    maxDim: Int = 1920,
    quality: Int = 85
): ByteArray? = runCatching {
    // Pass 1: read dimensions without decoding pixels.
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    check(opts.outWidth > 0 && opts.outHeight > 0)

    // Calculate the largest power-of-2 subsample that keeps the image above maxDim.
    var sample = 1
    while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) > maxDim) sample *= 2

    // Pass 2: decode at reduced resolution using 16-bit colour (half the RAM of ARGB_8888).
    opts.inJustDecodeBounds = false
    opts.inSampleSize = sample
    opts.inPreferredConfig = Bitmap.Config.RGB_565
    val sampled = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts))

    // Fine-scale to exactly maxDim if the subsampled bitmap is still too large.
    val largest = maxOf(sampled.width, sampled.height)
    val final = if (largest > maxDim) {
        val s = maxDim.toFloat() / largest
        Bitmap.createScaledBitmap(
            sampled,
            (sampled.width * s).toInt(),
            (sampled.height * s).toInt(),
            true
        ).also { sampled.recycle() }
    } else sampled

    ByteArrayOutputStream().use { out ->
        final.compress(Bitmap.CompressFormat.JPEG, quality, out)
        final.recycle()
        out.toByteArray()
    }
}.getOrNull()
```

File: `app/src/main/java/com/houseofmmminq/macaco/util/ImageStorage.kt`

---

## Change 2 — Use `compressForStorage` in `persistToGallery`

Replace the raw stream copy with a read-compress-write pattern in both the API 29+ and
API ≤ 28 paths. If `compressForStorage` returns null (unrecognised format), the original
bytes are used as a safe fallback — no photo is ever lost.

```kotlin
// BEFORE — API 29+ path inside persistToGallery (lines ~73–75)
val ok = resolver.openOutputStream(uri)?.use { output ->
    resolver.openInputStream(source)?.use { input -> input.copyTo(output); true } ?: false
} ?: false

// AFTER — API 29+ path
val sourceBytes = resolver.openInputStream(source)?.use { it.readBytes() } ?: run {
    runCatching { resolver.delete(uri, null, null) }
    return null
}
val outputBytes = compressForStorage(sourceBytes) ?: sourceBytes   // fallback: write original
val ok = resolver.openOutputStream(uri)?.use { output ->
    output.write(outputBytes); true
} ?: false
```

```kotlin
// BEFORE — API ≤ 28 path inside persistToGallery (lines ~91–93)
resolver.openInputStream(source)?.use { input ->
    file.outputStream().use { output -> input.copyTo(output) }
} ?: return null

// AFTER — API ≤ 28 path
val sourceBytes = resolver.openInputStream(source)?.use { it.readBytes() } ?: return null
val outputBytes = compressForStorage(sourceBytes) ?: sourceBytes   // fallback: write original
file.writeBytes(outputBytes)
```

File: `app/src/main/java/com/houseofmmminq/macaco/util/ImageStorage.kt`

---

## Change 3 — New imports

```kotlin
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
```

File: `app/src/main/java/com/houseofmmminq/macaco/util/ImageStorage.kt`

---

## Scope

- **In:** `persistToGallery` — the single entry point for all new entry photos (both photo
  picker and camera capture flows go through here).
- **Out:** `persist` (profile photo, theme background) — these are single small images;
  no change needed.
- **Out:** `persistBytesToGallery` — used only by backup restore, which writes bytes that
  were already compressed when originally saved via `persistToGallery`. No double-compression.
- **Out:** Drive upload, backup export, Coil display — all untouched; they benefit
  automatically because the stored file is already small.
- **No ViewModel changes. No string changes. No new permissions.**

---

## Notes for Code

- `compressForStorage` allocates at most ~2 MB of heap for a 12 MP photo thanks to
  `inSampleSize`. This is safe on the IO dispatcher.
- `persistToGallery` is currently called from the photo-picker result handler. Confirm it
  is dispatched to `Dispatchers.IO` (or wrapped in `withContext(Dispatchers.IO)`) before
  submitting — the raw `copyTo` was fast enough to overlook, but the bitmap decode is CPU
  work that should not block the main thread.
- The JPEG fallback (when `compressForStorage` returns null) means animated GIFs and other
  non-JPEG/PNG formats are stored as-is rather than broken. This matches WhatsApp's behaviour.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `compressForStorage` helper (two-pass decode → scale to 1920 px → JPEG 85%) | `ImageStorage.kt` |
| 2 | Replace raw `copyTo` with read → compress → write in both API paths of `persistToGallery` | `ImageStorage.kt` |
| 3 | Add `Bitmap`, `BitmapFactory`, `ByteArrayOutputStream` imports | `ImageStorage.kt` |
