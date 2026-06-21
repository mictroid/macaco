# Macaco — NavGraph: Fix blank screen after deleting an entry

Deleting an entry from `EntryDetailScreen` leaves the app showing a blank screen with only the
bottom nav bar. The back stack is popped twice — once by the `LaunchedEffect` that fires when the
entry disappears from `visibleEntries`, and once by the explicit `popBackStack()` in `onDelete`.
The second pop removes `JournalListScreen` from the stack.

---

## Root cause

**File:** `ui/navigation/NavGraph.kt`

```kotlin
// ~line 244
if (entries.none { it.id == id }) {
    LaunchedEffect(Unit) { navController.popBackStack() }   // ← pop #1
    return@composable
}
EntryDetailScreen(
    ...
    onDelete = { entryId ->
        viewModel.deleteEntry(entryId)      // Firestore local cache updates immediately
        navController.popBackStack()         // ← pop #2, but pop #1 already fired
    },
```

`deleteEntry` writes to Firestore. The local cache reflects the deletion so fast that
`visibleEntries` emits a list without the deleted entry **before** `popBackStack()` in `onDelete`
runs. The `LaunchedEffect` triggers first, popping back to `JournalListScreen`. Then `onDelete`'s
own `popBackStack()` pops `JournalListScreen` off the stack too → blank screen.

---

## Fix

Swap the two lines in `onDelete` so navigation happens **before** the delete write:

```kotlin
onDelete = { entryId ->
    navController.popBackStack()    // navigate away first — composable leaves the stack
    viewModel.deleteEntry(entryId)  // Firestore write happens after; LaunchedEffect can't fire
},
```

Once `EntryDetailScreen` is off the back stack its composable is disposed, so the `LaunchedEffect`
can no longer trigger a second pop regardless of when Firestore confirms the deletion.

---

## Summary

| File | Change |
|------|--------|
| `ui/navigation/NavGraph.kt` | In the EntryDetail composable's `onDelete` lambda, call `navController.popBackStack()` before `viewModel.deleteEntry(entryId)` |
