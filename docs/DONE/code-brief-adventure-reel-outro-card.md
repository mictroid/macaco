# Macaco — Adventure Reel: Branded Outro Card + Tracked Share Link

Two areas touched: `data/sync/AdventureReelEncoder.kt` (new branded end-card appended to every
rendered reel) and `util/AppActions.kt` + `ui/screens/JournalListScreen.kt` (tracked link added
to the share-sheet caption). Read `docs/DONE/code-brief-adventure-reel-v3.md` first — the
per-frame branding overlay, cross-dissolve, and Ken Burns continuity are already shipped; this
brief only appends a new segment at the end and touches the share intent, nothing mid-reel
changes.

---

## Change 1 — Branded outro card (logo + QR) appended to the reel

**Problem:** reels currently end right after the last photo's Ken Burns hold. There's no path
back to the app once a reel is reposted or screenshotted off-platform beyond the ~15%-opacity
watermark that's already burned into every frame — it's there, but too subtle for someone
scrolling past to act on.

**Fix:** append a 2-second branded end card after the last photo: dark-teal background, monkey
mark + "macaco" wordmark, and a white-carded QR code that resolves to a tracked Play Store
listing. It fades in over 0.5 s (reusing the existing cosine-ease dissolve math) then holds for
1.5 s — long enough to actually be scanned off a paused video, which a hard cut wouldn't allow.

```
┌──────────────────────────────┐
│                               │
│           🐒 (160px)          │
│                               │
│           macaco              │  ← gold wordmark
│      Your travels, saved      │  ← gold tagline, ~77% alpha
│                               │
│      ┌─────────────────┐      │
│      │                 │      │
│      │   [ QR code ]   │      │  ← white card, QR asset below
│      │                 │      │
│      └─────────────────┘      │
│                               │
│       Scan to get Macaco      │  ← white CTA text
│                               │
└──────────────────────────────┘
```

**Asset already in place** — no need to generate it: `app/src/main/res/drawable-nodpi/reel_qr_code.png`
(1848×1848 PNG, dark-teal-on-white, error-correction level H). It encodes:

```
https://play.google.com/store/apps/details?id=com.houseofmmminq.macaco&referrer=utm_source%3Dreel_share%26utm_medium%3Dvideo%26utm_campaign%3Dadventure_reel
```

The editable source lives at `design/macaco_adventure_reel_qr.png` — if the tracked URL ever
changes, that PNG needs regenerating (a Cowork task, Python `qrcode` lib), not something to
hand-edit in Android Studio.

### 1a — New frame-count constants

```kotlin
// BEFORE
companion object {
    private const val WIDTH  = 720
    private const val HEIGHT = 1280
    private const val FPS    = 30
    private const val BITRATE = 2_000_000       // 2 Mbps — good quality, ~15 MB/min
    private const val PHOTO_FRAMES  = 90        // 3 s per photo at 30 fps
    private const val FADE_FRAMES   = 15        // 0.5 s cross-dissolve
    private const val MIME = "video/avc"
}

// AFTER — two new constants for the outro
companion object {
    private const val WIDTH  = 720
    private const val HEIGHT = 1280
    private const val FPS    = 30
    private const val BITRATE = 2_000_000       // 2 Mbps — good quality, ~15 MB/min
    private const val PHOTO_FRAMES  = 90        // 3 s per photo at 30 fps
    private const val FADE_FRAMES   = 15        // 0.5 s cross-dissolve
    private const val OUTRO_FADE_FRAMES = 15    // 0.5 s fade from last photo into the outro card
    private const val OUTRO_HOLD_FRAMES = 45    // 1.5 s hold — long enough to actually scan the QR
    private const val MIME = "video/avc"
}
```

### 1b — New Paint fields + brand color constants

Add below the existing four Paint fields (`kenBurnsPaint` / `pillPaint` / `textPaint` /
`logoPaint`). These are `android.graphics.Paint` writing pixels into the video frame via
MediaCodec's Canvas, same as the existing ones in this file — hardcoded ARGB is correct here,
not a Compose theming violation (see the existing comment above `pillPaint`).

```kotlin
private val outroLayerPaint = Paint()   // alpha set per-frame in drawOutroCard for the fade-in
private val outroLogoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
private val outroTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = OUTRO_GOLD
    textSize = 56f
    textAlign = Paint.Align.CENTER
    typeface = android.graphics.Typeface.DEFAULT_BOLD
}
private val outroTaglinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.argb(196, 0xF0, 0xC8, 0x40)   // gold at ~77% opacity
    textSize = 24f
    textAlign = Paint.Align.CENTER
}
private val outroCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
private val outroCtaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    textSize = 26f
    textAlign = Paint.Align.CENTER
}

// Raw ARGB matching the app's existing brand tokens (SplashTealMid / SplashGoldBright in
// ui/screens/SplashScreen.kt) — can't reference a Compose Color from this render context, so
// these are re-declared as plain Ints. Keep in sync if the Splash palette ever changes.
private val OUTRO_BG_COLOR = android.graphics.Color.rgb(0x0A, 0x4A, 0x58)   // SplashTealMid
private val OUTRO_GOLD = android.graphics.Color.rgb(0xF0, 0xC8, 0x40)       // SplashGoldBright
```

`OUTRO_BG_COLOR`/`OUTRO_GOLD` call `Color.rgb()`, not compile-time constants — declare as
class-body `private val`, not inside `companion object`.

### 1c — Load the outro logo + QR bitmaps once, alongside the existing watermark logo

```kotlin
// BEFORE (inside encode(), just before the try block)
val logoBitmap = loadLogoBitmap(sizePx = 48)
try {

// AFTER
val logoBitmap = loadLogoBitmap(sizePx = 48)
val outroLogoBitmap = loadLogoBitmap(sizePx = 160)
val qrBitmap = loadQrBitmap(targetSizePx = 420)
try {
```

Update `finally` to recycle both new bitmaps:

```kotlin
// BEFORE
} finally {
    logoBitmap?.recycle()
    runCatching { encoder.stop() }
    encoder.release()
    if (muxerStarted) runCatching { muxer.stop() }
    muxer.release()
    inputSurface.release()
}

// AFTER
} finally {
    logoBitmap?.recycle()
    outroLogoBitmap?.recycle()
    qrBitmap?.recycle()
    runCatching { encoder.stop() }
    encoder.release()
    if (muxerStarted) runCatching { muxer.stop() }
    muxer.release()
    inputSurface.release()
}
```

### 1d — Append the outro render loop after the last photo

```kotlin
// BEFORE
                prevBitmap = bitmap
            }
            prevBitmap?.recycle()
            check(framesRendered > 0) {
                context.getString(R.string.reel_no_photos_error)
            }
            drainEncoder(true)

// AFTER
                prevBitmap = bitmap
            }

            // ── Branded outro card ───────────────────────────────────────────────────
            // Fades the last photo into a branded end-card so the reel carries attribution
            // even after it's reposted or screenshotted off-platform. Reuses the same cosine
            // ease as the photo-to-photo dissolve above.
            prevBitmap?.let { lastPhoto ->
                for (f in 0 until OUTRO_FADE_FRAMES) {
                    coroutineContext.ensureActive()
                    val alpha = (0.5f - 0.5f * cos(PI * f.toDouble() / OUTRO_FADE_FRAMES)).toFloat()
                    postFrame { canvas ->
                        drawKenBurns(canvas, lastPhoto, 1f, PHOTO_FRAMES - 1)
                        drawOutroCard(canvas, outroLogoBitmap, qrBitmap, alpha)
                    }
                    drainEncoder(false)
                    framesRendered++
                    onProgress((framesRendered.toFloat() / totalFrames).coerceAtMost(1f))
                }
                for (f in 0 until OUTRO_HOLD_FRAMES) {
                    coroutineContext.ensureActive()
                    postFrame { canvas -> drawOutroCard(canvas, outroLogoBitmap, qrBitmap, 1f) }
                    drainEncoder(false)
                    framesRendered++
                    onProgress((framesRendered.toFloat() / totalFrames).coerceAtMost(1f))
                }
            }

            prevBitmap?.recycle()
            check(framesRendered > 0) {
                context.getString(R.string.reel_no_photos_error)
            }
            drainEncoder(true)
```

Note: when there are zero photos, `prevBitmap` is null and the whole outro block is skipped via
`?.let` — the existing `check(framesRendered > 0)` still fires exactly as before.

### 1e — Update `totalFrames` to include the outro

```kotlin
// BEFORE
val totalFrames = (totalPhotos * PHOTO_FRAMES + (totalPhotos - 1) * FADE_FRAMES)
    .coerceAtLeast(1)

// AFTER
val totalFrames = (totalPhotos * PHOTO_FRAMES + (totalPhotos - 1) * FADE_FRAMES
    + OUTRO_FADE_FRAMES + OUTRO_HOLD_FRAMES).coerceAtLeast(1)
```

### 1f — New helper functions (add near `loadLogoBitmap`)

```kotlin
/**
 * Decodes the branded QR-code drawable (drawable-nodpi/reel_qr_code.png — the same tracked
 * Play Store link as AppActions.REEL_SHARE_URL) and scales it to [targetSizePx] for the outro
 * card. Source is 1848×1848; inSampleSize=4 avoids decoding full resolution just to downscale.
 */
private fun loadQrBitmap(targetSizePx: Int): Bitmap? = runCatching {
    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
    val raw = BitmapFactory.decodeResource(context.resources, R.drawable.reel_qr_code, opts)
        ?: return@runCatching null
    Bitmap.createScaledBitmap(raw, targetSizePx, targetSizePx, true).also {
        if (it !== raw) raw.recycle()
    }
}.getOrNull()

/**
 * Branded end-card: dark-teal background, macaco wordmark + monkey mark, and a white-carded
 * QR code. Composited via `saveLayer` so the whole card fades in as one unit over [alpha]
 * (0f..1f) rather than each element fading independently.
 */
private fun drawOutroCard(canvas: Canvas, outroLogo: Bitmap?, qr: Bitmap?, alpha: Float) {
    outroLayerPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
    val saveCount = canvas.saveLayer(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), outroLayerPaint)

    canvas.drawColor(OUTRO_BG_COLOR)

    outroLogo?.let { logo ->
        canvas.drawBitmap(logo, (WIDTH - logo.width) / 2f, 260f, outroLogoPaint)
    }
    canvas.drawText("macaco", WIDTH / 2f, 470f, outroTitlePaint)
    canvas.drawText(context.getString(R.string.reel_outro_tagline), WIDTH / 2f, 508f, outroTaglinePaint)

    qr?.let { qrBitmap ->
        val cardPad = 28f
        val cardSize = qrBitmap.width + cardPad * 2
        val cardLeft = (WIDTH - cardSize) / 2f
        val cardTop = 580f
        canvas.drawRoundRect(
            android.graphics.RectF(cardLeft, cardTop, cardLeft + cardSize, cardTop + cardSize),
            32f, 32f, outroCardPaint
        )
        canvas.drawBitmap(qrBitmap, cardLeft + cardPad, cardTop + cardPad, null)
        canvas.drawText(
            context.getString(R.string.reel_outro_cta),
            WIDTH / 2f, cardTop + cardSize + 56f, outroCtaPaint
        )
    }

    canvas.restoreToCount(saveCount)
}
```

**File:** `data/sync/AdventureReelEncoder.kt`.

---

## Change 2 — Tracked link in the share-sheet caption ("direct link" alongside the QR)

**Problem:** right now the only path back to the app is the outro QR, which only helps if
someone has the video paused and a second device to scan with. Apps that support text alongside
media (WhatsApp, Telegram, Messages, X/Twitter, Gmail) can carry a tappable link instead — no
scanning needed.

**Fix:** attach the same tracked URL as `EXTRA_TEXT` on the existing reel share intent.
Instagram/TikTok Stories ignore `EXTRA_TEXT` on `ACTION_SEND` by design (their sticker/link
system needs a separate approved SDK integration) — those shares still rely on the QR and
per-frame watermark instead. This is a small, safe addition, not a full solution for every
platform.

### 2a — Add the tracked URL constant to `AppActions.kt`

```kotlin
// BEFORE
object AppActions {
    // Matches the Play Console listing (build.gradle applicationId).
    private const val PACKAGE = "com.houseofmmminq.macaco"
    private const val LISTING_URL = "https://play.google.com/store/apps/details?id=$PACKAGE"

// AFTER
object AppActions {
    // Matches the Play Console listing (build.gradle applicationId).
    private const val PACKAGE = "com.houseofmmminq.macaco"
    private const val LISTING_URL = "https://play.google.com/store/apps/details?id=$PACKAGE"

    // Play's own referrer parameter (not a webpage UTM) — Firebase Analytics' automatic Install
    // Referrer collection reads this at first_open and attributes the install to
    // reel_share / video / adventure_reel in the Acquisition report, no extra SDK needed.
    // Keep in sync with the QR encoded in res/drawable-nodpi/reel_qr_code.png — if this URL ever
    // changes, regenerate that PNG from design/macaco_adventure_reel_qr.png's source URL too.
    const val REEL_SHARE_URL = "$LISTING_URL&referrer=" +
        "utm_source%3Dreel_share%26utm_medium%3Dvideo%26utm_campaign%3Dadventure_reel"
```

### 2b — Add the link to the share intent in `JournalListScreen.kt`

```kotlin
// BEFORE
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, state.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, context.getString(R.string.reel_share_chooser))
                )

// AFTER
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, state.uri)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        context.getString(R.string.reel_share_caption, AppActions.REEL_SHARE_URL)
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, context.getString(R.string.reel_share_chooser))
                )
```

Add the import (not currently present in this file): `import com.houseofmmminq.macaco.util.AppActions`

**Files:** `util/AppActions.kt`, `ui/screens/JournalListScreen.kt`.

---

## Localization

Three new string keys — all 11 languages need translations (`values/` default plus
`values-de/es/fr/it/ja/nl/pl/pt/sv/zh-rCN/`), not just English:

| Key | EN value |
|-----|----------|
| `reel_outro_tagline` | Your travels, saved |
| `reel_outro_cta` | Scan to get Macaco |
| `reel_share_caption` | Made with 🐒 Macaco — get the app: %1$s |

---

## Scope

- **In:** appended branded outro card (logo, wordmark, tagline, QR, CTA) on every rendered reel;
  tracked link added to the share-sheet caption text.
- **Out:** embedding the monkey logo inside the QR modules themselves — kept as a separate
  element above the code instead, safer for scan reliability, no need to juggle error-correction
  budget against a center logo.
- **Out:** per-platform deep linking (opening the app directly for users who already have Macaco
  installed, instead of the Play listing) — needs Android App Links + Digital Asset Links,
  worth a separate brief if reel-driven installs turn out to matter.
- **Out:** regenerating the QR PNG if the tracked URL changes — that's a Cowork/Python task, not
  Code's; the asset is already in place at `res/drawable-nodpi/reel_qr_code.png`.

---

## Verification

1. Generate a reel from a trip with 2+ photos. Confirm it now ends with a ~2 s branded card:
   clean fade-in (no double-exposure glitch against the frozen last photo), holds long enough to
   scan the QR with a second phone, and the QR resolves to the Play Store listing.
2. Confirm existing mid-reel behavior (Ken Burns, dissolves, per-frame watermark/location pill)
   is unchanged — this only appends to the end, nothing upstream should differ.
3. Trigger a share from a completed reel. Confirm the caption on a text-capable target (Messages,
   Gmail) now includes the tracked link, and the video still attaches correctly on WhatsApp as
   before.
4. Confirm a zero-photo trip still surfaces `reel_no_photos_error` via the existing `check()` —
   the outro block should no-op cleanly (`prevBitmap` is null) rather than throwing.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1a | New `OUTRO_FADE_FRAMES` / `OUTRO_HOLD_FRAMES` constants | `AdventureReelEncoder.kt` |
| 1b | New outro Paint fields + brand color constants | `AdventureReelEncoder.kt` |
| 1c | Load outro logo + QR bitmaps once; recycle in `finally` | `AdventureReelEncoder.kt` |
| 1d | Append outro fade-in + hold render loop after last photo | `AdventureReelEncoder.kt` |
| 1e | `totalFrames` includes outro frame count | `AdventureReelEncoder.kt` |
| 1f | `loadQrBitmap()` + `drawOutroCard()` helpers | `AdventureReelEncoder.kt` |
| 2a | `REEL_SHARE_URL` tracked-link constant | `AppActions.kt` |
| 2b | Tracked link added to share-intent `EXTRA_TEXT` | `JournalListScreen.kt` |
| — | QR asset already placed (no code change needed) | `res/drawable-nodpi/reel_qr_code.png` |
| — | 3 new string keys × 11 languages | `strings.xml` × 11 |
