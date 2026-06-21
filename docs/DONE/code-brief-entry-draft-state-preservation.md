# Macaco — New/Edit Entry: Preserve Draft State Across Phone Lock / Process Death

When the phone screen locks, Android may kill the app process under memory pressure. On unlock
the Activity is recreated, but all form fields in `NewEditEntryScreen` use `remember { mutableStateOf(...) }`
— which is wiped on process death — so the user loses everything they typed.

The fix is to replace `remember` with `rememberSaveable` for every form field. `rememberSaveable`
serialises state into the Activity's saved-instance Bundle, which Android preserves through
process death and restores when the user returns from the lock screen.

Touches: `NewEditEntryScreen.kt` only.

---

## Background: what survives what

| Scenario | `remember` | `rememberSaveable` |
|---|---|---|
| Recomposition | ✓ | ✓ |
| Rotation / config change | ✗ | ✓ |
| Phone lock → process killed → unlock | ✗ | ✓ |
| User swipes app out of recents | ✗ | ✗ (expected — user chose to close) |

`enableEdgeToEdge()` is already set in `MainActivity` and `rememberSaveable` is already
imported and used elsewhere in the nav layer (`NavGraph.kt`), so no new dependencies are needed.

---

## 1. Primitive fields — swap remember → rememberSaveable

`String`, `Boolean`, and `Long` are auto-saved by the Bundle without any custom saver.

```kotlin
// NewEditEntryScreen.kt — replace ALL of these:

// BEFORE:
var title       by remember { mutableStateOf(existingEntry?.title ?: "") }
var location    by remember { mutableStateOf(existingEntry?.location ?: "") }
var mood        by remember { mutableStateOf(existingEntry?.mood ?: "") }
var description by remember { mutableStateOf(existingEntry?.description ?: "") }
var tripName    by remember { mutableStateOf(existingEntry?.tripName ?: "") }
var titleError  by remember { mutableStateOf(false) }

// AFTER:
var title       by rememberSaveable { mutableStateOf(existingEntry?.title ?: "") }
var location    by rememberSaveable { mutableStateOf(existingEntry?.location ?: "") }
var mood        by rememberSaveable { mutableStateOf(existingEntry?.mood ?: "") }
var description by rememberSaveable { mutableStateOf(existingEntry?.description ?: "") }
var tripName    by rememberSaveable { mutableStateOf(existingEntry?.tripName ?: "") }
var titleError  by rememberSaveable { mutableStateOf(false) }
```

---

## 2. Date — keep mutableLongStateOf but wrap in rememberSaveable

`Long` is Bundle-serialisable. Switch from `mutableLongStateOf` inside `remember` to
`mutableStateOf<Long>` inside `rememberSaveable` (both read identically at the call site):

```kotlin
// BEFORE:
var dateMillis by remember { mutableLongStateOf(existingEntry?.dateMillis ?: System.currentTimeMillis()) }

// AFTER:
var dateMillis by rememberSaveable { mutableStateOf(existingEntry?.dateMillis ?: System.currentTimeMillis()) }
```

`rememberDatePickerState` already uses `rememberSaveable` internally, so the picker dialog's
own state is already preserved — this change only affects the committed `dateMillis` value.

---

## 3. List<String> fields — use a listSaver

`List<String>` is not directly Bundle-serialisable, so it needs a custom `Saver`. Add this
private val at the top of the file (after the imports, before the `MOODS` list):

```kotlin
// NewEditEntryScreen.kt — add near top of file, after imports:

private val StringListSaver = listSaver<List<String>, String>(
    save    = { it },
    restore = { it }
)
```

Then use it for `photoUris` and `tags`:

```kotlin
// BEFORE:
var photoUris by remember { mutableStateOf(existingEntry?.photoUris ?: emptyList()) }
var tags      by remember { mutableStateOf(existingEntry?.tags ?: emptyList()) }

// AFTER:
var photoUris by rememberSaveable(stateSaver = StringListSaver) {
    mutableStateOf(existingEntry?.photoUris ?: emptyList())
}
var tags by rememberSaveable(stateSaver = StringListSaver) {
    mutableStateOf(existingEntry?.tags ?: emptyList())
}
```

Import needed (add to imports if not already present):
```kotlin
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
```

---

## 4. sessionAdded — use a set-aware listSaver

`sessionAdded` tracks photos copied into storage this session so they can be cleaned up if the
user cancels. If this set is lost on process death, those gallery files become orphaned. Save it
using a second saver that round-trips through `List`:

```kotlin
// NewEditEntryScreen.kt — add near top of file alongside StringListSaver:

private val StringSetSaver = listSaver<Set<String>, String>(
    save    = { it.toList() },
    restore = { it.toSet() }
)
```

```kotlin
// BEFORE:
var sessionAdded by remember { mutableStateOf(emptySet<String>()) }

// AFTER:
var sessionAdded by rememberSaveable(stateSaver = StringSetSaver) {
    mutableStateOf(emptySet())
}
```

---

## 5. Leave ephemeral UI state as remember

These control transient dialog/picker visibility and don't need to survive process death
(the user would be confused to return to an open dialog after unlocking the phone):

```kotlin
// Leave these unchanged — keep as remember:
var showDatePicker        by remember { mutableStateOf(false) }
var showPhotoSourceDialog by remember { mutableStateOf(false) }
var pendingCameraUri      by remember { mutableStateOf<android.net.Uri?>(null) }
```

---

## Scope note

This fix covers both new entries (the priority, per the product decision) and edit entries
(unsaved edits are also restored, which is a bonus). For edit entries, the original Firestore
data is never at risk — only the in-progress unsaved changes benefit from this change.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `StringListSaver` + `StringSetSaver` private vals | `ui/screens/NewEditEntryScreen.kt` |
| 2 | `title`, `location`, `mood`, `description`, `tripName`, `titleError` → `rememberSaveable` | `ui/screens/NewEditEntryScreen.kt` |
| 3 | `dateMillis` → `rememberSaveable { mutableStateOf<Long>(...) }` | `ui/screens/NewEditEntryScreen.kt` |
| 4 | `photoUris`, `tags` → `rememberSaveable(stateSaver = StringListSaver)` | `ui/screens/NewEditEntryScreen.kt` |
| 5 | `sessionAdded` → `rememberSaveable(stateSaver = StringSetSaver)` | `ui/screens/NewEditEntryScreen.kt` |
