# Macaco — Adventure Reel v3: stutter fix (Ken Burns continuity + Paint pre-allocation)

Two files: `data/sync/AdventureReelEncoder.kt` only. No caller changes needed.

Read `docs/DONE/code-brief-adventure-reel-v2.md` for prior context (branding overlay, cosine
dissolve, 1440px decode, animated zoom — all implemented). This brief fixes two new bugs
discovered from a real recording on Galaxy A53.

---

## Fix 1 — Ken Burns snap-back (primary stutter cause)

**Problem:** After each 15-frame cross-dissolve into a new photo, the new photo's Ken Burns
has run from `t = 0` to `t = 14/90 = 0.156`. The main display loop then resets to `f = 0`
(`t = 0`), so the pan position snaps 18–77 pixels back to the upper-left extreme in a single
frame (1/30 s). This is a visible jerk at every photo transition.

Example for a typical landscape photo (4000×3000, fillScale = 0.427):
```
End of dissolve:  pan offset = maxDx * (1 - 0.156) = 493 * 0.844 = 416 px from center-left
Start of main:    pan offset = maxDx * (1 - 0.000) = 493 * 1.000 = 493 px from center-left
                                                                    ↑ 77 px snap in 1 frame
```

**Fix:** Start the main display loop at `FADE_FRAMES` instead of `0`, so the Ken Burns
animation is continuous from the first dissolve frame through to the last display frame.
Cap `t` at `1.0` in `drawKenBurns` because the extended loop runs `f` up to
`PHOTO_FRAMES + FADE_FRAMES - 1 = 104`, which would otherwise produce `t > 1`.

The total frame count per photo is unchanged: dissolve (15) + main (90) = 105 frames.

### 1a — Cap `t` in `drawKenBurns`

```kotlin
// BEFORE (line ~94 in drawKenBurns)
val t = frameInPhoto.toFloat() / PHOTO_FRAMES          // 0..1

// AFTER — coerce so the extended main loop (f up to 104) stays within [0,1]
val t = (frameInPhoto.toFloat() / PHOTO_FRAMES).coerceAtMost(1f)
```

### 1b — Offset the main display loop

```kotlin
// BEFORE (line ~196) — main photo display
for (f in 0 until PHOTO_FRAMES) {
    coroutineContext.ensureActive()
    postFrame { canvas ->
        drawKenBurns(canvas, bitmap, 1f, f)
        drawBranding(canvas, logoBitmap, meta.overlayText)
    }
    drainEncoder(false)
    framesRendered++
    onProgress((framesRendered.toFloat() / totalFrames).coerceAtMost(1f))
}

// AFTER — start at FADE_FRAMES so the first frame continues from where the dissolve left off
for (f in FADE_FRAMES until PHOTO_FRAMES + FADE_FRAMES) {
    coroutineContext.ensureActive()
    postFrame { canvas ->
        drawKenBurns(canvas, bitmap, 1f, f)
        drawBranding(canvas, logoBitmap, meta.overlayText)
    }
    drainEncoder(false)
    framesRendered++
    onProgress((framesRendered.toFloat() / totalFrames).coerceAtMost(1f))
}
```

For the **first photo** (no dissolve, `prev == null`), the loop still starts at `f = 0`
because the dissolve block is skipped — no change needed there. Only photos after the first
are affected by this fix, which is exactly what we want.

---

## Fix 2 — Pre-allocate Paint objects (secondary stutter / GC pressure)

**Problem:** `drawKenBurns` allocates `new Paint()` on every call. `drawBranding` allocates
3 `Paint` objects on every call. For a 4-photo reel (405 frames), this creates ~1,620 short-
lived Paint objects during the encode loop. On the A53's Exynos 1280, GC pauses during this
loop stall the render thread and create uneven frame delivery to the encoder.

Note: these are `android.graphics.Paint` for MediaCodec Canvas rendering, not Compose
theming — hardcoded colours are correct here; they're writing pixel data into the video frame.

**Fix:** Promote all four Paints to class-level properties. They're constructed once and
reused every frame. `kenBurnsPaint.alpha` is the only property that changes per call; set it
inline before each `drawBitmap`.

### 2a — Add four class-level Paint fields

Add these four properties inside the `AdventureReelEncoder` class body, alongside the existing
`companion object`:

```kotlin
// Pre-allocated Paints — never allocate inside the render loop (GC stall risk on A53).
private val kenBurnsPaint = Paint().apply { isFilterBitmap = true }
private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.argb(178, 7, 30, 38)   // macaco dark-teal at 70% opacity
}
private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    textSize = 22f
    textAlign = Paint.Align.CENTER
    typeface = android.graphics.Typeface.DEFAULT
}
private val logoPaint = Paint().apply { alpha = 38 }       // ~15% opacity
```

### 2b — Update `drawKenBurns` to reuse `kenBurnsPaint`

```kotlin
// BEFORE — inside drawKenBurns (line ~109)
val paint = Paint().apply {
    this.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
    isFilterBitmap = true
}
canvas.save()
canvas.translate(dx, dy)
canvas.scale(scale, scale)
canvas.drawBitmap(bitmap, 0f, 0f, paint)
canvas.restore()

// AFTER — mutate the pre-allocated paint; no allocation
kenBurnsPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
canvas.save()
canvas.translate(dx, dy)
canvas.scale(scale, scale)
canvas.drawBitmap(bitmap, 0f, 0f, kenBurnsPaint)
canvas.restore()
```

### 2c — Update `drawBranding` to reuse the three pre-allocated Paints

```kotlin
// BEFORE — inside drawBranding (line ~280)
private fun drawBranding(canvas: Canvas, logoBitmap: Bitmap?, overlayText: String?) {
    if (overlayText != null) {
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
            alpha = 38
        }
        val logoX = ((WIDTH - 48) / 2).toFloat()
        val logoY = (HEIGHT - 48 - 8).toFloat()
        canvas.drawBitmap(logoBitmap, logoX, logoY, logoPaint)
    }
}

// AFTER — remove all local Paint allocations; use class-level fields directly
private fun drawBranding(canvas: Canvas, logoBitmap: Bitmap?, overlayText: String?) {
    if (overlayText != null) {
        canvas.drawRoundRect(
            android.graphics.RectF(32f, 1152f, 688f, 1224f),
            24f, 24f,
            pillPaint
        )
        canvas.drawText(overlayText, 360f, 1196f, textPaint)
    }

    if (logoBitmap != null) {
        val logoX = ((WIDTH - 48) / 2).toFloat()
        val logoY = (HEIGHT - 48 - 8).toFloat()
        canvas.drawBitmap(logoBitmap, logoX, logoY, logoPaint)
    }
}
```

---

## Scope

- **In:** Ken Burns continuity across dissolve boundary; Paint pre-allocation.
- **Out:** Audio track, title card, blurred-background letterbox for landscape photos — all
  deferred to a future brief.
- **Out:** Resolution / bitrate changes — still 720×1280 @ 30 fps, 2 Mbps.
- **Out:** `totalFrames` recalculation — unchanged (dissolve + main = 105 frames/photo, same
  as before).

---

## Verification

1. Generate a reel with 3+ photos from different trips. Scrub through the video frame by frame
   at each transition — the pan/zoom should flow smoothly with no visible backward jump when
   the dissolve finishes and the main display begins.
2. The dissolve itself (0.5 s, 15 frames) should still feel silky — the cosine alpha from v2
   is unchanged.
3. No regression on single-photo reels (no dissolve, progress reaches 1.0).

---

## Summary

| # | Change | File |
|---|--------|------|
| 1a | Cap `t` at 1.0 in `drawKenBurns` | `AdventureReelEncoder.kt` |
| 1b | Main display loop starts at `FADE_FRAMES` not `0` | `AdventureReelEncoder.kt` |
| 2a | Four class-level Paint fields replacing per-frame allocations | `AdventureReelEncoder.kt` |
| 2b | `drawKenBurns` reuses `kenBurnsPaint` instead of allocating | `AdventureReelEncoder.kt` |
| 2c | `drawBranding` reuses `pillPaint`, `textPaint`, `logoPaint` | `AdventureReelEncoder.kt` |
