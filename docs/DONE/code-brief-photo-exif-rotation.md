# Macaco — ImageStorage / JournalBackup: Preserve EXIF Orientation When Re-encoding

Fixes camera photos rendering sideways: both re-encode paths decode JPEGs and write new ones
without reading the EXIF orientation tag. Touches `ImageStorage.kt`, `JournalBackup.kt`, and
`gradle/libs.versions.toml` + `app/build.gradle.kts` (one new dependency).

**Background:** Cameras (Samsung and Pixel included) commonly store portrait shots as
landscape pixels + an EXIF `Orientation` tag. `BitmapFactory` ignores the tag, and
`Bitmap.compress` writes a JPEG without one — so every photo that goes through
`compressForStorage` (all entry photos) or `compressToBytes` (compact backup export) loses its
rotation and displays sideways in the app, in Drive, and in backups.

---

## Change 0 — Add the AndroidX ExifInterface dependency

`gradle/libs.versions.toml`:

```toml
# [versions]
exifinterface = "1.3.7"

# [libraries]
androidx-exifinterface = { group = "androidx.exifinterface", name = "exifinterface", version.ref = "exifinterface" }
```

`app/build.gradle.kts` dependencies block:

```kotlin
implementation(libs.androidx.exifinterface)
```

(Use `androidx.exifinterface.media.ExifInterface` — unlike the framework class it reads from an
`InputStream` on all supported API levels, min 24 included.)

---

## Change 1 — ImageStorage.compressForStorage: apply EXIF rotation

**Problem:** Decode → scale → re-encode with no orientation handling.

**Fix:** Read the orientation from the source bytes, and rotate/flip the final bitmap before
compressing. Add a shared helper both call sites can use.

```kotlin
// NEW — add to ImageStorage (top-level private helpers)
/** EXIF orientation of [bytes], or ORIENTATION_NORMAL if unreadable. */
private fun exifOrientation(bytes: ByteArray): Int = runCatching {
    androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
        .getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        )
}.getOrDefault(androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)

/** Returns [bitmap] transformed per the EXIF [orientation] (recycling the input if replaced). */
private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = android.graphics.Matrix()
    when (orientation) {
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
        androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        else -> return bitmap
    }
    val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (out !== bitmap) bitmap.recycle()
    return out
}
```

Wire into `compressForStorage` — the final compress block:

```kotlin
// BEFORE (~line 236)
        ByteArrayOutputStream().use { out ->
            final.compress(Bitmap.CompressFormat.JPEG, quality, out)
            final.recycle()
            out.toByteArray()
        }

// AFTER
        val oriented = applyExifOrientation(final, exifOrientation(bytes))
        ByteArrayOutputStream().use { out ->
            oriented.compress(Bitmap.CompressFormat.JPEG, quality, out)
            oriented.recycle()
            out.toByteArray()
        }
```

**File:** `ImageStorage.kt`

---

## Change 2 — JournalBackup.compressToBytes: same fix

**Problem:** The compact-export path re-encodes with the identical omission. (The full-quality
export copies original bytes untouched, so it's unaffected.)

**Fix:** Read the orientation from a separate stream open (this method streams rather than
holding bytes), then transform before compressing. Reuse the same matrix logic — either make
the two helpers above `internal` on `ImageStorage` and call them, or inline the equivalents;
prefer calling `ImageStorage`'s helpers to avoid duplication.

```kotlin
// BEFORE (JournalBackup.compressToBytes, ~line 143)
        val bitmap = runCatching {
            resolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        }.getOrNull() ?: return null

        val baos = java.io.ByteArrayOutputStream()
        val encodeOk = runCatching {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)
        }.also {
            bitmap.recycle()
        }.getOrDefault(false)

// AFTER
        val bitmap = runCatching {
            resolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        }.getOrNull() ?: return null

        // Preserve camera rotation: read the EXIF tag from a fresh stream and bake it in,
        // since re-encoding below strips all EXIF (including Orientation).
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                androidx.exifinterface.media.ExifInterface(stream).getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        val oriented = ImageStorage.applyExifOrientation(bitmap, orientation)

        val baos = java.io.ByteArrayOutputStream()
        val encodeOk = runCatching {
            oriented.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)
        }.also {
            oriented.recycle()
        }.getOrDefault(false)
```

(If sharing the helper, change its visibility in `ImageStorage` from `private` to `internal`
and drop the `private` on `applyExifOrientation`.)

**File:** `JournalBackup.kt`

---

## Scope notes

- Photos already persisted through the old path are permanently rotated (their EXIF is gone) —
  no migration possible; out of scope.
- `AdventureReelEncoder.loadBitmap` also ignores EXIF, but its inputs are photos that already
  went through `compressForStorage` (now baked upright) or Drive copies of the same — after
  this fix, new photos are fine there without changes. Do not modify the encoder in this brief.

## Summary

| # | Change | File |
|---|--------|------|
| 0 | Add `androidx.exifinterface:exifinterface:1.3.7` | `libs.versions.toml`, `app/build.gradle.kts` |
| 1 | `exifOrientation` + `applyExifOrientation` helpers; bake rotation in `compressForStorage` | `ImageStorage.kt` |
| 2 | Bake rotation in compact-export `compressToBytes` | `JournalBackup.kt` |
