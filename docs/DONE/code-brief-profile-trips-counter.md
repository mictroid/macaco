# Macaco — ProfileScreen: Add Trips Counter to Stats Row

`tripName` is live on `TravelEntry` (v1.5). The profile stats row shows Memories · Locations · Photos.
Add a Trips count between Memories and Locations, hidden when 0 so new users aren't confused.

---

## Add Trips stat to the stats row

**Problem:** The stats card in `ProfileScreen.kt` (around line 296) shows three stats:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth().padding(24.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically
) {
    StatItem(value = "${entries.size}", label = stringResource(R.string.profile_memories))
    Box(modifier = Modifier.width(1.dp).height(48.dp).background(MaterialTheme.colorScheme.outlineVariant))
    StatItem(
        value = entries.mapNotNull { it.location.ifBlank { null } }.distinct().size.toString(),
        label = stringResource(R.string.profile_locations)
    )
    Box(modifier = Modifier.width(1.dp).height(48.dp).background(MaterialTheme.colorScheme.outlineVariant))
    StatItem(
        value = entries.sumOf { it.photoUris.size }.toString(),
        label = stringResource(R.string.profile_photos)
    )
}
```

No Trips counter. `entries` is already `collectAsState()` from `viewModel.entries` (line 80).

**Fix:** Compute `tripCount` inline from `entries` — same pattern as the other stats — and
insert it between Memories and Locations, guarded by `if (tripCount > 0)`.

```
┌──────────┬──────────┬────────────┬────────┐
│ Memories │  Trips   │  Locations │ Photos │
│    12    │    3     │     8      │   47   │
└──────────┴──────────┴────────────┴────────┘
           ↑ only shown when tripCount > 0
```

**File:** `ui/screens/ProfileScreen.kt`

Add the trip count computation just before the stats Row (after `val entries by viewModel.entries.collectAsState()`):

```kotlin
val tripCount = entries
    .mapNotNull { it.tripName?.trim()?.ifBlank { null } }
    .distinct()
    .size
```

Then update the stats `Row` to insert a conditional Trips divider + stat after Memories:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth().padding(24.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically
) {
    StatItem(value = "${entries.size}", label = stringResource(R.string.profile_memories))

    if (tripCount > 0) {
        Box(modifier = Modifier.width(1.dp).height(48.dp).background(MaterialTheme.colorScheme.outlineVariant))
        StatItem(value = tripCount.toString(), label = stringResource(R.string.profile_trips))
    }

    Box(modifier = Modifier.width(1.dp).height(48.dp).background(MaterialTheme.colorScheme.outlineVariant))
    StatItem(
        value = entries.mapNotNull { it.location.ifBlank { null } }.distinct().size.toString(),
        label = stringResource(R.string.profile_locations)
    )
    Box(modifier = Modifier.width(1.dp).height(48.dp).background(MaterialTheme.colorScheme.outlineVariant))
    StatItem(
        value = entries.sumOf { it.photoUris.size }.toString(),
        label = stringResource(R.string.profile_photos)
    )
}
```

No new imports needed.

---

## New string key (×11 languages)

**File:** `app/src/main/res/values/strings.xml` and all locale variants

| Key | EN value |
|-----|----------|
| `profile_trips` | Trips |

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Compute `tripCount` inline; insert conditional Trips stat between Memories and Locations | `ui/screens/ProfileScreen.kt` |
| 2 | Add `profile_trips` string key | `res/values*/strings.xml` (×11) |
