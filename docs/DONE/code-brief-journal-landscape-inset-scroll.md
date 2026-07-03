# Macaco — Journal List: On This Day Scrollability + Nav Bar Inset

Two landscape bugs in `JournalListScreen.kt`:

1. **Can't scroll past "On This Day"** — the banner sits above the `LazyColumn` in a fixed
   `Column`. In landscape the banner takes ~200 dp; the LazyColumn gets the leftover ~130 dp
   and renders so few visible entries that the page appears non-scrollable. Touch gestures on
   the banner area don't propagate to the list below.

2. **× button (and tag chips) hidden under the system nav bar** — the Scaffold uses
   `contentWindowInsets = WindowInsets(0, 0, 0, 0)` so nothing in the content area handles
   the side nav bar that appears on the right edge in landscape. The dismiss × on the banner
   card and the last few tag chips are partially unreachable.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/JournalListScreen.kt`

---

## Change 1 — Add missing imports (~line 15)

`WindowInsets` is already imported; add the four companions needed for the inset modifier.

```kotlin
// ADD after the existing WindowInsets import:
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
```

---

## Change 2 — Add nav bar end inset to the content Column (~line 736)

One line added to the Column modifier. This pushes ALL content (banner, tags, list items)
away from the right-edge system nav bar in landscape without affecting portrait layout
(the end inset is 0 dp when the nav bar is at the bottom).

### BEFORE
```kotlin
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // Faint teal wash from the top so the page isn't a flat slab behind the cards.
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            ) {
```

### AFTER
```kotlin
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End))
                    // Faint teal wash from the top so the page isn't a flat slab behind the cards.
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            ) {
```

---

## Change 3 — Move OnThisDayBanner into the LazyColumn (~line 750)

Currently the banner lives ABOVE the `LazyColumn` in the outer Column. Moving it inside
the LazyColumn as the first item makes the entire page — banner + entries — one scrollable
unit. A single upward swipe anywhere on screen dismisses the banner into view of the entries.

### BEFORE (outer Column body, lines 750–781)
```kotlin
                if (onThisDayEntries.isNotEmpty() && !onThisDayDismissed) {
                    OnThisDayBanner(
                        entries = onThisDayEntries,
                        cachedDrivePhotos = cachedDrivePhotos,
                        onEntryClick = onEntryClick,
                        onDismiss = { onThisDayDismissed = true }
                    )
                }
                if (allTags.isNotEmpty()) {
                    TagFilterRow(
                        tags = allTags,
                        selected = selectedTags,
                        onToggle = { viewModel.toggleTagFilter(it) },
                        onClear = { viewModel.clearTagFilter() }
                    )
                }
                // Wider side gutters on tablets (sw600dp+) so the single-column list doesn't stretch.
                val listHorizontalPadding =
                    if (LocalConfiguration.current.screenWidthDp >= 600) 80.dp else 16.dp
                when {
                    entries.isEmpty() -> EmptyState(modifier = Modifier.fillMaxSize())
                    visibleEntries.isEmpty() -> NoMatchState(modifier = Modifier.fillMaxSize())
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = listHorizontalPadding,
                            top = 10.dp,
                            end = listHorizontalPadding,
                            bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (hasTrips) {
```

### AFTER
```kotlin
                if (allTags.isNotEmpty()) {
                    TagFilterRow(
                        tags = allTags,
                        selected = selectedTags,
                        onToggle = { viewModel.toggleTagFilter(it) },
                        onClear = { viewModel.clearTagFilter() }
                    )
                }
                // Wider side gutters on tablets (sw600dp+) so the single-column list doesn't stretch.
                val listHorizontalPadding =
                    if (LocalConfiguration.current.screenWidthDp >= 600) 80.dp else 16.dp
                when {
                    entries.isEmpty() -> EmptyState(modifier = Modifier.fillMaxSize())
                    visibleEntries.isEmpty() -> NoMatchState(modifier = Modifier.fillMaxSize())
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = listHorizontalPadding,
                            top = 10.dp,
                            end = listHorizontalPadding,
                            bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Banner is now the first LazyColumn item so the whole page
                        // is one scrollable unit — swipe up anywhere to reach entries.
                        if (onThisDayEntries.isNotEmpty() && !onThisDayDismissed) {
                            item(key = "on_this_day_banner") {
                                OnThisDayBanner(
                                    entries = onThisDayEntries,
                                    cachedDrivePhotos = cachedDrivePhotos,
                                    onEntryClick = onEntryClick,
                                    onDismiss = { onThisDayDismissed = true }
                                )
                            }
                        }
                        if (hasTrips) {
```

The only behavioural change: the banner is no longer shown when a tag filter is active but
no entries match (`visibleEntries.isEmpty()`). This is acceptable — the "on this day"
reminder is not relevant when the user has narrowed to a specific tag with zero results.

---

## Cross-screen audit: right-edge nav bar

The user asked to review all pages. Here is what was found:

| Screen | Status |
|---|---|
| `JournalListScreen.kt` | **Fixed by this brief** (Column inset + banner move) |
| `ProfileScreen.kt` — right pane | **Fixed by `code-brief-profile-landscape-nav-inset.md`** |
| `MapScreen.kt` — east chevron | ✅ Already has `windowInsetsPadding(…End)` |
| `NewEditEntryScreen.kt` | ✅ Scaffold `TopAppBar` actions are inset-aware by default |
| `EntryDetailScreen.kt` | ✅ Scaffold TopAppBar handles insets |
| `SettingsScreen.kt` | ✅ Standard Scaffold, no manually-positioned end elements |

No other screens have manually-positioned interactive content at the right edge without
inset handling.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `WindowInsetsSides`, `navigationBars`, `only`, `windowInsetsPadding` imports | `JournalListScreen.kt` |
| 2 | Add `.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End))` to content Column | `JournalListScreen.kt` |
| 3 | Move `OnThisDayBanner` call from outer Column into LazyColumn as first `item` | `JournalListScreen.kt` |
