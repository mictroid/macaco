# Macaco — Adventure Reel: Outro Card Fixes (center collision, mid-reel branding, short link)

Follow-up to `docs/DONE/code-brief-adventure-reel-outro-card.md` (shipped vc64, confirmed
on-device by the user). Three fixes based on reviewing the actual shipped output: the outro
QR sits where video-player pause icons render, mid-reel frames carry no visible branding on
busy/light photos, and the tracked URL is long enough to make the QR unnecessarily dense.
One file changed for the encoder (`data/sync/AdventureReelEncoder.kt`), one constant changed in
`util/AppActions.kt`, one string value changed. All snippets below are copied from the current
shipped source (post the vc64 init-order fix), not the original brief draft.

**Correction from an earlier draft of this brief, based on a real WhatsApp screenshot:** an
earlier pass argued for keeping the caption link as the full `play.google.com` URL (domain trust
outweighs length). Seeing the actual shipped output disproves that — the full URL with its
URL-encoded referrer string (`...&referrer=utm_source%3Dreel_share%26utm_medium%3D...`) wraps
across six lines in a WhatsApp bubble. A six-line wall of percent-encoded text reads as more
spammy/phishing-like than a short link does, regardless of domain — it doesn't matter that
`play.google.com` is in there if it's buried in noise. Fix 3 below reverses course: the
share-caption link now uses the **same short redirect as the QR**, not the full tracked URL.

**QR and caption now use the identical short link — one URL, not two.** Both are baked from the
same `https://mictroid.github.io/macaco/r/` (36 chars, one line, the word "macaco" visible in the
path right after "Made with 🐒 Macaco" in the caption text for context). All the actual UTM/
referrer attribution logic stays server-side in `r/index.html`, invisible to whoever reads the
caption or scans the QR — it only becomes the long Play Store URL after the redirect fires.

Two supporting assets are **already done, no code-side action needed**:
- `res/drawable-nodpi/reel_qr_code.png` and `design/macaco_adventure_reel_qr.png` have been
  **regenerated** to encode a short redirect URL (`https://mictroid.github.io/macaco/r/`, 36
  chars) instead of the old ~140-char tracked Play link. Old QR was version 13 (69×69 modules);
  new one is version 5 (37×37 modules) — roughly 3.5× bigger physical squares at the same print
  size, meaningfully more resistant to platform re-compression and phone-screen scanning. This
  short URL is QR-only — it is not used anywhere else, not referenced by any Kotlin constant.
- `r/index.html` has been added to the repo root — a static redirect page (meta-refresh + JS
  `location.replace`) that sends `https://mictroid.github.io/macaco/r/` to the full tracked Play
  Store URL. It deploys via the same push-to-master GitHub Pages pipeline already serving
  `/privacy-policy.html` and `/terms-of-service.html` — no new hosting to provision.

---

## Fix 1 — Outro QR/logo collide with the video player's own pause icon

**Problem:** confirmed from a real on-device screenshot — most video players (Photos, Gallery,
WhatsApp, Instagram) center their pause/play control at the frame's true vertical middle
(y≈640 of the 1280-tall frame). The current outro card's QR card starts at `cardTop = 580f`,
close enough to that midpoint that a paused player's control icon lands directly on the QR's
upper third, right where the position-detection squares and dense data modules are.

**Fix:** compress the logo/wordmark/tagline into the upper band and push the QR further down,
leaving a clear ~380px gap (roughly y=416 to y=800) straddling the true center where a player's
pause icon renders. Also shrink the QR itself slightly (420px → 360px target) so the card fits
comfortably in the lower band without crowding the CTA text off the bottom of the frame.

```
┌──────────────────────────────┐  y=0
│                               │
│           🐒 (140px)          │  y=180–320
│           macaco              │  y=380
│      Every trip, remembered   │  y=416
│                               │
│   ← center-collision zone →   │  y≈416–800, deliberately empty —
│                               │    this is where paused-player
│                               │    controls typically render
│      ┌─────────────────┐      │
│      │   [ QR, 360px ] │      │  y=800–1208
│      └─────────────────┘      │
│       Scan to get Macaco      │  y=1258
│                               │  y=1280
└──────────────────────────────┘
```

```kotlin
// BEFORE — drawOutroCard (current shipped version)
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

// AFTER — logo/title/tagline compressed upward; QR pushed below the center-collision band
private fun drawOutroCard(canvas: Canvas, outroLogo: Bitmap?, qr: Bitmap?, alpha: Float) {
    outroLayerPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
    val saveCount = canvas.saveLayer(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), outroLayerPaint)

    canvas.drawColor(OUTRO_BG_COLOR)

    outroLogo?.let { logo ->
        canvas.drawBitmap(logo, (WIDTH - logo.width) / 2f, 180f, outroLogoPaint)
    }
    canvas.drawText("macaco", WIDTH / 2f, 380f, outroTitlePaint)
    canvas.drawText(context.getString(R.string.reel_outro_tagline), WIDTH / 2f, 416f, outroTaglinePaint)

    // Deliberately nothing drawn between here and cardTop below — that gap straddles the frame's
    // true vertical center (y=640), where paused video players render their own play/pause icon.
    qr?.let { qrBitmap ->
        val cardPad = 24f
        val cardSize = qrBitmap.width + cardPad * 2
        val cardLeft = (WIDTH - cardSize) / 2f
        val cardTop = 800f
        canvas.drawRoundRect(
            android.graphics.RectF(cardLeft, cardTop, cardLeft + cardSize, cardTop + cardSize),
            32f, 32f, outroCardPaint
        )
        canvas.drawBitmap(qrBitmap, cardLeft + cardPad, cardTop + cardPad, null)
        canvas.drawText(
            context.getString(R.string.reel_outro_cta),
            WIDTH / 2f, cardTop + cardSize + 50f, outroCtaPaint
        )
    }

    canvas.restoreToCount(saveCount)
}
```

Also shrink the loaded QR target size to match (360 instead of 420 — the smaller card is what
makes room for the lower band without crowding the CTA off the bottom edge):

```kotlin
// BEFORE (inside encode(), before the try block)
val qrBitmap = loadQrBitmap(targetSizePx = 420)

// AFTER
val qrBitmap = loadQrBitmap(targetSizePx = 360)
```

Also bump the outro logo down slightly from 160px to 140px so it fits the tighter upper band —
update the load call:

```kotlin
// BEFORE
val outroLogoBitmap = loadLogoBitmap(sizePx = 160)

// AFTER
val outroLogoBitmap = loadLogoBitmap(sizePx = 140)
```

**File:** `data/sync/AdventureReelEncoder.kt`.

---

## Fix 2 — No visible branding on mid-reel frames

**Problem:** the existing per-frame watermark (`logoPaint`, ~15% opacity, 48px, bottom-center)
floats directly over the photo. Against busy or light-colored photos (e.g. a museum info board)
it's essentially invisible, confirmed from a real on-device screenshot. It's the only branding
mid-reel besides the location pill, which currently carries no logo at all.

**Fix:** draw a small (28px), fully-opaque monkey glyph inside the location pill itself. The
pill's own dark, semi-opaque background guarantees contrast regardless of what's in the photo
behind it — unlike the floating watermark, which depends on the photo being dark enough to show
a 15%-opacity mark. Keep the existing floating watermark as-is — it's still the only branding on
frames where `overlayText` is null (repeat-location photos skip the pill), so it shouldn't be
removed, just reinforced.

### 2a — Load a third, pill-sized logo bitmap

```kotlin
// BEFORE (inside encode(), before the try block)
val logoBitmap = loadLogoBitmap(sizePx = 48)
val outroLogoBitmap = loadLogoBitmap(sizePx = 140)   // (140 after Fix 1 above)
val qrBitmap = loadQrBitmap(targetSizePx = 360)       // (360 after Fix 1 above)

// AFTER
val logoBitmap = loadLogoBitmap(sizePx = 48)
val pillLogoBitmap = loadLogoBitmap(sizePx = 28)
val outroLogoBitmap = loadLogoBitmap(sizePx = 140)
val qrBitmap = loadQrBitmap(targetSizePx = 360)
```

Recycle it alongside the others:

```kotlin
// BEFORE
} finally {
    logoBitmap?.recycle()
    outroLogoBitmap?.recycle()
    qrBitmap?.recycle()
    ...

// AFTER
} finally {
    logoBitmap?.recycle()
    pillLogoBitmap?.recycle()
    outroLogoBitmap?.recycle()
    qrBitmap?.recycle()
    ...
```

### 2b — New Paint field for the pill glyph (fully opaque — the pill guarantees contrast)

```kotlin
// AFTER — add alongside the existing four Paint fields
private val pillLogoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
```

### 2c — Draw the glyph inside the pill, pass it through `drawBranding`

```kotlin
// BEFORE
private fun drawBranding(canvas: Canvas, logoBitmap: Bitmap?, overlayText: String?) {
    // ── Location pill ──────────────────────────────────────────────────────
    if (overlayText != null) {
        canvas.drawRoundRect(
            android.graphics.RectF(32f, 1152f, 688f, 1224f),
            24f, 24f,
            pillPaint
        )
        canvas.drawText(overlayText, 360f, 1196f, textPaint)
    }

    // ── Logo watermark (bottom-centre) ─────────────────────────────────────
    if (logoBitmap != null) {
        val logoX = ((WIDTH - 48) / 2).toFloat()
        val logoY = (HEIGHT - 48 - 8).toFloat()
        canvas.drawBitmap(logoBitmap, logoX, logoY, logoPaint)
    }
}

// AFTER — new pillLogoBitmap param, drawn left-inset inside the pill before the text
private fun drawBranding(
    canvas: Canvas,
    logoBitmap: Bitmap?,
    pillLogoBitmap: Bitmap?,
    overlayText: String?
) {
    // ── Location pill ──────────────────────────────────────────────────────
    if (overlayText != null) {
        canvas.drawRoundRect(
            android.graphics.RectF(32f, 1152f, 688f, 1224f),
            24f, 24f,
            pillPaint
        )
        // Small opaque glyph inside the pill — guaranteed contrast against pillPaint's dark
        // background regardless of the photo behind it. Left-inset, vertically centred in the
        // 72px-tall pill: (72 - 28) / 2 = 22.
        pillLogoBitmap?.let { glyph ->
            canvas.drawBitmap(glyph, 48f, 1174f, pillLogoPaint)
        }
        canvas.drawText(overlayText, 360f, 1196f, textPaint)
    }

    // ── Logo watermark (bottom-centre) ─────────────────────────────────────
    if (logoBitmap != null) {
        val logoX = ((WIDTH - 48) / 2).toFloat()
        val logoY = (HEIGHT - 48 - 8).toFloat()
        canvas.drawBitmap(logoBitmap, logoX, logoY, logoPaint)
    }
}
```

Update both call sites to pass the new parameter (dissolve loop and main display loop):

```kotlin
// BEFORE (appears twice — once in the cross-dissolve loop, once in the main display loop)
drawBranding(canvas, logoBitmap, meta.overlayText)

// AFTER (both call sites)
drawBranding(canvas, logoBitmap, pillLogoBitmap, meta.overlayText)
```

**Scope note:** the glyph sits at a fixed x=48–76px slice on the pill's left edge; centered
location text only risks visually crowding it for unusually long strings (pill is 656px wide,
typical "City, Country · Mon YYYY" strings run well under half that). Not worth dynamic layout
for this edge case — flag it if it comes up in review, don't pre-solve it.

**File:** `data/sync/AdventureReelEncoder.kt`.

---

## Fix 3 — Point the share-caption link at the same short redirect as the QR

**Problem:** confirmed from a real WhatsApp screenshot — `REEL_SHARE_URL` (the full Play Store
URL plus the URL-encoded referrer string) renders as a six-line wall of percent-encoded text in
the share caption. That reads as spam/phishing-adjacent regardless of the domain buried inside
it; the "play.google.com is trustworthy" argument from the previous draft of this brief doesn't
survive contact with what it actually looks like in a chat bubble.

**Fix:** `REEL_SHARE_URL` becomes the same short redirect the QR already encodes. One line,
36 characters, and it still visibly contains "macaco" in the path right after "Made with 🐒
Macaco" in the caption copy — enough brand context without the URL-encoded noise.

```kotlin
// BEFORE
object AppActions {
    private const val PACKAGE = "com.houseofmmminq.macaco"
    private const val LISTING_URL = "https://play.google.com/store/apps/details?id=$PACKAGE"

    // Play's own referrer parameter (not a webpage UTM) — Firebase Analytics' automatic Install
    // Referrer collection reads this at first_open and attributes the install to
    // reel_share / video / adventure_reel in the Acquisition report, no extra SDK needed.
    // Keep in sync with the QR encoded in res/drawable-nodpi/reel_qr_code.png — if this URL ever
    // changes, regenerate that PNG from design/macaco_adventure_reel_qr.png's source URL too.
    const val REEL_SHARE_URL = "$LISTING_URL&referrer=" +
        "utm_source%3Dreel_share%26utm_medium%3Dvideo%26utm_campaign%3Dadventure_reel"

// AFTER
object AppActions {
    private const val PACKAGE = "com.houseofmmminq.macaco"
    private const val LISTING_URL = "https://play.google.com/store/apps/details?id=$PACKAGE"

    // Short redirect (r/index.html, served via the same GitHub Pages pipeline as
    // privacy-policy.html) that forwards to the full Play Store URL with the referrer-tagged
    // UTM. Used for BOTH the share-caption text and the QR (res/drawable-nodpi/reel_qr_code.png,
    // design/macaco_adventure_reel_qr.png) — the full URL with its URL-encoded referrer string
    // renders as an unreadable wall of text in a chat bubble, which reads as spammier than a
    // short link regardless of domain. The actual attribution logic (utm_source/medium/campaign)
    // lives in r/index.html, not here — edit that file if the tracked destination ever changes.
    const val REEL_SHARE_URL = "https://mictroid.github.io/macaco/r/"
```

**File:** `util/AppActions.kt`.

---

## Localization — tagline copy change only

One string value changed (key already exists from the previous brief, all 11 languages already
have a translation for it — this just supersedes the English source value; re-translate the
other 10):

| Key | Old EN value | New EN value |
|-----|---------------|--------------|
| `reel_outro_tagline` | Your travels, saved | Every trip, remembered |

---

## Scope

- **In:** outro layout repositioned to clear the center-collision zone; QR shrunk slightly to
  fit the new layout; pill-glyph branding added for mid-reel visibility; QR and share-caption
  link both now point at the same short GitHub Pages redirect instead of the full tracked URL;
  tagline copy updated.
- **Out:** dynamic pill layout to avoid the glyph ever crowding long location strings (see Fix 2
  scope note) — revisit only if it actually comes up.
- **Out:** further shortening the redirect URL with a custom domain — the GitHub Pages path is
  already a ~5x reduction from the original; a custom domain would shave a few more characters
  for meaningfully more setup cost (domain purchase + DNS), not worth it at this size.

---

## Verification

1. Generate a reel, let it play to the outro, then pause it in at least two different apps
   (e.g. Android's default Gallery/Photos viewer and WhatsApp's inline player) — confirm the
   pause icon in both no longer overlaps the QR or the wordmark.
2. Confirm the QR still resolves correctly: scan it with a second phone, confirm it lands on
   `mictroid.github.io/mac