# Brief: Fix Blank Screen After Deleting an Entry (v2)

**Priority:** High  
**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/navigation/NavGraph.kt`

## Problem

After deleting an entry from `EntryDetailScreen`, the app returns to a completely blank
`JournalListScreen`. This is a regression — the previous fix (pop first, delete second) was
designed to prevent double-popping, but on slower devices (S8, Android 8.1) it still triggers.

## Root cause

The NavGraph composable for `EntryDetail` contains both:
1. An `onDelete` handler that calls `navController.popBackStack()` then `viewModel.deleteEntry()`
2. A guard that calls `LaunchedEffect(Unit) { navController.popBackStack() }` when
   `entries.none { it.id == id }`

The intent was: pop first so the composable is removed from the back stack before Firestore
propagates the deletion. But Compose recomposition is asynchronous — the `EntryDetail` composable
remains alive for at least one more recomposition frame after `popBackStack()` is called (exit
animation + Compose's scheduling). On some devices Firestore's local cache propagates the deletion
within that window, triggering the guard's `LaunchedEffect` → a **second** `popBackStack()`. With
two pops, navigation falls past `JournalList` (the NavHost root), resulting in a blank screen.

## Fix

Add an `isBeingDeleted` flag in the `EntryDetail` composable lambda. Set it to `true` before
calling `popBackStack()` in `onDelete`. The guard checks this flag and skips the backup pop if
deletion is already in progress.

### In `NavGraph.kt`, inside the `composable(route = Screen.EntryDetail.route)` block:

```kotlin
composable(
    route = Screen.EntryDetail.route,
    arguments = listOf(navArgument("entryId") { type = NavType.StringType })
) { backStackEntry ->
    val id = backStackEntry.arguments?.getString("entryId") ?: return@composable
    val entries by viewModel.visibleEntries.collectAsState()
    val cachedDrivePhotos by viewModel.cachedDrivePhotos.collectAsState()

    // NEW: tracks that onDelete has already fired a popBackStack(),
    // so the entries-change guard below doesn't fire a second one.
    var isBeingDeleted by remember { mutableStateOf(false) }

    if (!isBeingDeleted && entries.none { it.id == id }) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return@composable
    }

    EntryDetailScreen(
        entries = entries,
        initialEntryId = id,
        onEdit = { entryId -> navController.navigate(Screen.EditEntry.createRoute(entryId)) },
        onDelete = { entryId ->
            isBeingDeleted = true          // NEW: block the guard before popping
            navController.popBackStack()
            viewModel.deleteEntry(entryId)
        },
        onBack = { navController.popBackStack() },
        onTagClick = { tag ->
            viewModel.setTagFilter(tag)
            navController.popBackStack(Screen.JournalList.route, inclusive = false)
        },
        onSaveEntry = { viewModel.saveEntry(it) },
        onSuppressAutoLock = { viewModel.suppressAutoLockOnce() },
        cachedDrivePhotos = cachedDrivePhotos
    )
}
```

The only changes from the current code are:
1. `var isBeingDeleted by remember { mutableStateOf(false) }` — new state variable
2. `if (!isBeingDeleted && entries.none { it.id == id })` — guard now checks flag
3. `isBeingDeleted = true` — set before `popBackStack()` in `onDelete`

No other files need to change.

## Why this is safe

- `isBeingDeleted` is scoped to the composable lambda, so it resets to `false` the next time
  `EntryDetailScreen` is opened (new composable instance).
- Setting it to `true` does not prevent the `LaunchedEffect` from running in genuine "entry
  disappeared from another device" scenarios — those scenarios don't go through `onDelete`, so
  `isBeingDeleted` stays `false` and the guard fires normally.
