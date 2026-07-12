# Macaco — Journal List: fix FAB over-inset on some devices + collapse repeat suggestion cards

Two independent bugs on `JournalListScreen.kt`, reported from live devices (Galaxy S8 vs Galaxy
A53) and from real camera-roll usage. Neither touches the same code path as the other, but both
land in the same file (plus `strings.xml` for the second).

---

## Change 1 — "New Entry" FAB renders far too high on some devices (Galaxy S8)

**Problem:** the FAB was given `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` in an
earlier brief (`docs/DONE/code-brief-content-width-landscape.md`) specifically to stop it hiding
behind the Galaxy A53's system nav bar in landscape (nav bar sits on the side edge there, not the
bottom). That fix works on the A53. On a Galaxy S8, the same modifier pushes the FAB up to
roughly the middle third of the screen — nowhere near the actual nav bar.

`WindowInsets.safeDrawing` is a broad union: status bars, navigation bars, display cutout, IME,
*and* system-gesture insets, all added together as padding. The FAB doesn't need the IME or
gesture components at all — it isn't near a text field, and it was never intentionally protected
from gesture-nav insets. The most likely explanation for the S8-specific blowup is that one of
those extra components (IME or mandatory-system-gestures) reports an inflated, stale, or
otherwise incorrect value on that device/OS combination — a known class of inconsistency across
OEM Android skins, especially older ones — and `safeDrawing` blindly adds whatever it reports as
extra bottom padding.

**Fix:** narrow the FAB's inset to just the two inset types the original A53 fix actually needed
— `navigationBars` (the side nav bar in landscape, bottom nav bar in portrait) unioned with
`displayCutout` (front-camera notch on the opposite edge, for parity with how the content
`Column` below already handles the cutout side). This drops IME and system-gesture insets from
the calculation entirely, which should eliminate the S8 over-padding while still fully covering
the original A53 landscape case.

```
BEFORE (S8)                          AFTER (S8, all devices)
┌─────────────────────┐              ┌─────────────────────┐
│ macaco header        │              │ macaco header        │
│ ┌───────────────────┐│              │ ┌───────────────────┐│
│ │ South America      ││              │ │ South America      ││
│ │ [photo] [+New Entry]│ ← wrong      │ │ [photo]  [photo]   ││
│ │         [photo]    ││              │ └───────────────────┘│
│ └───────────────────┘│              │  Patagonian Glacier...│
│  Patagonian Glacier..│              │                       │
│                       │              │                       │
│                       │              │           [+New Entry]│ ← correct
└─────────────────────┘              └─────────────────────┘
```

```kotlin
// ui/screens/JournalListScreen.kt — BEFORE (~line 466-478)

            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onNewEntry,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.common_new_entry)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    // Scaffold's contentWindowInsets is zeroed on this screen (hand-managed
                    // insets elsewhere), so the FAB needs its own — otherwise it renders behind
                    // the system nav bar in landscape (nav bar sits on the side edge there, not
                    // the bottom), as seen on the Galaxy A53.
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                )
            },
```

```kotlin
// ui/screens/JournalListScreen.kt — AFTER

            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onNewEntry,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.common_new_entry)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    // Scaffold's contentWindowInsets is zeroed on this screen (hand-managed
                    // insets elsewhere), so the FAB needs its own. Deliberately narrower than
                    // `safeDrawing` (which also bundles IME + system-gesture insets and was
                    // observed pushing the FAB to mid-screen on a Galaxy S8) — navigationBars
                    // covers the side nav bar in landscape (the original A53 bug this modifier
                    // was added for) and displayCutout covers the front-camera notch on the
                    // opposite edge, matching the content Column's own cutout handling below.
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                    )
                )
            },
```

New imports: `androidx.compose.foundation.layout.union`, `androidx.compose.foundation.layout.displayCutout`.
(`navigationBars` is already imported, line 30.)

**File:** `ui/screens/JournalListScreen.kt`.

---

## Change 2 — Same-place suggestion cards read as duplicates

**Problem:** `PhotoRollScanner.scan()` (`util/PhotoRollScanner.kt`) clusters camera-roll photos
by a 6-hour time gap and ~2km radius (`CLUSTER_GAP_MILLIS`, `CLUSTER_RADIUS_DEGREES`, lines
31-32) — it does not merge across days. Three photos from Berlin taken on Jul 1, Jul 3, and Jul
10 are genuinely three separate clusters by that logic, each independently reverse-geocoded to
the same city. `JournalListScreen`'s render loop (lines 561-572) renders one full `SuggestionCard`
per cluster with no awareness of siblings, so the user sees three near-identical "New photos from
Berlin" cards back to back. This is working as designed underneath, but reads as a bug. Fix at
the presentation layer — group by `placeName` in the list, not in the scanner (leaves the
clustering/dismiss/accept data model untouched).

Small bonus bug fixed in the same change: `photo_suggestion_subtitle` renders "1 photos" for a
single-photo cluster — it's a plain string, not a `<plurals>` resource.

```
BEFORE (3 near-identical cards)          AFTER (1 grouped card)
┌───────────────────────────┐            ┌───────────────────────────┐
│ 📷 New photos from Berlin  │            │ 📷 New photos from Berlin  │
│ 1 photos, Jul 1             │            │ 3 visits                   │
│         [Dismiss] [Create]  │            │ 1 photo, Jul 1  [Dis][Cr] │
├───────────────────────────┤            │ 1 photo, Jul 3  [Dis][Cr] │
│ 📷 New photos from Berlin  │            │ 1 photo, Jul 10 [Dis][Cr] │
│ 1 photos, Jul 3             │            └───────────────────────────┘
│         [Dismiss] [Create]  │
├───────────────────────────┤
│ 📷 New photos from Berlin  │
│ 1 photos, Jul 10            │
│         [Dismiss] [Create]  │
└───────────────────────────┘
```

### 2a — Group the render loop by `placeName`

```kotlin
// ui/screens/JournalListScreen.kt — BEFORE (lines 561-572)

                        suggestedClusters.forEach { cluster ->
                            item(key = "suggestion-${cluster.startMillis}") {
                                SuggestionCard(
                                    cluster = cluster,
                                    onDismiss = { viewModel.dismissSuggestedCluster(cluster) },
                                    onCreate = {
                                        viewModel.acceptSuggestedCluster(cluster)
                                        onNewEntry()
                                    }
                                )
                            }
                        }
```

```kotlin
// ui/screens/JournalListScreen.kt — AFTER

                        // Clusters sharing a place name (e.g. two separate Berlin visits within
                        // the scan window) collapse into one grouped card instead of repeating
                        // near-identical cards back to back. Clusters with no resolved place name
                        // (geocoding failed/unavailable) always render individually — there's
                        // nothing to group them on. Accept/dismiss stay per-cluster either way;
                        // only the visual presentation changes.
                        val suggestionsByPlace = remember(suggestedClusters) {
                            suggestedClusters.groupBy { it.placeName }
                        }
                        suggestionsByPlace.forEach { (placeName, clustersForPlace) ->
                            if (placeName != null && clustersForPlace.size > 1) {
                                item(key = "suggestion-group-$placeName") {
                                    SuggestionGroupCard(
                                        placeName = placeName,
                                        clusters = clustersForPlace,
                                        onDismiss = { viewModel.dismissSuggestedCluster(it) },
                                        onCreate = {
                                            viewModel.acceptSuggestedCluster(it)
                                            onNewEntry()
                                        }
                                    )
                                }
                            } else {
                                clustersForPlace.forEach { cluster ->
                                    item(key = "suggestion-${cluster.startMillis}") {
                                        SuggestionCard(
                                            cluster = cluster,
                                            onDismiss = { viewModel.dismissSuggestedCluster(cluster) },
                                            onCreate = {
                                                viewModel.acceptSuggestedCluster(cluster)
                                                onNewEntry()
                                            }
                                        )
                                    }
                                }
                            }
                        }
```

New import: `androidx.compose.runtime.remember` (already imported, line 79) — no new import
needed for this half.

### 2b — New `SuggestionGroupCard` composable

Add next to `SuggestionCard` (~line 1116). Same card chrome (shape, colors, elevation) as the
existing single card, one header instead of three, then a compact per-visit row with its own
Dismiss/Create actions.

```kotlin
// ui/screens/JournalListScreen.kt — ADD (near SuggestionCard, line ~1171)

@Composable
private fun SuggestionGroupCard(
    placeName: String,
    clusters: List<PhotoRollScanner.PhotoCluster>,
    onDismiss: (PhotoRollScanner.PhotoCluster) -> Unit,
    onCreate: (PhotoRollScanner.PhotoCluster) -> Unit
) {
    val dayFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📷", fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.photo_suggestion_title, placeName),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                pluralStringResource(R.plurals.photo_suggestion_visit_count, clusters.size, clusters.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 22.dp, top = 2.dp)
            )
            Spacer(Modifier.height(6.dp))
            clusters.sortedBy { it.startMillis }.forEach { cluster ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(
                            R.string.photo_suggestion_subtitle,
                            pluralStringResource(
                                R.plurals.photo_suggestion_photo_count,
                                cluster.photoUris.size, cluster.photoUris.size
                            ),
                            dayFormat.format(Date(cluster.startMillis))
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { onDismiss(cluster) },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(stringResource(R.string.photo_suggestion_dismiss), style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = { onCreate(cluster) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.photo_suggestion_create), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
```

New import: `androidx.compose.foundation.layout.PaddingValues` (already imported, line 21).

### 2c — Fix "1 photos" and reuse the same fixed phrase in the single-card path

`photo_suggestion_subtitle`'s placeholder type changes from `%1$d` (raw count) to `%1$s` (a
pre-pluralized phrase built via the new `photo_suggestion_photo_count` plural) so both
`SuggestionCard` and `SuggestionGroupCard` share one correctly-pluralized phrase instead of each
hand-rolling "N photos".

```kotlin
// ui/screens/JournalListScreen.kt — BEFORE (SuggestionCard, ~line 1146-1149)

                    Text(
                        stringResource(
                            R.string.photo_suggestion_subtitle, cluster.photoUris.size, dateRange
                        ),
```

```kotlin
// ui/screens/JournalListScreen.kt — AFTER

                    Text(
                        stringResource(
                            R.string.photo_suggestion_subtitle,
                            pluralStringResource(
                                R.plurals.photo_suggestion_photo_count,
                                cluster.photoUris.size, cluster.photoUris.size
                            ),
                            dateRange
                        ),
```

**Files:** `ui/screens/JournalListScreen.kt`, `res/values/strings.xml` (×11 languages).

---

## Localization

```xml
<!-- strings.xml — CHANGE (placeholder type only, wording unchanged) -->
<!-- was: <string name="photo_suggestion_subtitle">%1$d photos, %2$s</string> -->
<string name="photo_suggestion_subtitle">%1$s, %2$s</string>

<!-- strings.xml — ADD -->
<plurals name="photo_suggestion_photo_count">
    <item quantity="one">%d photo</item>
    <item quantity="other">%d photos</item>
</plurals>
<plurals name="photo_suggestion_visit_count">
    <item quantity="one">%d visit</item>
    <item quantity="other">%d visits</item>
</plurals>
```

| Key | Change | EN value |
|-----|--------|----------|
| `photo_suggestion_subtitle` | placeholder type `%1$d` → `%1$s` (now receives an already-pluralized phrase, not a raw int) | `%1$s, %2$s` |
| `photo_suggestion_photo_count` | new plural | one: `%d photo` / other: `%d photos` |
| `photo_suggestion_visit_count` | new plural | one: `%d visit` / other: `%d visits` |

All 11 supported languages need the two new plurals added and `photo_suggestion_subtitle`
updated. The subtitle's translated *wording* shouldn't need to change (`%s` can still hold a
number-containing phrase) — only its format-spec character in each locale's XML.

---

## Scope

- **In:** narrowing the FAB's window-inset modifier; grouping suggestion cards by place name in
  the UI only; fixing the "1 photos" pluralization.
- **Out:** changing `PhotoRollScanner`'s clustering thresholds (`CLUSTER_GAP_MILLIS` /
  `CLUSTER_RADIUS_DEGREES`) to merge cross-day visits into one cluster — that would change what
  "Create entry" seeds (one `EntrySeed` per cluster, with a single `dateMillis` — see
  `JournalViewModel.acceptSuggestedCluster`), which is a bigger semantic change (what date does a
  multi-day merged entry get?) than this brief's presentation-only fix.
- **Out:** confirming the exact root cause of the S8's inflated `safeDrawing` value (IME vs.
  system-gesture insets) — not diagnosable without an on-device inset dump. The fix removes both
  suspects regardless of which one it actually was.

---

## Verification

1. On a device/emulator with a bottom nav bar in portrait: confirm the FAB still sits correctly
   above the nav bar (unchanged from today).
2. On the Galaxy A53 (or an emulator matching its landscape nav-bar-on-the-side layout): confirm
   the FAB still clears the nav bar — this must not regress the original fix.
3. On the Galaxy S8 (or any device that previously showed the FAB mid-screen): confirm it now
   renders at the bottom, clear of content.
4. Seed 3+ camera-roll photos in the same city across different days (>6h apart) and confirm they
   collapse into one `SuggestionGroupCard` with one header and three visit rows, each with working
   independent Dismiss/Create.
5. Confirm a single-cluster, single-place suggestion still renders as the plain `SuggestionCard`
   (group card only kicks in at 2+ clusters sharing a place).
6. Confirm a 1-photo cluster now reads "1 photo, Jul 1" (not "1 photos, Jul 1") in both the plain
   card and the grouped card's row.
7. Confirm dismissing or accepting one row inside a grouped card only removes that row/cluster,
   leaving siblings in place — same underlying dismiss/accept flow as before, just reached from a
   different visual container.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | FAB inset narrowed from `safeDrawing` to `navigationBars ∪ displayCutout` (fixes S8 over-padding, keeps A53 fix) | `JournalListScreen.kt` |
| 2 | Suggestion cards grouped by place name; new `SuggestionGroupCard` | `JournalListScreen.kt` |
| 3 | "1 photos" → "1 photo": new plurals, `photo_suggestion_subtitle` placeholder type change | `strings.xml` × 11 |
