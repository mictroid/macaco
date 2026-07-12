# Macaco — Adventure Reel icon placement + collapsible journal groups

Two unrelated changes bundled from the same review pass: (1) the macaco glyph in the Adventure
Reel's location pill sits far from the location text and reads small, and (2) the journal list's
trip/month section headers should be collapsible for easier browsing. Files touched:
`data/sync/AdventureReelEncoder.kt`, `ui/screens/JournalListScreen.kt`.

---

## 1. Reel location-pill icon: too far from the text, too small

**Problem:** In `drawBranding()`, the pill's macaco glyph is drawn at a fixed `x=48` (near the
pill's left edge, pill spans x=32–688) at a fixed 28px size, while the location text is drawn
`CENTER`-aligned (`textPaint.textAlign = Paint.Align.CENTER`) at `x=360`, the pill's horizontal
midpoint. Because the glyph's position is fixed and unrelated to where the (centred, variable-
width) text actually starts, there's typically a large empty gap between them — the icon reads as
stranded on the far left instead of sitting next to the text.

**Fix:** Measure the text's actual rendered width and anchor the glyph immediately to its left
(with a small gap), so the pair always sits together as one visual group and re-centres correctly
regardless of location-text length. Also bump the glyph from 28px to 34px — noticeably larger,
per feedback. Clamp the icon's x so a very long location string can't push it off the left edge
of the pill.

```
BEFORE (fixed icon position, independent of text):
┌──────────────────────────────────────────────────┐
│ [icon]                    Lisbon, Portugal        │   ← big dead gap
└──────────────────────────────────────────────────┘

AFTER (icon anchored to text's measured left edge):
┌──────────────────────────────────────────────────┐
│              [icon] Lisbon, Portugal              │   ← icon+text read as one group
└──────────────────────────────────────────────────┘
```

**File:** `data/sync/AdventureReelEncoder.kt` (~line 218, pillLogoBitmap load size)

```kotlin
// BEFORE
        val pillLogoBitmap = loadLogoBitmap(sizePx = 28)
```

```kotlin
// AFTER
        // 34 — up from 28. Feedback: the pill glyph read too small next to the location text.
        val pillLogoBitmap = loadLogoBitmap(sizePx = 34)
```

**File:** `data/sync/AdventureReelEncoder.kt` (`drawBranding()`, ~line 428-444)

```kotlin
// BEFORE
        if (overlayText != null) {
            // Use the pre-allocated class fields — never allocate Paint inside the render loop.
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
            // Vertically centre text within the pill (pill midpoint y = 1188; baseline ≈ 1196).
            canvas.drawText(overlayText, 360f, 1196f, textPaint)
        }
```

```kotlin
// AFTER
        if (overlayText != null) {
            // Use the pre-allocated class fields — never allocate Paint inside the render loop.
            canvas.drawRoundRect(
                android.graphics.RectF(32f, 1152f, 688f, 1224f),
                24f, 24f,
                pillPaint
            )
            // textPaint is CENTER-aligned at x=360 (the pill's horizontal midpoint), so the
            // text's actual left edge shifts with its length. Anchor the glyph relative to that
            // measured left edge (instead of the old fixed far-left x=48) so icon+text always
            // read as one adjacent group, centred together in the pill regardless of text length.
            // Clamped to 40f so a very long location string can't push the icon past the pill's
            // rounded left edge.
            val textWidth = textPaint.measureText(overlayText)
            val iconSize = 34f
            val iconGap = 8f
            val iconX = (360f - textWidth / 2f - iconGap - iconSize).coerceAtLeast(40f)
            val iconY = 1152f + (72f - iconSize) / 2f   // vertically centred in the 72px-tall pill
            pillLogoBitmap?.let { glyph ->
                canvas.drawBitmap(glyph, iconX, iconY, pillLogoPaint)
            }
            // Vertically centre text within the pill (pill midpoint y = 1188; baseline ≈ 1196).
            canvas.drawText(overlayText, 360f, 1196f, textPaint)
        }
```

**Out of scope:** the bottom-centre floating watermark logo (`drawBranding`'s other branch,
~line 447-451, the 15%-opacity mark) — that one is unrelated to the location pill and wasn't part
of this feedback.

**Verification:** re-export a reel with a short location ("Rome") and a long one ("Santa
Margherita Ligure, Italy") and confirm the icon stays adjacent to the text and never clips the
pill's rounded edge in either case.

---

## 2. Collapsible trip/month groups on the journal list

**Problem:** `JournalListScreen`'s `LazyColumn` renders every trip section (`TripHeader` +
its entries) and every month section (`MonthHeader` + its entries) fully expanded, with no way to
collapse a group. On an account with many trips/months this makes browsing to a specific one a
long scroll.

**Fix:** Track which section keys are collapsed in local Compose state (session-scoped —
`remember`, not persisted to DataStore, so every fresh app open starts fully expanded; this is a
browsing convenience, not a preference worth persisting). Add a chevron to both `TripHeader` and
`MonthHeader` and make the whole header row tappable to toggle. Skip that section's `items(...)`
call in the `LazyColumn` when its key is collapsed.

**File:** `ui/screens/JournalListScreen.kt` (imports, top of file)

```kotlin
// ADD to the androidx.compose.material.icons.filled imports
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
```

**File:** `ui/screens/JournalListScreen.kt` (~line 176-181, next to the existing `listState`/
`collapsed` header-scroll state — same area, unrelated state, add alongside it)

```kotlin
// ADD
    // Collapsible trip/month groups: which section keys ("trip:<name>" / "month:<label>") are
    // currently collapsed. Session-scoped only (remember, not rememberSaveable/DataStore) — every
    // fresh app open starts fully expanded; this is a browsing convenience, not a setting worth
    // persisting.
    var collapsedSections by remember { mutableStateOf(setOf<String>()) }
    fun toggleSection(key: String) {
        collapsedSections =
            if (key in collapsedSections) collapsedSections - key else collapsedSections + key
    }
```

**File:** `ui/screens/JournalListScreen.kt` (~line 573-604, the trip/month rendering in the
`LazyColumn`)

```kotlin
// BEFORE
                        if (hasTrips) {
                            tripSections.forEach { (trip, sectionEntries) ->
                                item(key = "trip-header-$trip") {
                                    TripHeader(
                                        tripName = trip,
                                        entryCount = sectionEntries.size,
                                        isPurchased = isPurchased == true,
                                        onCreateReel = { viewModel.startReel(trip, sectionEntries) },
                                        onShare = { shareDialogTrip = trip to sectionEntries }
                                    )
                                }
                                items(sectionEntries, key = { it.id }) { entry ->
                                    EntryCard(
                                        entry = entry,
                                        cachedDrivePhotos = cachedDrivePhotos,
                                        selectedTags = selectedTags,
                                        onClick = { onEntryClick(entry.id) }
                                    )
                                }
                            }
                        }
                        monthSections.forEach { (month, sectionEntries) ->
                            item(key = "header-$month") { MonthHeader(month) }
                            items(sectionEntries, key = { it.id }) { entry ->
                                EntryCard(
                                    entry = entry,
                                    cachedDrivePhotos = cachedDrivePhotos,
                                    selectedTags = selectedTags,
                                    onClick = { onEntryClick(entry.id) }
                                )
                            }
                        }
```

```kotlin
// AFTER
                        if (hasTrips) {
                            tripSections.forEach { (trip, sectionEntries) ->
                                val sectionKey = "trip:$trip"
                                item(key = "trip-header-$trip") {
                                    TripHeader(
                                        tripName = trip,
                                        entryCount = sectionEntries.size,
                                        isPurchased = isPurchased == true,
                                        collapsed = sectionKey in collapsedSections,
                                        onToggleCollapse = { toggleSection(sectionKey) },
                                        onCreateReel = { viewModel.startReel(trip, sectionEntries) },
                                        onShare = { shareDialogTrip = trip to sectionEntries }
                                    )
                                }
                                if (sectionKey !in collapsedSections) {
                                    items(sectionEntries, key = { it.id }) { entry ->
                                        EntryCard(
                                            entry = entry,
                                            cachedDrivePhotos = cachedDrivePhotos,
                                            selectedTags = selectedTags,
                                            onClick = { onEntryClick(entry.id) }
                                        )
                                    }
                                }
                            }
                        }
                        monthSections.forEach { (month, sectionEntries) ->
                            val sectionKey = "month:$month"
                            item(key = "header-$month") {
                                MonthHeader(
                                    month = month,
                                    collapsed = sectionKey in collapsedSections,
                                    onToggleCollapse = { toggleSection(sectionKey) }
                                )
                            }
                            if (sectionKey !in collapsedSections) {
                                items(sectionEntries, key = { it.id }) { entry ->
                                    EntryCard(
                                        entry = entry,
                                        cachedDrivePhotos = cachedDrivePhotos,
                                        selectedTags = selectedTags,
                                        onClick = { onEntryClick(entry.id) }
                                    )
                                }
                            }
                        }
```

**File:** `ui/screens/JournalListScreen.kt` (`TripHeader`, ~line 1327-1383)

```kotlin
// BEFORE
@Composable
private fun TripHeader(
    tripName: String,
    entryCount: Int,
    isPurchased: Boolean,
    onCreateReel: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            tripName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f)
        )
        Text(
            pluralStringResource(R.plurals.journal_list_memories, entryCount, entryCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        if (isPurchased) {
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = stringResource(R.string.trip_share_action),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onCreateReel,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Filled.Videocam,
                    contentDescription = stringResource(R.string.reel_create_button),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
```

```kotlin
// AFTER
@Composable
private fun TripHeader(
    tripName: String,
    entryCount: Int,
    isPurchased: Boolean,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onCreateReel: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onToggleCollapse)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
            contentDescription = if (collapsed)
                stringResource(R.string.common_expand) else stringResource(R.string.common_collapse),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            tripName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f)
        )
        Text(
            pluralStringResource(R.plurals.journal_list_memories, entryCount, entryCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        if (isPurchased) {
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = stringResource(R.string.trip_share_action),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onCreateReel,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Filled.Videocam,
                    contentDescription = stringResource(R.string.reel_create_button),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
```

Note: the `Share`/`Videocam` `IconButton`s are nested clickables inside the row's own
`clickable(onToggleCollapse)` — this is standard Compose behaviour (the inner `IconButton`
consumes its own tap; taps elsewhere on the row bubble to the outer `clickable`), no extra
handling needed.

**File:** `ui/screens/JournalListScreen.kt` (`MonthHeader`, ~line 1424-1445)

```kotlin
// BEFORE
@Composable
private fun MonthHeader(month: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            month.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
        )
    }
}
```

```kotlin
// AFTER
@Composable
private fun MonthHeader(month: String, collapsed: Boolean, onToggleCollapse: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp, start = 4.dp)
            .clickable(onClick = onToggleCollapse),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
            contentDescription = if (collapsed)
                stringResource(R.string.common_expand) else stringResource(R.string.common_collapse),
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            month.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
        )
    }
}
```

**Localization:** two new content-description strings, needed across all 11 supported languages.

| Key | EN value |
|-----|----------|
| `common_expand` | Expand |
| `common_collapse` | Collapse |

**Out of scope:**
- Persisting collapsed state across app restarts — explicitly decided against; this is a
  session-scoped browsing convenience, not a setting.
- Collapsing/expanding "all at once" (a single top-level toggle) — not requested; each section
  toggles independently.
- Applying the same collapse affordance to the `OnThisDayBanner` or camera-roll suggestion
  cards — those aren't trip/month groups and weren't part of this request.

**Verification:** with multiple trips and multiple months in the list, tap each header type and
confirm: (1) only that section's entries hide/show, (2) the chevron flips direction, (3) trip
header's Share/Reel buttons still work independently without toggling collapse, (4) force-closing
and reopening the app shows everything expanded again.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Pill glyph anchored to measured text left edge (was fixed far-left `x=48`); size 28px → 34px | `data/sync/AdventureReelEncoder.kt` |
| 2 | `collapsedSections` state + toggle helper | `ui/screens/JournalListScreen.kt` |
| 3 | Skip `items(...)` for collapsed trip/month sections in the `LazyColumn` | `ui/screens/JournalListScreen.kt` |
| 4 | `TripHeader` gains `collapsed`/`onToggleCollapse`, chevron icon, clickable row | `ui/screens/JournalListScreen.kt` |
| 5 | `MonthHeader` gains `collapsed`/`onToggleCollapse`, chevron icon, clickable row | `ui/screens/JournalListScreen.kt` |
| 6 | New strings `common_expand` / `common_collapse` (×11 languages) | `res/values*/strings.xml` |
