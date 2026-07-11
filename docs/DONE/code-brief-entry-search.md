# Macaco — Search Across Entries

New feature: a search icon in the journal header opens a full-screen search over title,
description, location, tags, and trip name — the only way to find an old entry today is
scrolling or the tag filter. Entirely client-side over the existing `entries` StateFlow, no
backend change. Four files touched, one new: `data/model/TravelEntry.kt`,
`ui/screens/JournalListScreen.kt`, `ui/screens/SearchScreen.kt` (NEW),
`ui/navigation/Screen.kt`, `ui/navigation/NavGraph.kt`.

---

## Change 1 — Shared search-matching extension

**Problem:** no text search exists anywhere in the app — only the tag-filter chips and
trip/month grouping.

**Fix:** add a `List<TravelEntry>.matchingSearch(query)` extension next to the existing
`tagsByFrequency()` / `tripNames()` / `locations()` helpers, so the matching logic lives in one
place and is unit-testable the same way `tagsByFrequency()` already is (see
`TagsByFrequencyTest.kt`).

```kotlin
// data/model/TravelEntry.kt — ADD

/**
 * Entries whose title, description, location, tags, or trip name contain [query]
 * (case-insensitive, substring match). Blank query returns an empty list — the search screen
 * shows a placeholder rather than the whole journal, since "empty query, show everything" isn't
 * useful for a search screen the same way it is for the main list.
 */
fun List<TravelEntry>.matchingSearch(query: String): List<TravelEntry> {
    val q = query.trim()
    if (q.isBlank()) return emptyList()
    return filter { entry ->
        entry.title.contains(q, ignoreCase = true) ||
            entry.description.contains(q, ignoreCase = true) ||
            entry.location.contains(q, ignoreCase = true) ||
            entry.tags.any { it.contains(q, ignoreCase = true) } ||
            entry.tripName?.contains(q, ignoreCase = true) == true
    }.sortedByDescending { it.dateMillis }
}
```

**File:** `data/model/TravelEntry.kt`.

---

## Change 2 — Search icon in the journal header

**Problem:** there's no entry point into search anywhere in the UI.

**Fix:** add a magnifying-glass `IconButton` to both header layouts (portrait and landscape),
mirroring the avatar's placement so the header stays visually balanced. `JournalListScreen` gets
a new `onSearch: () -> Unit` parameter.

```
┌──────────────────────────────┐
│  🔍                      👤  │   ← portrait: search leading, avatar trailing
│           🐒 macaco           │
│         12 memories           │
└──────────────────────────────┘
```

```kotlin
// ui/screens/JournalListScreen.kt — BEFORE (function signature)
fun JournalListScreen(
    viewModel: JournalViewModel,
    onNewEntry: () -> Unit,
    onEntryClick: (String) -> Unit,
    onProfile: () -> Unit
) {

// AFTER
fun JournalListScreen(
    viewModel: JournalViewModel,
    onNewEntry: () -> Unit,
    onEntryClick: (String) -> Unit,
    onProfile: () -> Unit,
    onSearch: () -> Unit
) {
```

```kotlin
// ui/screens/JournalListScreen.kt — portrait header, BEFORE (~line 304)
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = macacoContentGutter()),
    verticalAlignment = Alignment.CenterVertically
) {
    Spacer(Modifier.size(40.dp))
    Spacer(Modifier.weight(1f))
    if (currentUser != null) {
        // ...avatar...
    }
}

// AFTER — leading spacer replaced with the search button, same 40dp footprint so the
// centred MacacoBrandBlock (drawn on top, TopCenter-aligned) stays centred either way.
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = macacoContentGutter()),
    verticalAlignment = Alignment.CenterVertically
) {
    IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
        Icon(
            Icons.Filled.Search,
            contentDescription = stringResource(R.string.search_action),
            tint = Color.White
        )
    }
    Spacer(Modifier.weight(1f))
    if (currentUser != null) {
        // ...avatar... (unchanged)
    }
}
```

```kotlin
// ui/screens/JournalListScreen.kt — landscape header, BEFORE (~line 260, the TopEnd avatar box)
Box(
    modifier = Modifier
        .align(Alignment.TopEnd)
        .size(40.dp)
        .padding(end = macacoContentGutter()),
    contentAlignment = Alignment.Center
) {
    if (currentUser != null) {
        // ...avatar...
    }
}

// AFTER — search button added to the same TopEnd corner, avatar unchanged; search moves to
// TopStart so the two controls don't crowd one corner in the shorter landscape header.
Box(
    modifier = Modifier
        .align(Alignment.TopStart)
        .size(40.dp)
        .padding(start = macacoContentGutter()),
    contentAlignment = Alignment.Center
) {
    IconButton(onClick = onSearch) {
        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_action), tint = Color.White)
    }
}
Box(
    modifier = Modifier
        .align(Alignment.TopEnd)
        .size(40.dp)
        .padding(end = macacoContentGutter()),
    contentAlignment = Alignment.Center
) {
    if (currentUser != null) {
        // ...avatar... (unchanged)
    }
}
```

New import: `androidx.compose.material.icons.filled.Search`.

Also drop the `private` modifier from `EntryCard` (line 665) — `SearchScreen.kt` reuses it
as-is so search results look identical to the main list, rather than a second implementation of
the same card.

**File:** `ui/screens/JournalListScreen.kt`.

---

## Change 3 — `SearchScreen` (NEW FILE)

Auto-focused text field, live-filtered as the user types, three states: not-yet-typed hint,
no-matches, and results (reusing `EntryCard`).

```kotlin
package com.houseofmmminq.macaco.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.data.model.matchingSearch
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: JournalViewModel,
    onEntryClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val cachedDrivePhotos by viewModel.cachedDrivePhotos.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val results = remember(entries, query) { entries.matchingSearch(query) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        when {
            query.isBlank() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Search, contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.search_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            results.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.search_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(results, key = { it.id }) { entry ->
                    EntryCard(
                        entry = entry,
                        cachedDrivePhotos = cachedDrivePhotos,
                        selectedTags = emptySet(),
                        onClick = { onEntryClick(entry.id) }
                    )
                }
            }
        }
    }
}
```

**File:** `ui/screens/SearchScreen.kt` (new).

---

## Change 4 — Navigation wiring

```kotlin
// ui/navigation/Screen.kt — ADD
object Search : Screen("search")
```

```kotlin
// ui/navigation/NavGraph.kt — ADD inside the NavHost
composable(Screen.Search.route) {
    SearchScreen(
        viewModel = viewModel,
        onEntryClick = { id ->
            navController.navigate(Screen.EntryDetail.createRoute(id))
        },
        onBack = { navController.popBackStack() }
    )
}
```

Update the `JournalList` composable block to pass the new callback:
`onSearch = { navController.navigate(Screen.Search.route) }`.

**File:** `ui/navigation/Screen.kt`, `ui/navigation/NavGraph.kt`.

---

## Localization

| Key | EN value |
|-----|----------|
| `search_action` | Search |
| `search_hint` | Search your entries… |
| `search_empty_hint` | Search by title, location, description, or tag |
| `search_no_results` | No entries match your search |

(`common_back` already exists.) All 11 languages need these four keys.

---

## Scope

- **In:** search across title, description, location, tags, trip name; reuses `EntryCard` for
  visual parity with the main list.
- **Out:** search history / recent searches, fuzzy or typo-tolerant matching (substring only, v1),
  and searching within Drive-only (not-yet-cached) photo captions or anything not already in
  `TravelEntry` — there's no separate text index, this filters the in-memory list you already
  have loaded.

---

## Verification

1. Tap the new search icon from both portrait and landscape journal headers; confirm the field
   auto-focuses and the keyboard opens immediately.
2. Type a query matching an entry's title, then clear it and try one matching only the
   description, then one matching only a tag, then a trip name — confirm each surfaces the right
   entry(ies).
3. Type a query with zero matches — confirm the no-results state, not a blank screen.
4. Tap a result — confirm it opens the same `EntryDetail` screen as tapping it from the main list.
5. Confirm the main journal list's own tag-filter (`selectedTags`) is untouched by this change —
   search passes `emptySet()` to `EntryCard` deliberately so it never shows tag highlighting from
   the main list's leftover filter state.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `matchingSearch()` extension | `TravelEntry.kt` |
| 2 | Search icon in portrait + landscape headers; `EntryCard` made reusable | `JournalListScreen.kt` |
| 3 | New `SearchScreen` | `SearchScreen.kt` (new) |
| 4 | New route + wiring | `Screen.kt`, `NavGraph.kt` |
| — | 4 new string keys × 11 languages | `strings.xml` × 11 |
