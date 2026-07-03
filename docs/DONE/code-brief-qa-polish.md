# Macaco — QA Polish Batch: Gallery Back, Camera Process-Death, Reel Robustness, Share Fallback

Four small independent fixes from the 2026-07-03 code QA. Touches `EntryDetailScreen.kt`,
`NewEditEntryScreen.kt`, `ProfileScreen.kt`, `JournalViewModel.kt`, `AdventureReelEncoder.kt`.

---

## Change 1 — Detail screen: system back closes the photo gallery, not the screen

**Problem:** The full-screen gallery overlay (`galleryStartIndex != null`) is just a Box layered
over the Scaffold. Pressing system back while it's open pops the whole EntryDetail screen
instead of closing the overlay.

**Fix:** Add a `BackHandler` scoped to the overlay being open. Place it next to the overlay
block (~line 886).

```kotlin
// BEFORE
        galleryStartIndex?.let { startIndex ->
            val count = maxOf(currentEntry.photoUris.size, currentEntry.driveFileIds.size)

// AFTER
        // System back closes the gallery first; only a second back leaves the screen.
        androidx.activity.compose.BackHandler(enabled = galleryStartIndex != null) {
            galleryStartIndex = null
        }
        galleryStartIndex?.let { startIndex ->
            val count = maxOf(currentEntry.photoUris.size, currentEntry.driveFileIds.size)
```

(Import `androidx.activity.compose.BackHandler` at the top.)

**File:** `EntryDetailScreen.kt`

---

## Change 2 — Camera capture survives process death

**Problem:** `pendingCameraUri` is held in `remember` in both `NewEditEntryScreen` (~line 190)
and `ProfileScreen` (~line 114). Opening the camera routinely gets the app process killed on
low-RAM devices; on return, `TakePicture` reports success but `pendingCameraUri` is null and
the captured photo is silently dropped. (The rest of the entry form already uses
`rememberSaveable` for exactly this scenario.)

**Fix:** Persist the URI string across process death.

```kotlin
// BEFORE (NewEditEntryScreen, ~line 190)
    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

// AFTER
    // Saveable as a string: the camera app backgrounds us and the OS may kill the process;
    // without this the captured photo is lost on return.
    var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingCameraUri: android.net.Uri? = pendingCameraUriString?.let(android.net.Uri::parse)
```

Update the writes: `pendingCameraUri = uri` → `pendingCameraUriString = uri.toString()`, and
`pendingCameraUri = null` → `pendingCameraUriString = null`. The read in the launcher callback
(`val captured = pendingCameraUri`) works unchanged.

Apply the identical pattern in `ProfileScreen.kt` (~line 114 and the `cameraPicker` callback
and photo-source sheet at ~line 192).

**Files:** `NewEditEntryScreen.kt`, `ProfileScreen.kt`

---

## Change 3 — Adventure Reel: per-photo Drive fallback, fail on zero frames, use the pre-allocated Paints

**Problem A (photo resolution):** `startReel` uses `entry.photoUris.ifEmpty { drive cache }` —
all-or-nothing per entry. On a reinstalled device `photoUris` is non-empty but every URI is
dead, so the encoder decodes nothing.

```kotlin
// BEFORE (JournalViewModel.startReel, ~line 115)
                val uris = entry.photoUris.ifEmpty {
                    entry.driveFileIds.mapNotNull { id -> cachedDrivePhotos.value[id] }
                }.filter { it.isNotBlank() }

// AFTER — resolve per photo: prefer the Drive-cached copy when one exists (it only exists
// when the local URI was unreadable), else the local URI. Mirrors EntryDetail's displayPhotoUri.
                val cache = cachedDrivePhotos.value
                val count = maxOf(entry.photoUris.size, entry.driveFileIds.size)
                val uris = (0 until count).mapNotNull { i ->
                    entry.driveFileIds.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { cache[it] }
                        ?: entry.photoUris.getOrNull(i)
                }.filter { it.isNotBlank() }
```

**Problem B (empty output):** if every `loadBitmap` returns null the encoder still finishes and
returns `Result.success` with a zero-frame, broken mp4 that then hits the share sheet.

```kotlin
// BEFORE (AdventureReelEncoder.encode, ~line 226)
                prevBitmap = bitmap
            }
            prevBitmap?.recycle()
            drainEncoder(true)

// AFTER
                prevBitmap = bitmap
            }
            prevBitmap?.recycle()
            check(framesRendered > 0) {
                context.getString(R.string.reel_no_photos_error)
            }
            drainEncoder(true)
```

(`framesRendered` is already in scope; `check` throws inside `runCatching`, surfacing the
existing localized error via `ReelState.Error`.)

**Problem C (per-frame allocations):** the class-level `pillPaint` / `textPaint` / `logoPaint`
fields carry a comment saying "never allocate inside the render loop", but `drawBranding`
shadows all three with fresh local `Paint(...)` objects on every frame (30/s). Delete the local
allocations and use the fields:

```kotlin
// BEFORE (drawBranding, ~line 296)
        if (overlayText != null) {
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                // Macaco dark-teal scrim at 70% opacity.
                color = android.graphics.Color.argb(178, 7, 30, 38)
            }
            canvas.drawRoundRect(
                android.graphics.RectF(32f, 1152f, 688f, 1224f),
                24f, 24f,
                pillPaint
            )
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 22f
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL
                )
            }
            canvas.drawText(overlayText, 360f, 1196f, textPaint)
        }

        if (logoBitmap != null) {
            val logoPaint = Paint().apply {
                alpha = 38    // ~15% opacity — visible but unobtrusive
            }
            val logoX = ((WIDTH - 48) / 2).toFloat()   // = 336f
            val logoY = (HEIGHT - 48 - 8).toFloat()    // = 1224f → bottom edge 1272f
            canvas.drawBitmap(logoBitmap, logoX, logoY, logoPaint)
        }

// AFTER — use the pre-allocated class fields (they already hold identical values)
        if (overlayText != null) {
            canvas.drawRoundRect(
                android.graphics.RectF(32f, 1152f, 688f, 1224f),
                24f, 24f,
                pillPaint
            )
            canvas.drawText(overlayText, 360f, 1196f, textPaint)
        }

        if (logoBitmap != null) {
            val logoX = ((WIDTH - 48) / 2).toFloat()   // = 336f
            val logoY = (HEIGHT - 48 - 8).toFloat()    // = 1224f → bottom edge 1272f
            canvas.drawBitmap(logoBitmap, logoX, logoY, logoPaint)
        }
```

(A reusable `RectF` field is optional; the Paints are the hot allocation.)

**Files:** `JournalViewModel.kt`, `AdventureReelEncoder.kt`

---

## Change 4 — shareEntry: include Drive-cached photos

**Problem:** `shareEntry` builds share URIs from `entry.photoUris` only. On any device other
than the one that added the photos, those URIs are unreadable and the share silently attaches
nothing — even though the app is displaying the photos fine from the Drive cache.

**Fix:** Pass `cachedDrivePhotos` in and resolve per photo like `displayPhotoUri` does. The
Drive-cache files are `file://` URIs under `cacheDir/drive_photos/`, which the existing
FileProvider branch already converts (`file_paths.xml` already exposes cache paths for the
reel — verify `<cache-path>` covers `drive_photos/`, add if missing).

```kotlin
// BEFORE (call site, ~line 234)
                    IconButton(onClick = { shareEntry(context, currentEntry) }) {

// AFTER
                    IconButton(onClick = { shareEntry(context, currentEntry, cachedDrivePhotos) }) {
```

```kotlin
// BEFORE (~line 1077 + the shareUris block ~line 1109)
private fun shareEntry(context: Context, entry: TravelEntry) {
    …
    val shareUris = ArrayList<Uri>(
        entry.photoUris.mapNotNull { uriString ->
            val uri = Uri.parse(uriString)
            …
        }
    )

// AFTER
private fun shareEntry(
    context: Context,
    entry: TravelEntry,
    cachedDrivePhotos: Map<String, String> = emptyMap()
) {
    …
    val photoCount = maxOf(entry.photoUris.size, entry.driveFileIds.size)
    val resolvedUris = (0 until photoCount).mapNotNull { i ->
        entry.driveFileIds.getOrNull(i)?.takeIf { it.isNotEmpty() }
            ?.let { cachedDrivePhotos[it] }
            ?: entry.photoUris.getOrNull(i)
    }
    val shareUris = ArrayList<Uri>(
        resolvedUris.mapNotNull { uriString ->
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "file" -> uri.path?.let { path ->
                    runCatching { FileProvider.getUriForFile(context, authority, File(path)) }.getOrNull()
                }
                else -> uri // already a content:// URI — pass through
            }
        }
    )
```

**File:** `EntryDetailScreen.kt` (+ possibly `res/xml/file_paths.xml`)

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `BackHandler` closes gallery overlay before popping the screen | `EntryDetailScreen.kt` |
| 2 | `pendingCameraUri` → `rememberSaveable` (string) in both camera flows | `NewEditEntryScreen.kt`, `ProfileScreen.kt` |
| 3 | Reel: per-photo Drive fallback; fail on zero rendered frames; use pre-allocated Paints | `JournalViewModel.kt`, `AdventureReelEncoder.kt` |
| 4 | Share resolves Drive-cached photos per index | `EntryDetailScreen.kt`, `file_paths.xml` |
