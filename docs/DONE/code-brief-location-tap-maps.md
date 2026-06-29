# Macaco — EntryDetailScreen: Location chip opens Google Maps

Two-file change. Adds `AppActions.openMapsSearch()` and wires both location chips in
`EntryDetailScreen` so tapping the location opens Google Maps (or any installed maps app)
with the location as a search query.

---

## Change 1 — Add `openMapsSearch` to AppActions

```kotlin
// ADD to AppActions.kt — after the existing openUrl() function

/**
 * Opens the location in Google Maps (or any installed map app) as a search query.
 * Uses the `geo:0,0?q=` URI scheme — no coordinates needed, the map app resolves
 * the name itself. Falls back silently if no map app is installed (edge case).
 */
fun openMapsSearch(context: Context, location: String) {
    val uri = Uri.parse("geo:0,0?q=${Uri.encode(location)}")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}
```

File: `app/src/main/java/com/houseofmmminq/macaco/util/AppActions.kt`

---

## Change 2 — Wire the main location chip (FlowRow, line ~727)

```kotlin
// BEFORE — EntryDetailScreen.kt lines ~725-741 (main chip in the FlowRow)
if (entry.location.isNotBlank()) {
    AssistChip(
        onClick = {},
        label = { Text(entry.location) },
        leadingIcon = {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.primary,
            leadingIconContentColor = MaterialTheme.colorScheme.primary
        )
    )
}

// AFTER
if (entry.location.isNotBlank()) {
    AssistChip(
        onClick = { AppActions.openMapsSearch(context, entry.location) },
        label = { Text(entry.location) },
        leadingIcon = {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.primary,
            leadingIconContentColor = MaterialTheme.colorScheme.primary
        )
    )
}
```

File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/EntryDetailScreen.kt`

---

## Change 3 — Wire the second location chip (landscape/header section, line ~461)

There is a second location chip earlier in the file (around line 459) inside a separate
layout branch (landscape / tablet header). Apply the identical `onClick` change there too.

```kotlin
// BEFORE — EntryDetailScreen.kt line ~461
if (entry.location.isNotBlank()) {
    AssistChip(
        onClick = {},
        label = { Text(entry.location) },
        leadingIcon = {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.primary,
            leadingIconContentColor = MaterialTheme.colorScheme.primary
        )
    )
}

// AFTER
if (entry.location.isNotBlank()) {
    AssistChip(
        onClick = { AppActions.openMapsSearch(context, entry.location) },
        label = { Text(entry.location) },
        leadingIcon = {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.primary,
            leadingIconContentColor = MaterialTheme.colorScheme.primary
        )
    )
}
```

File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/EntryDetailScreen.kt`

---

## Scope

- **In:** both location `AssistChip` onClick handlers in `EntryDetailScreen`; new
  `openMapsSearch` function in `AppActions`.
- **Out:** the date chip `onClick` — still a no-op, not touched.
- **Out:** Adventures map navigation — this opens an external maps app, not Macaco's own map.
- **No new strings. No ViewModel changes. No navigation changes. No permissions needed.**

## Verification

1. Open any entry with a location.
2. Tap the location chip.
3. Google Maps (or the device's default map app) should open with the location text as a search query.
4. Verify on both portrait (main FlowRow chip) and landscape/tablet (header chip).

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `openMapsSearch(context, location)` using `geo:0,0?q=` intent | `AppActions.kt` |
| 2 | Wire main location chip `onClick` to `openMapsSearch` | `EntryDetailScreen.kt` |
| 3 | Wire landscape/header location chip `onClick` to `openMapsSearch` | `EntryDetailScreen.kt` |
