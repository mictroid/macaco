# Macaco — Adventure Reel: Fix stale REEL_SHARE_URL (shipped the wrong version of v2's Fix 3)

Single-constant fix in `util/AppActions.kt`. vc65 shipped `code-brief-adventure-reel-outro-card-v2.md`,
but Fix 3 landed as an **earlier draft** of that brief's reasoning, not the final corrected
version — a race between an in-place brief edit and Code picking it up. Confirmed live in the
current source: `REEL_SHARE_URL` is still the full tracked Play Store URL, which is exactly the
six-line percent-encoded wall of text a real WhatsApp screenshot showed rendering badly in the
share caption. That screenshot is *why* v2 was corrected in the first place — this brief just
re-applies that correction, which never made it into the shipped build.

---

## Fix — `REEL_SHARE_URL` should be the short redirect, not the full tracked URL

**Problem (confirmed in current source, `util/AppActions.kt` lines 23–31):**

```kotlin
// CURRENT — this is the stale, pre-correction version
    // Play's own referrer parameter (not a webpage UTM) — Firebase Analytics' automatic Install
    // Referrer collection reads this at first_open and attributes the install to
    // reel_share / video / adventure_reel in the Acquisition report, no extra SDK needed.
    // Deliberately the FULL url, not the short QR redirect (r/index.html / reel_qr_code.png) —
    // this is human-visible text in the share sheet, and a recognizable play.google.com domain
    // is worth more here than the character savings that matter for QR pixel density. Don't
    // "simplify" this to match the QR's short link.
    const val REEL_SHARE_URL = "$LISTING_URL&referrer=" +
        "utm_source%3Dreel_share%26utm_medium%3Dvideo%26utm_campaign%3Dadventure_reel"
```

That comment describes the reasoning from *before* the WhatsApp screenshot — a real share showed
this exact URL wrapping into six lines of `%3D`/`%26` noise in the caption bubble, which reads as
spammier than a short link regardless of the domain buried inside it. The corrected version of
`code-brief-adventure-reel-outro-card-v2.md` reversed this to use the same short redirect as the
QR; that corrected version is what should have shipped.

**Fix:**

```kotlin
// AFTER
    // Short redirect (r/index.html, served via the same GitHub Pages pipeline as
    // privacy-policy.html) that forwards to the full Play Store URL with the referrer-tagged
    // UTM. Used for BOTH the share-caption text and the QR (res/drawable-nodpi/reel_qr_code.png,
    // design/macaco_adventure_reel_qr.png) — the full URL with its URL-encoded referrer string
    // renders as an unreadable wall of text in a chat bubble, which reads as spammier than a
    // short link regardless of domain. The actual attribution logic (utm_source/medium/campaign)
    // lives in r/index.html, not here — edit that file if the tracked destination ever changes.
    const val REEL_SHARE_URL = "https://mictroid.github.io/macaco/r/"
```

`LISTING_URL` stays as-is (still used elsewhere — `shareApp()`, `openPlayStoreListing()`). Only
`REEL_SHARE_URL`'s value and its comment change; no other file touches this constant besides the
reel share intent in `JournalListScreen.kt`, which needs no change itself (it just reads
`AppActions.REEL_SHARE_URL`).

**File:** `util/AppActions.kt`.

---

## Scope

- **In:** one constant value + its comment.
- **Out:** everything else from v2 (outro repositioning, pill-glyph branding) — already shipped
  correctly in vc65, confirmed against current source, not touched by this brief.

---

## Verification

1. Trigger a reel share to WhatsApp (or Messages). Confirm the caption now shows
   `https://mictroid.github.io/macaco/r/` on one line, not the six-line encoded URL.
2. Tap the link from the share target; confirm it opens the redirect page and lands on the
   Macaco Play Store listing.
3. Confirm the QR on the outro card is unaffected (it already encoded the short link — this
   brief only touches the caption's constant, not the QR asset).

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `REEL_SHARE_URL` value + comment corrected to the short redirect | `AppActions.kt` |
