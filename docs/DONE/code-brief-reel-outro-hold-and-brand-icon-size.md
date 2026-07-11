# Macaco — Adventure Reel outro hold + brand icon size consistency

Two unrelated fixes bundled together because they came out of the same design review: (1) the
Adventure Reel's QR outro card doesn't hold long enough to actually be scanned, and (2) the
`macaco` header icon renders too small in every landscape header (Journal, Adventures/Map,
Profile) because they all share one component and one constant. Files touched:
`data/sync/AdventureReelEncoder.kt`, `ui/components/MacacoBrandBlock.kt`,
`ui/screens/EntryDetailScreen.kt`.

---

## 1. Reel outro card doesn't hold long enough to scan the QR

**Problem:** `AdventureReelEncoder` fades into the branded end-card (macaco mark + QR) and holds
it for `OUTRO_HOLD_FRAMES = 45` frames — 1.5 s at 30 fps. In practice (sharing to WhatsApp
Status, Instagram, etc.) that's not enough time to raise a phone's camera and get a scan lock
before the clip ends or loops back to the first photo. The card needs to stay on screen
noticeably longer — it's the only chance a viewer has to find their way back to the app.

**Fix:** Increase `OUTRO_HOLD_FRAMES` from 45 (1.5 s) to 120 (4 s) — roughly the same dwell time
already given to a single photo (`PHOTO_FRAMES = 90`, 3 s), which is the app's existing baseline
for "long enough to look at something in this reel." This adds ~2.5 s to total reel length,
which is proportionate for a slideshow that already spends 3 s per photo.

**File:** `data/sync/AdventureReelEncoder.kt` (companion object, ~line 46)

```kotlin
// BEFORE
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

```kotlin
// AFTER
    companion object {
        private const val WIDTH  = 720
        private const val HEIGHT = 1280
        private const val FPS    = 30
        private const val BITRATE = 2_000_000       // 2 Mbps — good quality, ~15 MB/min
        private const val PHOTO_FRAMES  = 90        // 3 s per photo at 30 fps
        private const val FADE_FRAMES   = 15        // 0.5 s cross-dissolve
        private const val OUTRO_FADE_FRAMES = 15    // 0.5 s fade from last photo into the outro card
        // 4 s hold — matches PHOTO_FRAMES' 3 s-per-photo dwell plus a margin, since scanning a QR
        // (raise camera, focus, lock) takes longer than just looking at a photo. Previously 45
        // frames / 1.5 s, which real shares showed wasn't enough time before the clip ended or
        // looped back to the first photo, making the app hard to find again.
        private const val OUTRO_HOLD_FRAMES = 120   // 4 s hold
        private const val MIME = "video/avc"
    }
```

No other change needed — `OUTRO_HOLD_FRAMES` is the only place this value is read (the hold loop
at ~line 283 just iterates `for (f in 0 until OUTRO_HOLD_FRAMES)`).

**Out of scope:** anything about how WhatsApp/Instagram loop or replay the exported MP4 — that's
controlled by the host app, not Macaco. This fix maximizes the fraction of each loop spent on the
QR; it can't force a hard stop on someone else's player.

**Verification:** re-export a reel, confirm total added length is ~2.5 s over the previous
build, and do a real scan test (phone camera, from a fresh share to WhatsApp) to confirm 4 s is
actually enough — adjust the constant again if not.

---

## 2. Landscape header icon is too small, and inconsistent risk across screens

**Problem:** Journal, Adventures (Map), and Profile all render their landscape header through
the shared `MacacoBrandBlock` composable — but its `collapsed` branch (the icon-only state,
which is what renders in landscape once the header shrinks) hardcodes the icon to **28.dp** for
landscape, noticeably smaller than the 48.dp used everywhere in portrait. Because all three
screens read the same constant, they're already guaranteed to match each other in code — the
size just needs to go up. Separately, `EntryDetailScreen.kt` has its own **duplicate**, non-shared
header that also uses a hardcoded 28.dp icon — since it doesn't go through `MacacoBrandBlock`,
it needs its own matching edit or it'll be the odd one out once the shared constant changes.

**Fix:** Bump the landscape `collapsedIconSize` in `MacacoBrandBlock` from 28.dp to 36.dp, and
bump `EntryDetailScreen`'s own header icon to match (also 36.dp), so every screen's compact
header icon is identical in size again.

**File:** `ui/components/MacacoBrandBlock.kt` (~line 47)

```kotlin
// BEFORE
        collapsed -> {
            // Landscape is already height-constrained (that's why isLandscape triggers below
            // 480dp), so its collapsed state needs to actually shrink, not just drop the
            // wordmark while keeping the full portrait-sized icon. 28dp matches the compact
            // icon size EntryDetailScreen's own (non-collapsing) header already uses. Portrait
            // keeps the original 48dp/8dp — matches Journal's reference header, unchanged.
            val collapsedIconSize = if (isLandscape) 28.dp else MacacoBrandIconSize
            val collapsedBottomPadding = if (isLandscape) 4.dp else 8.dp
```

```kotlin
// AFTER
        collapsed -> {
            // Landscape is already height-constrained (that's why isLandscape triggers below
            // 480dp), so its collapsed state needs to actually shrink, not just drop the
            // wordmark while keeping the full portrait-sized icon. 36dp — up from 28dp, which
            // read as too small next to the 48dp portrait icon — matches EntryDetailScreen's own
            // (non-collapsing) header, bumped to the same size below. Portrait keeps the
            // original 48dp/8dp — matches Journal's reference header, unchanged.
            val collapsedIconSize = if (isLandscape) 36.dp else MacacoBrandIconSize
            val collapsedBottomPadding = if (isLandscape) 4.dp else 8.dp
```

**File:** `ui/screens/EntryDetailScreen.kt` (~line 336, inside the top bar's centred brand Column)

```kotlin
// BEFORE
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
```

```kotlin
// AFTER
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            // 36dp — matches MacacoBrandBlock's landscape-collapsed icon size so
                            // every screen's compact header icon is the same size again.
                            modifier = Modifier.size(36.dp)
                        )
```

**Out of scope:**
- Deduplicating `EntryDetailScreen`'s hand-rolled header into `MacacoBrandBlock(collapsed = true)`
  itself — that's a real cleanup (it's the only header not using the shared component, which is
  exactly why it drifted to a mismatched size once already) but it's a bigger, riskier change than
  a size bump and isn't needed to fix the immediate visual bug. Worth a follow-up brief.
- The icon looking slightly off-centre within its own bounding box: `ic_launcher_foreground` is an
  adaptive-icon *foreground layer* (108dp canvas, only the inner ~66dp "safe zone" is guaranteed
  visible after masking), so a lot of its box is transparent padding, and that padding isn't
  perfectly symmetric top-to-bottom. Sizing it up helps, but doesn't fully fix that — a proper fix
  would need a dedicated, tightly-cropped brand-mark asset with no built-in mask padding. Not
  attempted here; flagging for a separate design pass if the icon still reads as off-centre after
  this change.
- The soft teal glow/halo visible behind the icon in every screenshot — this sits at the same
  absolute position regardless of which screen is open and doesn't move with the icon's actual
  layout bounds, which points to it being the phone's own camera-cutout blending treatment
  (common on punch-hole displays), not something Macaco draws. Not a code fix.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `OUTRO_HOLD_FRAMES` 45 → 120 (1.5 s → 4 s) so the QR card holds long enough to scan | `data/sync/AdventureReelEncoder.kt` |
| 2 | Landscape `collapsedIconSize` 28.dp → 36.dp | `ui/components/MacacoBrandBlock.kt` |
| 3 | Header icon 28.dp → 36.dp, matching #2 | `ui/screens/EntryDetailScreen.kt` |
