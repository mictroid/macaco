# Macaco — Fix: Print Book PDF renders every photo as a blank placeholder

One file, one function. `data/sync/PrintBookExporter.kt`'s `decodeForPrint()` always returns
`null`, so every page in an exported book shows the gray placeholder fill instead of a photo —
confirmed against a real exported PDF ("My Travels Mew.pdf"): every photo page is flat
`#E0E0E0`, while the cover title text, entry caption bars, and the branded outro page (logo, QR,
text) all render correctly, since none of those go through this function.

---

## The bug

**Problem:** `BitmapFactory.decodeStream(stream, null, opts)` called with
`opts.inJustDecodeBounds = true` **always returns `null`** — that's the documented way to read
`outWidth`/`outHeight` into `opts` without allocating a bitmap. The current code chains an
elvis operator off that always-null result, so it unconditionally bails out before a single
photo is ever decoded:

```kotlin
// data/sync/PrintBookExporter.kt — decodeForPrint(), current code (~line 176-178)
val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
    ?: return@runCatching null
val rawW = boundsOpts.outWidth
val rawH = boundsOpts.outHeight
```

`resolver.openInputStream(uri)?.use { ... }` evaluates to whatever the lambda returns —
`decodeStream(...)`'s return value, which is `null` by design here. So
`?: return@runCatching null` fires on *every* call, valid photo or not, and `rawW`/`rawH` are
never even reached. This exact pattern is why `JournalBackup.compressToBytes` (which this
function was modeled on) deliberately checks only whether the *stream itself* opened, then
discards the bounds-decode's return value on its own line — that separation is what's missing
here.

**Fix:** guard on the stream, not on the decode call's return value — matching
`JournalBackup.compressToBytes`'s proven pattern exactly.

```kotlin
// data/sync/PrintBookExporter.kt — decodeForPrint(), AAFTER
val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
val boundsStream = resolver.openInputStream(uri) ?: return@runCatching null
boundsStream.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
val rawW = boundsOpts.outWidth
val rawH = boundsOpts.outHeight
if (rawW <= 0 || rawH <= 0) return@runCatching null
```

The rest of `decodeForPrint()` — the real pixel decode (`decodeOpts`, no
`inJustDecodeBounds`) and the EXIF-orientation read — is correct as-is and needs no change; both
of those calls return genuine values on success, so their existing `?:` null-checks are legitimate
failure guards, not the same bug.

**File:** `data/sync/PrintBookExporter.kt`.

---

## Verification

1. Generate a print book from a trip with several photos (e.g. re-run the same export that
   produced "My Travels Mew.pdf"). Confirm every content page now shows the actual photo,
   center-cropped full-bleed, not the gray placeholder.
2. Confirm the cover and first-page photos (also routed through `drawFullBleedPhoto` →
   `decodeForPrint`) render correctly too — they share the same function, so they were equally
   broken and should be equally fixed.
3. Confirm the `photosSkipped` count in the post-export snackbar drops to 0 for a normal export
   (previously every photo counted as "skipped" even though it wasn't actually unreadable).
4. Regression-check the genuinely-unreadable case still works: point an entry's `photoUris` at
   a deleted/revoked URI and confirm that *specific* photo still falls back to the placeholder
   (via the legitimate second-decode guard) without affecting the rest of the book.
5. Confirm the branded outro page (logo/QR/text) is unchanged — it doesn't call
   `decodeForPrint` at all, so it was never affected by this bug and shouldn't be touched by the
   fix either.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Fix `decodeForPrint()`'s bounds-decode guard to check the stream, not the always-null decode return value | `PrintBookExporter.kt` |
