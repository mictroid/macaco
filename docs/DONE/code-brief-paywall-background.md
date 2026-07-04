# Macaco — Paywall: Own the Full-Screen Dark Background (light-mode white gap)

Screenshot-confirmed on device (light theme, free-limit paywall): the teal radial covers only
the top 300dp; everything below falls through to the THEME background. In light mode that's a
white lower half — the white feature bullets ("Attach photos…", "Sync across…", "Zero ads…")
and the "No hidden fees" footer become invisible, and the dark plan cards float on white.
The screen's copy and cards are all designed for dark; it must paint its own background
instead of inheriting the theme's. Touches `PurchaseScreen.kt` only.

```
Current (light mode):                     After:

┌─────────────────────────┐               ┌─────────────────────────┐
│  radial teal (300dp)    │               │  radial teal (300dp)    │
│  monkey / wordmark      │               │  monkey / wordmark      │
├─────────────────────────┤               │  …blends into…          │
│  WHITE (theme bg)       │               │  solid SplashTealEdge   │
│  invisible white text   │               │  white text readable    │
│  dark cards on white    │               │  dark cards on dark     │
└─────────────────────────┘               └─────────────────────────┘
```

---

## Change 1 — Solid brand-dark base under the radial header

**Fix:** Give the root Box a solid `SplashTealEdge` background — that is the radial gradient's
edge colour, so the 300dp header dissolves seamlessly into it with no visible seam. This
screen is a brand moment (per the chrome policy in `docs/DONE/code-brief-theme-chrome-policy.md`),
so a fixed brand colour — not a theme token — is correct here.

```kotlin
// BEFORE (PurchaseScreen.kt, ~line 99)
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(macacoBrandBackground())
        )

// AFTER
    // The paywall is designed dark (white copy, dark plan cards) — paint the whole screen in
    // the brand dark instead of inheriting the theme background, which is white in light mode
    // and made the lower half unreadable. SplashTealEdge is the radial's edge colour, so the
    // 300dp glow header below blends into it seamlessly.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashTealEdge)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(macacoBrandBackground())
        )
```

(`SplashTealEdge` is `internal` in `SplashScreen.kt`, same package — already accessible; it's
used by AppLockScreen the same way.)

**File:** `PurchaseScreen.kt`

---

## Change 2 — Sweep the screen for theme-dependent colours that assumed dark

With the background now always dark, verify the few `MaterialTheme.colorScheme.*` usages on
this screen still read correctly in BOTH light and dark theme settings (the theme still drives
them even though the backdrop is fixed):

- The error banner (`errorContainer`/`onErrorContainer`, ~line 224) — container colours are
  fine on dark in both modes; leave as-is.
- Plan cards, CTA, footer — already explicit dark-design colours; no change.
- If anything else uses `onSurface`/`onBackground` on this screen it must be replaced with
  `Color.White`-family or `SplashGold*` constants (as the rest of the screen does). At vc57
  there should be none — this is a checklist item, not an expected diff.

**File:** `PurchaseScreen.kt`

---

## Scope notes

- No change to the pre-purchase gate usage or the Paywall route — both render the same
  composable and both get the fix.
- Edge-to-edge: the root Box already extends under the status/navigation bars via the
  `WindowInsets(0,0,0,0)`-free layout; with the solid dark base, system-bar areas are dark in
  both modes — verify status-bar icons stay light on this screen (they should, the activity is
  edge-to-edge with the dark teal behind).

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Root Box gets solid `SplashTealEdge`; radial header blends into it | `PurchaseScreen.kt` |
| 2 | Checklist: no theme-driven `on*` colours left on the dark backdrop | `PurchaseScreen.kt` |
