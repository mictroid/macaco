# Macaco — EntryDetailScreen: Tablet layout improvements

Two fixes, one file: `ui/screens/EntryDetailScreen.kt`.

---

## Background

The existing phone-landscape two-pane layout is gated on `screenHeightDp < 480`. A landscape
tablet (Tab A9+: 1200×800dp) has `screenHeightDp = 800`, so it misses this check entirely and
falls into the single-column `LazyColumn` — stretching a hero photo across 1200dp of width with
content scrolling below. Portrait tablet is fine (photos scale to 52% height, content width is
appropriate for a journal). Only landscape tablet and tag chip overflow need fixing.

---

## Fix 1 — Extend the two-pane condition to cover landscape tablets

### 1a — Replace the `isLandscape` val (line ~321)

```kotlin
// BEFORE
val isLandscape = LocalConfiguration.current.screenHeightDp < 480

// AFTER
// Two-pane layout for: (a) phone landscape (short screen) OR (b) tablet in landscape
// orientation (wide AND wider-than-tall). Portrait tablet (tall screen) stays single-column.
val configuration = LocalConfiguration.current
val isLandscape = configuration.screenHeightDp < 480 ||
    (configuration.screenWidthDp >= 600 && configuration.screenWidthDp > configuration.screenHeightDp)
```

### 1b — Adjust photo/content panel weights for tablet landscape

The existing phone-landscape weights (photo 45%, content 55%) are tuned for narrow screens.
On a 1200dp tablet those become 540dp / 660dp — skewing the content panel too wide and the
photo panel too narrow. Use 50/50 on tablet; keep 45/55 on phone.

Find the two `weight(...)` calls in the landscape `Row` (lines ~332 and ~408):

```kotlin
// BEFORE — photo panel
Box(
    modifier = Modifier
        .weight(0.45f)
        .fillMaxHeight()
        ...
)

// AFTER — 50/50 on tablet, 45/55 on phone
val isTablet = configuration.screenWidthDp >= 600
Box(
    modifier = Modifier
        .weight(if (isTablet) 0.50f else 0.45f)
        .fillMaxHeight()
        ...
)

// BEFORE — content panel
LazyColumn(
    state = listState,
    modifier = Modifier
        .weight(0.55f)
        .fillMaxHeight()
)

// AFTER
LazyColumn(
    state = listState,
    modifier = Modifier
        .weight(if (isTablet) 0.50f else 0.55f)
        .fillMaxHeight()
)
```

`isTablet` is already declared in 1a's `configuration` block — use the same val.

### 1c — Add horizontal padding to the content panel on tablet landscape

The landscape content `LazyColumn`'s inner `Column` (line ~432) uses
`padding(horizontal = 16.dp, vertical = 12.dp)`. On a 50%-weight tablet panel that's a
600dp column with only 16dp gutters — too wide for comfortable reading. Add 32dp each side
on tablet:

```kotlin
// BEFORE
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
)

// AFTER
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(
            horizontal = if (isTablet) 32.dp else 16.dp,
            vertical = 12.dp
        ),
    verticalArrangement = Arrangement.spacedBy(10.dp)
)
```

---

## Fix 2 — Tag chip text overflow in EntryDetailScreen

Tag chip text has no overflow handling in `EntryDetailScreen`. Long tags get hard-clipped
rather than truncated with ellipsis. Fix both occurrences — one in the landscape panel
(line ~539) and one in the portrait `LazyColumn` (line ~823).

Both occurrences look like:

```kotlin
entry.tags.forEach { tag ->
    AssistChip(
        onClick = { onTagClick(tag) },
        label = { Text("#$tag") },
        border = null,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
```

Change **both** to:

```kotlin
entry.tags.forEach { tag ->
    AssistChip(
        onClick = { onTagClick(tag) },
        label = {
            Text(
                "#$tag",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp)
            )
        },
        border = null,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
```

Add import if not present: `import androidx.compose.ui.text.style.TextOverflow`

---

## Scope

- **In:** Landscape tablet two-pane layout; 50/50 weight split on tablet; content panel
  horizontal padding on tablet; tag chip overflow — all in `EntryDetailScreen.kt`.
- **Out:** Portrait tablet — layout is correct as-is (hero scales to 52% height, content
  width is fine for a journal).
- **Out:** `JournalListScreen` and `NewEditEntryScreen` — already handled in earlier briefs.

---

## Verification

On Tab A9+:

1. **Landscape**: Two-pane layout — photo left (50%), scrollable content right (50%). 
   Photo panel should show the editorial collage filling the panel height. Content panel
   should have comfortable reading width (~268dp usable after 32dp padding each side).
2. **Portrait**: Unchanged — hero photo at ~52% screen height, content below. No regression.
3. **Tag chips**: Long tags like "#mountain", "#adventure" show with ellipsis (`#mount…`)
   rather than hard-clipping (`#moun`). Short tags show in full.
4. **Phone landscape**: Unchanged — still 45/55 split, 16dp content padding.
5. **Phone portrait**: Unchanged — no regressions.
