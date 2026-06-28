# Macaco — JournalBackup: Compact export always skips photos

Fixes a one-line Kotlin null-check error in `JournalBackup.kt` that causes every photo to be
silently skipped when the user exports a compact backup, producing an empty `photoUris: []`
in the zip's `backup.json` and no `photos/` entries at all.

---

## Change 1 — Fix `compressToBytes` null-check that always short-circuits

**Problem:** `BitmapFactory.decodeStream` called with `inJustDecodeBounds = true` **always
returns null** — that is by design; it reads only the image dimensions without allocating a
bitmap. The current code chains this into a `?.use { } ?: return null` guard:

```kotlin
// BEFORE — JournalBackup.kt line 119–121
resolver.openInputStream(uri)?.use {
    android.graphics.BitmapFactory.decodeStream(it, null, boundsOpts)
} ?: return null  // ← fires on EVERY photo because decodeStream returns null
```

`resolver.openInputStream(uri)?.use { <block> }` evaluates to the *result of the block*.
The block is `BitmapFactory.decodeStream(...)` which returns `null`. So the whole expression
is `null` — and `?: return null` short-circuits immediately, skipping the photo. This happens
regardless of whether the URI is readable or not. The `photosSkipped` counter increments for
every photo, and the exported entry ends up with `photoUris = emptyList()`.

**Fix:** Separate the null check for `openInputStream` from the bounds-decode call. The guard
must check only whether the stream itself is null (meaning the URI is unreadable), not the
return value of `decodeStream`.

```kotlin
// AFTER — JournalBackup.kt: replace lines 119–121
// ① Guard: if the URI is unreadable, bail here.
val streamForBounds = resolver.openInputStream(uri) ?: return null
// ② Read dimensions only — decodeStream with inJustDecodeBounds always returns null; ignore it.
streamForBounds.use {
    android.graphics.BitmapFactory.decodeStream(it, null, boundsOpts)
}
```

No other changes to `compressToBytes` — the logic below this point (checking `rawW`/`rawH`,
computing `sampleSize`, pass-2 decode, JPEG encode) is correct and stays as-is.

File: `app/src/main/java/com/houseofmmminq/macaco/data/sync/JournalBackup.kt`

---

## Scope

- **In:** `compressToBytes` bounds-read guard — the two lines that open the first stream.
- **Out:** `readBytes`, `exportTo`, `importFrom`, the rest of `compressToBytes` — all correct.
- **Out:** Full export path (`compact = false`) — unaffected and working.
- **No ViewModel changes. No UI changes. No new strings. No navigation changes.**

## Verification

After `assembleDebug`:
1. Open Settings → Export Backup → **Compact**.
2. Unzip the resulting file.
3. Confirm `photos/<uuid>_0.jpg` etc. are present alongside `backup.json`.
4. Confirm `backup.json` entries have non-empty `photoUris` arrays.

The `ExportResult.photosSkipped` count (surfaced in the export snackbar if > 0) should be 0
for a device whose photos are readable in MediaStore.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Fix `openInputStream` null-check in `compressToBytes` so `inJustDecodeBounds` decode no longer short-circuits every photo | `JournalBackup.kt` |
