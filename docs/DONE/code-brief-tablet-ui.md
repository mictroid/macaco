# Macaco — JournalListScreen / NewEditEntryScreen: Tablet UI Improvements

Four layout fixes for the Samsung Tab A9+ (and any screen ≥ 600 dp wide): tag chip truncation,
entry list padding, photo grid height, and New Entry form max-width.
Touches `JournalListScreen.kt` and `NewEditEntryScreen.kt`.

No new dependencies. Tablet detection uses `LocalConfiguration.current.screenWidthDp >= 600`
(the standard Android sw600dp breakpoint), which is already available in every composable via
`LocalConfiguration.current` — no adaptive library needed.

---

## Fix 1: Tag chip truncation on entry cards

**Problem:** Tag chips on entry cards clip their text abruptly — `#mountain` becomes `#moun`,
`#adventure` becomes `#adv`. The `TagChips` Row has `clipToBounds()` which hard-clips any
content that overflows, and the chip `Text` has no `overflow` parameter so there is no `…`
fallback. On phone-width screens short tags happen to fit; on tablet the layout constraints
cause chips to clip aggressively.

**Fix:** Remove `clipToBounds()` from the `TagChips` Row (let it size naturally to its chips)
and add `overflow = TextOverflow.Ellipsis` + `widthIn(max = 100.dp)` to each chip `Text` as a
safety net against very long tags. The existing `maxLines = 1` is kept.

Find `TagChips` in `JournalListScreen.kt` (the private composable, not `TagFilterRow`):

```kotlin
// BEFORE:
@Composable
private fun TagChips(
    tags: List<String>,
    selectedTags: Set<String>,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tags.forEach { tag ->
            val isSelected = tag in selectedTags
            Text(
                "#$tag",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(...)
                    .clickable { onTagClick(tag) }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

// AFTER:
@Composable
private fun TagChips(
    tags: List<String>,
    selectedTags: Set<String>,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,           // ← remove clipToBounds()
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tags.forEach { tag ->
            val isSelected = tag in selectedTags
            Text(
                "#$tag",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,    // ← add ellipsis fallback
                modifier = Modifier
                    .widthIn(max = 100.dp)           // ← cap width so very long tags still truncate
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                    .clickable { onTagClick(tag) }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
```

`TextOverflow` is already imported in this file (used elsewhere). `widthIn` may need importing:
```kotlin
import androidx.compose.foundation.layout.widthIn
```

---

## Fix 2: Entry list — wider side padding on tablet

**Problem:** On an 865 dp tablet, the journal entry cards span almost the full screen width.
The single-column list looks stretched; cards read better with breathing room on both sides.

**Fix:** Read `screenWidthDp` from `LocalConfiguration` and increase the `LazyColumn`'s
horizontal `contentPadding` on screens ≥ 600 dp.

```
Phone (< 600 dp):                Tablet (≥ 600 dp):
┌──────────────────────┐         ┌──────────────────────────────────┐
│▌████████████████████▌│         │         ┌────────────────┐        │
│▌  Entry card         ▌│         │         │  Entry card    │        │
│▌████████████████████▌│         │         └────────────────┘        │
└──────────────────────┘         └──────────────────────────────────┘
  16 dp padding each side          80 dp padding each side
```

Find the `LazyColumn` that renders the entry list (the `else ->` branch around line 531 in
`JournalListScreen.kt`):

```kotlin
// Add at the top of JournalListScreen (or near the LazyColumn call, once):
val configuration = LocalConfiguration.current
val isTablet = configuration.screenWidthDp >= 600
val listHorizontalPadding = if (isTablet) 80.dp else 16.dp

// Replace the existing contentPadding:
// BEFORE:
contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 96.dp),

// AFTER:
contentPadding = PaddingValues(
    start = listHorizontalPadding,
    top = 10.dp,
    end = listHorizontalPadding,
    bottom = 96.dp
),
```

`LocalConfiguration` import (add if missing):
```kotlin
import androidx.compose.ui.platform.LocalConfiguration
```

---

## Fix 3: Entry photo grid — taller on tablet

**Problem:** `EntryPhotoArea` has a hardcoded `height(120.dp)`. At tablet width this produces
a very squat, landscape-heavy photo strip that looks out of proportion.

```
Phone (~400 dp wide):        Tablet (~700 dp wide, after padding fix):
┌────────────────────┐       ┌──────────────────────────────────────┐
│  photo  120 dp     │       │         photo         200 dp         │
└────────────────────┘       └──────────────────────────────────────┘
   good aspect ratio             still reasonable, not squat
```

**Fix:** Read `LocalConfiguration` inside `EntryPhotoArea` and use 200 dp on tablet.

Find `EntryPhotoArea` in `JournalListScreen.kt` (the `private fun` around line 655):

```kotlin
private fun EntryPhotoArea(
    displayUris: List<String>,
    totalCount: Int,
    mood: String
) {
    val topCorners = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600   // ← add this line
    val photoHeight = if (isTablet) 200.dp else 120.dp               // ← add this line

    if (displayUris.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(photoHeight)       // ← was height(72.dp) — keep the no-photo case proportional too
                .clip(topCorners)
                ...
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(photoHeight)           // ← was height(120.dp)
            .clip(topCorners)
    ) {
        // rest of the when() block is unchanged
    }
}
```

Note: the no-photo placeholder was `height(72.dp)`. Change it to `if (isTablet) 120.dp else 72.dp`
for the same proportional increase. Or simply use `photoHeight` for both cases as shown above —
the placeholder emoji scales fine at any height.

---

## Fix 4: New Entry form — centered and max-width on tablet

**Problem:** Form fields (Title, Location, Trip, Date, Description) span the full ~865 dp tablet
width. Single-line fields at this width feel awkward; the form was designed for ~400 dp phones.

```
Phone:                           Tablet (current):
┌──────────────────┐             ┌────────────────────────────────────┐
│ Title *          │             │ Title *                            │
│ Location         │             │ Location                           │
└──────────────────┘             └────────────────────────────────────┘

Tablet (after fix):
┌────────────────────────────────────┐
│        ┌──────────────────┐        │
│        │ Title *          │        │
│        │ Location         │        │
│        └──────────────────┘        │
└────────────────────────────────────┘
         centred, max 620 dp
```

**Fix:** Wrap the `LazyColumn`'s `contentPadding` in an adaptive horizontal padding. The
`LazyColumn` already uses `contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)`
— replace `16.dp` with a tablet-aware value.

Find the `LazyColumn` in `NewEditEntryScreen.kt` (around line 344):

```kotlin
// Add near the top of the composable:
val isTablet = LocalConfiguration.current.screenWidthDp >= 600
val formHorizontalPadding = if (isTablet) 100.dp else 16.dp

// Replace existing contentPadding:
// BEFORE:
contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),

// AFTER:
contentPadding = PaddingValues(horizontal = formHorizontalPadding, vertical = 12.dp),
```

This leaves ~665 dp of form width on the Tab A9+ — wide enough to be comfortable, narrow enough
to feel intentional. No structural change to the field layout is needed.

`LocalConfiguration` import (add if missing):
```kotlin
import androidx.compose.ui.platform.LocalConfiguration
```

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Remove `clipToBounds()` from `TagChips` Row; add `overflow = TextOverflow.Ellipsis` + `widthIn(max = 100.dp)` to chip `Text` | `ui/screens/JournalListScreen.kt` |
| 2 | Increase `LazyColumn` horizontal `contentPadding` from 16 dp to 80 dp when `screenWidthDp ≥ 600` | `ui/screens/JournalListScreen.kt` |
| 3 | Increase `EntryPhotoArea` height from 120 dp to 200 dp (placeholder from 72 dp to 120 dp) when `screenWidthDp ≥ 600` | `ui/screens/JournalListScreen.kt` |
| 4 | Increase `LazyColumn` horizontal `contentPadding` from 16 dp to 100 dp in New Entry form when `screenWidthDp ≥ 600` | `ui/screens/NewEditEntryScreen.kt` |
