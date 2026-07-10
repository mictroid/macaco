# Macaco — Collapsed Header: Actually Shrink in Landscape

In landscape, scrolling collapses the brand header (wordmark disappears) but the header barely
gets shorter — because the collapsed state keeps the icon at its full 48dp portrait size no
matter the orientation. This is a bug in the single shared composable `MacacoBrandBlock.kt`, so
it affects all four screens that use its `collapsed` state uniformly: `JournalListScreen.kt`,
`MapScreen.kt`, `ProfileScreen.kt`, `HelpAboutScreen.kt`. One fix in the shared component covers
all of them — same DRY approach as `docs/DONE/code-brief-macaco-brand-header-consistency.md`,
which is what put this component in charge of icon/wordmark sizing in the first place. Touches 1
file, 1 change.

**Not in scope:** `EntryDetailScreen.kt`'s header has no scroll-collapse behavior at all (static
48dp-tall row, already covered separately by `docs/code-brief-entry-detail-header-logo-centering.md`)
— unaffected by this brief.

---

## The collapsed header doesn't really collapse in landscape

**Problem:** `MacacoBrandBlock`'s `collapsed` branch renders the icon at the same fixed
`MacacoBrandIconSize` (48dp) regardless of `isLandscape`, with a flat 8dp bottom padding:

```kotlin
collapsed -> {
    Box(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(MacacoBrandIconSize)
        )
    }
}
```

Going from expanded landscape (icon 48dp + wordmark/count text row, ~76dp total in
`JournalListScreen`) to collapsed (icon 48dp + 8dp padding, 56dp total) only saves ~20dp — the
height of the one text line that disappears. The icon itself, which is the dominant element,
never shrinks. On a landscape screen (already short enough to trigger `isLandscape` at
`screenHeightDp < 480`), a 56dp header after "collapsing" still eats a large share of the
available vertical space — hence "the text disappears, but the header collapses minimally."

Portrait's collapsed state isn't part of this complaint and keeps its current 48dp/8dp sizing
unchanged — those values were deliberately set to match Journal's reference header in the prior
consistency brief, and portrait has more vertical headroom to spare.

**Fix:** In the `collapsed` branch only, size the icon and its bottom padding off `isLandscape` —
48dp/8dp in portrait (unchanged), a genuinely compact 28dp/4dp in landscape. 28dp matches the
icon size `EntryDetailScreen.kt` already uses for its own always-compact header, so this isn't a
new one-off value — it's reusing the app's existing "compact header icon" precedent. This drops
the landscape collapsed header from ~56dp to ~36dp, and widens the expanded-vs-collapsed
landscape swing from ~20dp to ~40dp — an actual, noticeable collapse.

```
BEFORE (landscape):                         AFTER (landscape):
Expanded  ~76dp  [icon 48 + wordmark row]    Expanded  ~76dp  [icon 48 + wordmark row]
Collapsed ~56dp  [icon 48 + 8dp pad]         Collapsed ~36dp  [icon 28 + 4dp pad]
          ─20dp saved on scroll                        ─40dp saved on scroll
```

### BEFORE — `MacacoBrandBlock.kt` (lines 47–59)

```kotlin
    when {
        collapsed -> {
            Box(
                modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(MacacoBrandIconSize)
                )
            }
        }
```

### AFTER

```kotlin
    when {
        collapsed -> {
            // Landscape is already height-constrained (that's why isLandscape triggers below
            // 480dp), so its collapsed state needs to actually shrink, not just drop the
            // wordmark while keeping the full portrait-sized icon. 28dp matches the compact
            // icon size EntryDetailScreen's own (non-collapsing) header already uses. Portrait
            // keeps the original 48dp/8dp — matches Journal's reference header, unchanged.
            val collapsedIconSize = if (isLandscape) 28.dp else MacacoBrandIconSize
            val collapsedBottomPadding = if (isLandscape) 4.dp else 8.dp
            Box(
                modifier = modifier.fillMaxWidth().padding(bottom = collapsedBottomPadding),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(collapsedIconSize)
                )
            }
        }
```

No new imports needed — `dp` is already imported in this file.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/components/MacacoBrandBlock.kt`

---

## Verify after implementing

All four call sites pick this up automatically since they all go through `MacacoBrandBlock`'s
`collapsed` branch — check each one in landscape, scrolled far enough to trigger `collapsed`:

- **JournalListScreen** — scroll the entry list down.
- **MapScreen (Adventures)** — pan/zoom the map (`hasMovedMap` drives `collapsed` here).
- **ProfileScreen** — scroll the profile content down.
- **HelpAboutScreen** — scroll down.

In each, confirm: the header visibly shrinks by a lot more than before once collapsed (not just
losing the wordmark), the icon doesn't look awkwardly tiny at 28dp against the brand-teal strip,
and the collapse/expand transition still animates smoothly (all four already wrap this in
`Modifier.animateContentSize()`, so no animation code needs to change). Also re-check portrait on
each screen — it should look completely unchanged, since portrait's sizing branch isn't touched.

## Out of scope

- `EntryDetailScreen.kt` — no scroll-collapse behavior exists there; not affected by this bug.
- Portrait collapsed sizing — unchanged, not part of the complaint.
- The *expanded* landscape header (icon + wordmark, before any scrolling) — unchanged; this brief
  only touches the collapsed-state icon size.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Shrink icon (48dp → 28dp) and bottom padding (8dp → 4dp) in the collapsed state, landscape only, so scroll-collapse actually saves meaningful header height | `ui/components/MacacoBrandBlock.kt` |
