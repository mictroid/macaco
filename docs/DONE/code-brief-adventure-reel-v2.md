# Macaco — Adventure Reel v2: sharper images, smooth dissolves, animated zoom, branding overlay

One file primary: `data/sync/AdventureReelEncoder.kt`. Caller (`JournalViewModel.kt`) needs
a small signature update for the branding overlay metadata.

Read `docs/DONE/code-brief-adventure-reel.md` for the original feature context.

---

## Fix 1 — Ease-in-out cross-dissolve (eliminates the glitch)

The current dissolve uses linear alpha (`f / FADE_FRAMES`). The human eye perceives linear
alpha as a visual pop at the start and a snap at the end — this is the "glitch". Replace with
a smooth-step cosine curve that eases in and out.

### 1a — Add import

```kotlin
// Add if not present
import kotlin.math.cos
import kotlin.math.PI
```

### 1b — Replace alpha in the fade loop (line ~167)

```kotlin
// BEFORE
for (f in 0 until FADE_FRAMES) {
    coroutineContext.ensureActive()
    val alpha = f.toFloat() / FADE_FRAMES
    postFrame { canvas ->
        drawKenBurns(canvas, prev, 1f, PHOTO_FRAMES - 1)
        drawKenBurns(canvas, bitmap, alpha, f)
    }

// AFTER — cosine ease-in-out: slow start, fast middle, slow end
for (f in 0 until FADE_FRAMES) {
    coroutineContext.ensureActive()
    val alpha = (0.5f - 0.5f * cos(PI * f.toDouble() / FADE_FRAMES)).toFloat()
    postFrame { canvas ->
        drawKenBurns(canvas, prev, 1f, PHOTO_FRAMES - 1)
        drawKenBurns(canvas, bitmap, alpha, f)
    }
```

---

## Fix 2 — Sharper image decode (eliminate blur from undersampling)

`loadBitmap` targets 1080px, but the video is 1280px tall. A photo capped at 1080 and then
scaled up to fill 1280px loses detail. Targeting 1440px gives one sample-size of headroom
above the video height — photos in the 1080–2880px range decode at significantly higher
resolution (sample=2 instead of sample=4), which is where most phone cameras land.

```kotlin
// BEFORE (line ~216)
while (rawLongest / sample > 1080) sample *= 2

// AFTER
while (rawLongest / sample > 1440) sample *= 2
```

---

## Fix 3 — Animated zoom: wider start, gentle pull-in

The current scale is fixed at `maxOf(fill) * 1.08f` — a constant 8% overcrop with only a
slow pan. The 1.08× factor crops aggressively (landscape photos lose ~60% of their width in
the 9:16 frame). Replace with an animated scale that starts at exactly fill (1.00×) and
gently zooms to 1.04× over the photo's duration, so the motion reads as a slow cinematic
pull-in rather than a crop with a pan.

```kotlin
// BEFORE — drawKenBurns (line ~79)
fun drawKenBurns(canvas: Canvas, bitmap: Bitmap, alpha: Float, frameInPhoto: Int) {
    val t = frameInPhoto.toFloat() / PHOTO_FRAMES
    val bw = bitmap.width.toFloat()
    val bh = bitmap.height.toFloat()
    val scale = maxOf(WIDTH / bw, HEIGHT / bh) * 1.08f
    val scaledW = bw * scale
    val scaledH = bh * scale
    val maxDx = (scaledW - WIDTH) / 2f
    val maxDy = (scaledH - HEIGHT) / 2f
    val dx = WIDTH / 2f - scaledW / 2f + maxDx * (0.5f - t * 0.5f)
    val dy = HEIGHT / 2f - scaledH / 2f + maxDy * (0.5f - t * 0.5f)

// AFTER — 1.00× → 1.04× fill; pan from offset toward centre
fun drawKenBurns(canvas: Canvas, bitmap: Bitmap, alpha: Float, frameInPhoto: Int) {
    val t = frameInPhoto.toFloat() / PHOTO_FRAMES
    val bw = bitmap.width.toFloat()
    val bh = bitmap.height.toFloat()
    // Fill scale: exactly enough to cover the frame on the short axis
    val fillScale = maxOf(WIDTH / bw, HEIGHT / bh)
    // Animate from 1.00× to 1.04× — less crop than old 1.08×, slow pull-in
    val scale = fillScale * (1.00f + 0.04f * t)
    val scaledW = bw * scale
    val scaledH = bh * scale
    // Overflow room available for panning (0 if photo perfectly matches frame ratio)
    val maxDx = (scaledW - WIDTH).coerceAtLeast(0f) / 2f
    val maxDy = (scaledH - HEIGHT).coerceAtLeast(0f) / 2f
    // Pan from upper-left offset to centre as photo plays (matching pull-in direction)
    val dx = WIDTH / 2f - scaledW / 2f + maxDx * (1f - t)
    val dy = HEIGHT / 2f - scaledH / 2f + maxDy * (1f - t)
```

The rest of `drawKenBurns` (paint setup, canvas.save/translate/scale/drawBitmap/restore) is
unchanged.

---

## Fix 4 — Branding overlays: location text pill + macaco watermark

Each frame gets two additions:
- **Location/date text pill** — a soft dark teal rounded rectangle at the bottom, containing
  white text (location name · date). Only shown when the photo's `overlayText` is non-null.
- **macaco logo watermark** — the launcher foreground drawable, 48×48px, bottom-right corner,
  at ~15% opacity. Present on every frame.

```
720px
├─────────────────────────────────┤
│                                 │  ↑
│         photo (Ken Burns)       │  1280px
│                                 │
│  ┌─────────────────────────┐   │
│  │  Patagonia · Jun 2025   │   │  ← location pill (y=1152–1224)
│  └─────────────────────────┘   │
│             [🐒]               │  ← logo watermark (bottom-centre, ~15% opacity, y=1232)
└─────────────────────────────────┘
```

### 4a — New data class (add at top of `AdventureReelEncoder.kt`, before the class)

```kotlin
/**
 * Metadata for one photo in the reel.
 * [overlayText] appears as a location/date pill at the bottom of the frame —
 * pass null to skip the overlay for that photo.
 */
data class ReelPhotoMeta(
    val uri: String,
    val overlayText: String? = null   // e.g. "Patagonia · Jun 2025"
)
```

### 4b — Update `encode()` signature

```kotlin
// BEFORE
suspend fun encode(
    photoUris: List<String>,
    outputName: String,
    onProgress: (Float) -> Unit
): Result<Uri>

// AFTER
suspend fun encode(
    photos: List<ReelPhotoMeta>,
    outputName: String,
    onProgress: (Float) -> Unit
): Result<Uri>
```

Inside `encode`, replace `photoUris` with `photos` and update:
```kotlin
// BEFORE (line ~150)
val totalPhotos = photoUris.size
...
for ((photoIdx, uriString) in photoUris.withIndex()) {
    ...
    val bitmap = loadBitmap(uriString) ?: continue

// AFTER
val totalPhotos = photos.size
...
for ((photoIdx, meta) in photos.withIndex()) {
    ...
    val bitmap = loadBitmap(meta.uri) ?: continue
```

### 4c — Add private helpers (add after `loadBitmap`, before the closing `}` of the class)

```kotlin
/**
 * Loads the launcher foreground drawable as a Bitmap for use as a watermark.
 * Returns null if the drawable cannot be found or drawn.
 */
private fun loadLogoBitmap(sizePx: Int): Bitmap? = runCatching {
    val drawable = androidx.core.content.res.ResourcesCompat.getDrawable(
        context.resources, R.drawable.ic_launcher_foreground, context.theme
    ) ?: return@runCatching null
    val bm = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bm)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(c)
    bm
}.getOrNull()

/**
 * Composites the branding layer onto [canvas]:
 *   - Semi-transparent location/date pill (bottom of frame) if [overlayText] is non-null.
 *   - macaco logo watermark (bottom-right, 15% opacity) if [logoBitmap] is non-null.
 *
 * Call this AFTER drawKenBurns so branding always sits on top.
 */
private fun drawBranding(canvas: Canvas, logoBitmap: Bitmap?, overlayText: String?) {
    // ── Location pill ──────────────────────────────────────────────────────
    if (overlayText != null) {
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            // Macaco dark-teal scrim at 70% opacity
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
        // Vertically centre text within the pill (pill midpoint y = (1152+1224)/2 = 1188;
        // baseline ≈ midpoint + textSize*0.35 ≈ 1196)
        canvas.drawText(overlayText, 360f, 1196f, textPaint)
    }

    // ── Logo watermark (bottom-centre) ─────────────────────────────────────
    if (logoBitmap != null) {
        val logoPaint = Paint().apply {
            alpha = 38    // ~15% opacity — visible but unobtrusive
        }
        // 48×48 logo centred horizontally, 8px from bottom edge.
        // Sits just below the location pill (pill ends at y=1224, logo starts at y=1232).
        val logoX = ((WIDTH - 48) / 2).toFloat()   // = 336f
        val logoY = (HEIGHT - 48 - 8).toFloat()     // = 1224f → bottom edge 1272f
        canvas.drawBitmap(logoBitmap, logoX, logoY, logoPaint)
    }
}
```

### 4d — Wire branding into the render loop

Load the logo once before the photo loop (add just before `var prevBitmap: Bitmap?`):

```kotlin
// Add before the photo loop (line ~147)
val logoBitmap = loadLogoBitmap(sizePx = 48)
```

Then add `drawBranding` call inside each `postFrame` — both the dissolve frames and the main
frames — after the `drawKenBurns` call(s):

```kotlin
// Dissolve frames — AFTER
postFrame { canvas ->
    drawKenBurns(canvas, prev, 1f, PHOTO_FRAMES - 1)
    drawKenBurns(canvas, bitmap, alpha, f)
    drawBranding(canvas, logoBitmap, meta.overlayText)   // ← add
}

// Main display frames — AFTER
postFrame { canvas ->
    drawKenBurns(canvas, bitmap, 1f, f)
    drawBranding(canvas, logoBitmap, meta.overlayText)   // ← add
}
```

Clean up the logo bitmap in the `finally` block:

```kotlin
finally {
    logoBitmap?.recycle()   // ← add
    runCatching { encoder.stop() }
    ...
}
```

### 4e — Update the caller in `JournalViewModel.kt`

Find the `AdventureReelEncoder(...).encode(photoUris, ...)` call and map the photo URIs to
`ReelPhotoMeta` objects. Use the entry's location and formatted date as the overlay text —
pass null for entries with no location:

```kotlin
// BEFORE (approximate)
encoder.encode(photoUris, outputName, onProgress)

// AFTER
val reelPhotos = tripEntries
    .sortedBy { it.dateMillis }
    .flatMap { entry ->
        val dateStr = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(entry.dateMillis))
        val overlayText = when {
            entry.location.isNotBlank() -> "${entry.location} · $dateStr"
            else -> null
        }
        entry.photoUris.map { uri ->
            ReelPhotoMeta(uri = uri, overlayText = overlayText)
        }
    }
encoder.encode(reelPhotos, outputName, onProgress)
```

---

## Scope

- **In:** Ease-in-out dissolve; sharper decode (1440px target); animated 1.00→1.04× zoom;
  location/date pill overlay; macaco logo watermark.
- **Out:** Audio/music track — requires a separate `MediaMuxer` audio track, deferred.
- **Out:** Opening/closing title card — deferred to a future reel brief.
- **Out:** Fit-with-blurred-background for landscape photos — deferred; the animated zoom
  already reduces the aggressive crop significantly.
- **Out:** Resolution/format changes — still 720×1280 @ 30fps, 2 Mbps.

---

## Verification

1. **Dissolve** — renders 4+ photos; cross-fade should feel silky (slow start, fast middle,
   slow end). No visible pop or snap between photos.
2. **Sharpness** — check a reel from photos taken on a recent Android camera (~12MP). Text
   visible in photos (signs, menus) should be legible in the video. No soft/blurry render.
3. **Zoom** — photos should feel "wider" than before. The slow pull-in should be visible but
   subtle — not a dramatic zoom, just a gentle lens draw.
4. **Branding** — entries with location set: location + date pill visible at bottom, white
   text readable against the teal scrim. Entries without location: no pill. macaco logo
   watermark visible (faint) bottom-right on all frames.
5. **Regression** — single-photo reel (no dissolve) completes without crash; progress
   callback reaches 1.0.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Cosine ease-in-out alpha in cross-dissolve | `AdventureReelEncoder.kt` |
| 2 | Decode target 1080 → 1440px (sharper bitmaps) | `AdventureReelEncoder.kt` |
| 3 | Animated 1.00→1.04× fill scale (less crop, pull-in motion) | `AdventureReelEncoder.kt` |
| 4 | `ReelPhotoMeta` data class + branding overlay helpers | `AdventureReelEncoder.kt` |
| 5 | Caller maps `photoUris` → `List<ReelPhotoMeta>` with location/date | `JournalViewModel.kt` |
