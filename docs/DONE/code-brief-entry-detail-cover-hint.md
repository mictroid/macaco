# Macaco — EntryDetailScreen: Photo long-press discoverability hint

Show a one-time contextual hint below the thumbnail strip telling users they can long-press
a photo to manage it. Appears up to 3 times total, then never again.
Touches `PreferencesManager.kt`, `JournalViewModel.kt`, and `EntryDetailScreen.kt`.

**Depends on:** `code-brief-photo-management.md` being implemented first — that brief adds
the `ModalBottomSheet` triggered by long-press (set as cover / move left / move right / remove).
This hint simply tells users the gesture exists.

Context: the long-press action added in `code-brief-photo-management.md` is invisible — users
have no way to discover it. This hint surfaces it exactly where and when it's relevant.

---

## Change 1 — Add hint counter to PreferencesManager

Add a persistent counter that tracks how many times the hint has been shown. Stops at 3.

```kotlin
// BEFORE (end of key declarations, around line 27):
private val KEY_APP_LOCK = booleanPreferencesKey("app_lock_enabled")

// AFTER:
private val KEY_APP_LOCK = booleanPreferencesKey("app_lock_enabled")
private val KEY_COVER_HINT_COUNT = intPreferencesKey("cover_hint_count")
```

Add the flow and suspend function alongside the other pairs:

```kotlin
val coverHintCount: Flow<Int> = context.dataStore.data
    .catch { emit(emptyPreferences()) }
    .map { prefs -> prefs[KEY_COVER_HINT_COUNT] ?: 0 }

suspend fun incrementCoverHintCount() {
    context.dataStore.edit { prefs ->
        prefs[KEY_COVER_HINT_COUNT] = (prefs[KEY_COVER_HINT_COUNT] ?: 0) + 1
    }
}
```

File: `PreferencesManager.kt`

---

## Change 2 — Show the hint in EntryDetailScreen

Below the thumbnail strip (and/or `AddPhotoTile` row), show a small animated hint label
when `coverHintCount < 3` and the entry has more than one photo. The hint auto-hides after
4 seconds and increments the counter each time it appears.

### Layout

```
┌─────────────────────────────────────────┐
│  [hero photo]        │  [thumb 2]       │
│                      ├──────────────────┤
│                      │  [thumb 3]       │
└──────────────────────┴──────────────────┘
┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──+─┐   ← thumbnail strip / overflow row
│  4   │ │  5   │ │  6   │ │  7   │ │    │
└──────┘ └──────┘ └──────┘ └──────┘ └────┘
  Long-press any photo to reorder or remove        ← hint line, fades out after 4s
```

### Implementation

Read the count in the composable (pass `preferencesManager` in via the ViewModel or
collect directly from `viewModel.coverHintCount`):

```kotlin
// Collect hint count — add near other state declarations at the top of EntryDetailScreen
val coverHintCount by viewModel.coverHintCount.collectAsState(initial = 0)
var showCoverHint by remember { mutableStateOf(false) }
```

Trigger the hint once per entry view (keyed on `entry.id` so it fires when the user swipes
to each new entry, not when `coverHintCount` increments — keying on the count would cause
the effect to restart immediately after incrementing, showing the hint three times in a row):

```kotlin
LaunchedEffect(entry.id) {
    if (entry.photoUris.size > 1 && coverHintCount < 3) {
        showCoverHint = true
        viewModel.incrementCoverHintCount()
        delay(4_000)
        showCoverHint = false
    }
}
```

Render the hint immediately after the thumbnail strip / overflow `LazyRow`:

```kotlin
AnimatedVisibility(
    visible = showCoverHint,
    enter = fadeIn(tween(300)),
    exit = fadeOut(tween(500))
) {
    Text(
        text = stringResource(R.string.entry_detail_cover_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
    )
}
```

File: `EntryDetailScreen.kt`

---

## Change 3 — Expose hint state from ViewModel

Add a thin pass-through in `JournalViewModel` so the screen can read and increment:

```kotlin
// In JournalViewModel:
val coverHintCount: Flow<Int> = preferencesManager.coverHintCount

fun incrementCoverHintCount() {
    viewModelScope.launch { preferencesManager.incrementCoverHintCount() }
}
```

File: `JournalViewModel.kt`

---

## String key

| Key | EN value |
|-----|----------|
| `entry_detail_cover_hint` | Long-press any photo to reorder or remove |

Localise across all 11 supported languages.

---

## Scope

- Hint shows only when the entry has 2+ photos.
- Hint shows at most 3 times across the entire lifetime of the app (not per entry).
- After 3 showings the hint never appears again, even on fresh entries.
- No dismiss button — it fades automatically after 4 seconds.
- No change to the long-press behaviour itself (already implemented).

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 