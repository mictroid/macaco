# Macaco — Accessibility Pass: Touch Targets + Text Sizes + Mood Semantics

Approved UX priority #4. Mechanical fixes: sub-48dp touch targets, sub-12sp informational
text, and screen-reader state for mood chips. Touches `JournalListScreen.kt`,
`NewEditEntryScreen.kt`, `ProfileScreen.kt`.

**Conflicts with pending briefs — ORDERING MATTERS:**
- Implement AFTER `code-brief-journal-list-navigation.md` — that brief rewrites
  `JournalListScreen`'s header (and removes the 9sp slogan + bumps the count size itself);
  this brief's JournalListScreen changes are limited to the card/banner areas, which it
  doesn't touch.
- `code-brief-video-transcode-guards.md` and `code-brief-video-thumbnail-perf.md` also edit
  `NewEditEntryScreen`, but in different functions (video launchers/overlay and
  `VideoThumbnailTile`); this brief touches the media-tile remove button and `MoodChip` /
  `MoodSelector`. Safe to batch; keep the diffs separate.

---

## Change 1 — Card tag chips become non-interactive

**Problem:** `TagChips` on journal cards are tappable filters rendered as plain 24dp-tall text
pills — an invisible interactive element inside a tappable card. Mis-taps trigger filtering
when the user meant to open the entry, and the target is half the 48dp guideline. Filtering
already has a dedicated, discoverable home: the `TagFilterRow` above the list.

**Fix:** Drop the click from card chips (keep the selected highlight — it still communicates
which filter matched).

```kotlin
// BEFORE (JournalListScreen.TagChips, ~line 1150)
                modifier = Modifier
                    .widthIn(max = 100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                    .clickable { onTagClick(tag) }
                    .padding(horizontal = 8.dp, vertical = 3.dp)

// AFTER — decorative label; filtering lives in TagFilterRow (48dp FilterChips)
                modifier = Modifier
                    .widthIn(max = 100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
```

Remove the now-unused `onTagClick` parameter from `TagChips` and the two `EntryCard` call
sites (`onTagClick = { viewModel.toggleTagFilter(it) }` — both trip and month sections), and
from `EntryCard`'s own parameter list.

**File:** `JournalListScreen.kt`

---

## Change 2 — On This Day dismiss: 48dp target

**Problem:** The dismiss circle is 24dp — half the minimum.

```kotlin
// BEFORE (OnThisDayBanner, ~line 1215)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {

// AFTER — 40dp visual circle is still light, and minimumInteractiveComponentSize
// guarantees the 48dp platform minimum without changing the row height perceptibly.
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
```

Import `androidx.compose.material3.minimumInteractiveComponentSize`.

**File:** `JournalListScreen.kt`

---

## Change 3 — Media-tile remove (×): bigger hit area, same look

**Problem:** The × on photo/video tiles in the editor is a 20dp target at the tile corner.

**Fix:** Keep the 20dp visual, expand the hit area to ~44dp by padding INSIDE the clickable.
(A full 48dp `IconButton` would cover a third of the 80dp tile.)

```kotlin
// BEFORE (NewEditEntryScreen, media row remove button — the Box with .size(20.dp), ~line 700)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .clickable {

// AFTER — outer 44dp clickable, inner 20dp visual chip
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(44.dp)
                                    .clickable {
                                        /* existing removal body unchanged */
                                    },
                                contentAlignment = Alignment.TopEnd
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    /* existing Icon unchanged */
                                }
                            }
```

(Code: move the existing `.clickable { … }` body — photo/video branch + `mediaOrder` removal +
sessionAdded cleanup — onto the outer Box, and the Icon into the inner one. Verify against the
live media-row section; it was reworked in the video feature.)

**File:** `NewEditEntryScreen.kt`

---

## Change 4 — Mood chips: selection state for TalkBack

**Problem:** `MoodChip` is a plain clickable Box with an emoji Text — TalkBack announces the
emoji name but not that it's selectable or selected (state is colour-only).

```kotlin
// BEFORE (NewEditEntryScreen.MoodChip, ~line 1076)
private fun MoodChip(emoji: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) SplashGold else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {

// AFTER
private fun MoodChip(emoji: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) SplashGold else MaterialTheme.colorScheme.surfaceVariant
            )
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center
    ) {
```

Imports: `androidx.compose.foundation.selection.selectable`,
`androidx.compose.ui.semantics.Role`. (Keep `SplashGold` here for now —
`code-brief-theme-chrome-policy.md` retokens it; whichever lands second wins, the two changes
don't collide textually if chrome-policy edits only the `background(...)` line.)

**File:** `NewEditEntryScreen.kt`

---

## Change 5 — Text floor: 12sp for informational text

**Problem:** Profile still shows the slogan at 10sp (the journal-list copy is removed by the
navigation brief).

```kotlin
// BEFORE (ProfileScreen portrait header, ~line 688)
                    Text(
                        text = "Roam Freely. Forget Nothing.",
                        color = SplashGold.copy(alpha = 0.82f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 1.sp
                    )

// AFTER — decorative slogan is fine, but not below the readable floor
                    Text(
                        text = "Roam Freely. Forget Nothing.",
                        color = SplashGold.copy(alpha = 0.82f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 1.sp
                    )
```

Sweep the remaining sub-12sp `fontSize` literals on informational text: LoginScreen slogan
(11sp → 12sp), PurchaseScreen slogan (11sp → 12sp), MapScreen mapped-count (11sp → 12sp).
Splash slogan (13sp) already passes.

**Files:** `ProfileScreen.kt`, `LoginScreen.kt`, `PurchaseScreen.kt`, `MapScreen.kt`

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Card tag chips → non-interactive labels | `JournalListScreen.kt` |
| 2 | On This Day dismiss → 48dp minimum target | `JournalListScreen.kt` |
| 3 | Media-tile × → 44dp hit area, 20dp visual | `NewEditEntryScreen.kt` |
| 4 | MoodChip → `selectable` semantics (RadioButton role) | `NewEditEntryScreen.kt` |
| 5 | 12sp floor for slogans/counts | `ProfileScreen.kt`, `LoginScreen.kt`, `PurchaseScreen.kt`, `MapScreen.kt` |
