# Macaco — NewEditEntryScreen: Fix Tablet Form Padding Not Applying

The `formHorizontalPadding` value is passed as `contentPadding` on the `LazyColumn`, but Compose's
`LazyColumn.contentPadding` does not reliably constrain item widths — items with `fillMaxWidth()`
still measure against the full layout width. Moving the padding to `Modifier.padding(horizontal =
formHorizontalPadding)` on the LazyColumn itself is the reliable fix. Touches
`ui/screens/NewEditEntryScreen.kt` only.

---

## Change: move horizontal padding from contentPadding to Modifier

**Problem:** `contentPadding = PaddingValues(horizontal = formHorizontalPadding, vertical = 12.dp)`
is set on the `LazyColumn`. For a vertical `LazyColumn`, horizontal `contentPadding` clips the
drawable area but does not reliably reduce the measured width passed to items. `OutlinedTextField`
and other `fillMaxWidth()` items still span the full screen width on the Tab A9+, making the form
look identical to the phone layout.

**Fix:** Remove the horizontal value from `contentPadding` and apply it via
`Modifier.padding(horizontal = formHorizontalPadding)` on the LazyColumn's modifier chain.
Keep vertical contentPadding (`12.dp`) unchanged — that controls scroll insets and is not affected.

```
BEFORE (fields span full width on tablet):
┌──────────────────────────────────────┐
│ Title *                              │
│ Location                             │
│ Trip                                 │
└──────────────────────────────────────┘

AFTER (fields inset 100dp each side on tablet, 16dp on phone):
┌──────────────────────────────────────┐
│          │ Title *        │          │
│  100dp   │ Location       │  100dp   │
│          │ Trip           │          │
└──────────────────────────────────────┘
```

Find the `LazyColumn` in `NewEditEntryScreen.kt` (inside `MacacoWatermarkBackground`, just after
the `formHorizontalPadding` declaration):

```kotlin
// BEFORE:
val formHorizontalPadding =
    if (LocalConfiguration.current.screenWidthDp >= 600) 100.dp else 16.dp
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .imePadding(),
    contentPadding = PaddingValues(horizontal = formHorizontalPadding, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
)

// AFTER:
val formHorizontalPadding =
    if (LocalConfiguration.current.screenWidthDp >= 600) 100.dp else 16.dp
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .imePadding()
        .padding(horizontal = formHorizontalPadding),   // ← moved here from contentPadding
    contentPadding = PaddingValues(vertical = 12.dp),   // ← horizontal removed
    verticalArrangement = Arrangement.spacedBy(16.dp)
)
```

No other changes. The `LazyRow` inside (for the photo strip) inherits the constrained width
automatically. No string changes, no new imports.

**File:** `ui/screens/NewEditEntryScreen.kt`

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Move `horizontal = formHorizontalPadding` from `contentPadding` to `Modifier.padding(horizontal = ...)` so items are actually constrained to the narrower width on tablet | `ui/screens/NewEditEntryScreen.kt` |
