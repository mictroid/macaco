# Macaco — NewEditEntryScreen + JournalViewModel + PreferencesManager: Mood Selector Refresh

Two changes in one: (1) replace the preset mood list with a more expressive, travel-focused set,
and (2) let users add their own emoji moods via a "+" button that persists their custom moods
in DataStore.

---

## 1. Update preset MOODS list

**Problem:** Current list in `NewEditEntryScreen.kt` (line 95):

```kotlin
private val MOODS = listOf("😊", "🌟", "😎", "🏔️", "🌊", "🌺", "✨", "🎭", "🍜", "🏛️", "🌅", "❤️")
```

Mix of places (🏔️ 🌊 🏛️) and feelings — place emojis are ambiguous as moods. The set also
uses older/generic emoji that don't feel travel-specific.

**Fix:** Replace with feeling-first emoji that describe how you felt on a travel day, with
a couple of nature anchors kept for context. Adjust to taste before shipping.

**File:** `ui/screens/NewEditEntryScreen.kt`

```kotlin
private val MOODS = listOf(
    "🤩", // Amazed
    "😌", // Peaceful
    "🥳", // Celebrating
    "😍", // Loved it
    "🫶", // Grateful
    "🥹", // Moved / touched
    "🤠", // Adventurous
    "😤", // Challenged / tough day
    "😴", // Exhausted
    "🔥", // Thrilled / on fire
    "💫", // Magical
    "🌿", // In nature / grounded
)
```

**Backward compatibility:** `mood` is stored as the emoji string in Firestore. Existing entries
keep their old emoji and render fine everywhere — they just won't appear as selectable presets
in the new picker.

---

## 2. Persist custom moods in PreferencesManager

**Problem:** No mechanism to store user-defined moods.

**Fix:** Add a new DataStore key storing custom moods as a `|`-delimited string. Emoji never
contain `|`, so the delimiter is safe.

**File:** `data/PreferencesManager.kt`

Add after the existing keys:

```kotlin
private val KEY_CUSTOM_MOODS = stringPreferencesKey("custom_moods")

val customMoods: Flow<List<String>> = context.dataStore.data
    .catch { emit(emptyPreferences()) }
    .map { prefs ->
        prefs[KEY_CUSTOM_MOODS]
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

suspend fun addCustomMood(emoji: String) {
    context.dataStore.edit { prefs ->
        val current = prefs[KEY_CUSTOM_MOODS]
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        if (emoji !in current) {
            prefs[KEY_CUSTOM_MOODS] = (current + emoji).joinToString("|")
        }
    }
}
```

No new imports needed.

---

## 3. Expose customMoods + addCustomMood from JournalViewModel

**File:** `ui/viewmodel/JournalViewModel.kt`

Add alongside the other StateFlows:

```kotlin
val customMoods: StateFlow<List<String>> = preferencesManager.customMoods
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

fun addCustomMood(emoji: String) {
    viewModelScope.launch { preferencesManager.addCustomMood(emoji) }
}
```

---

## 4. Update MoodSelector to show custom moods + add button

**Problem:** `MoodSelector` only shows the fixed `MOODS` list. No way to add custom emoji.

**Fix:** Update the signature to accept `customMoods` and `onAddCustomMood`. Show presets first,
then any custom moods, then a "+" chip. Tapping "+" shows an `AlertDialog` with a text field
for entering an emoji.

```
[ 🤩 ][ 😌 ][ 🥳 ] … presets … [ 🦋 ][ custom ] … [ + ]
                                 ↑ user-added, stored in DataStore
```

**File:** `ui/screens/NewEditEntryScreen.kt`

Update the `MoodSelector` signature and body. Replace the existing composable entirely:

```kotlin
@Composable
private fun MoodSelector(
    selectedMood: String,
    customMoods: List<String>,
    onMoodSelected: (String) -> Unit,
    onAddCustomMood: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingEmoji by remember { mutableStateOf("") }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; pendingEmoji = "" },
            title = { Text(stringResource(R.string.mood_add_custom_title)) },
            text = {
                OutlinedTextField(
                    value = pendingEmoji,
                    onValueChange = { pendingEmoji = it },
                    placeholder = { Text(stringResource(R.string.mood_add_custom_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val emoji = pendingEmoji.trim()
                        if (emoji.isNotBlank()) {
                            onAddCustomMood(emoji)
                            onMoodSelected(emoji)
                        }
                        showAddDialog = false
                        pendingEmoji = ""
                    }
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; pendingEmoji = "" }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 8.dp)
    ) {
        // Preset moods
        items(MOODS) { m -> MoodChip(m, selectedMood == m) { onMoodSelected(m) } }

        // User-added custom moods
        items(customMoods) { m -> MoodChip(m, selectedMood == m) { onMoodSelected(m) } }

        // Add custom mood button — gold-tinted to match the selected chip style
        item {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SplashGold.copy(alpha = 0.18f))
                    .clickable { showAddDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.mood_add_custom_cd),
                    tint = SplashGold,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun MoodChip(emoji: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                // Selected: Macaco gold — consistent with splash, nav bar, and brand moments.
                // Unselected: neutral surface so the emoji reads clearly at rest.
                if (selected) SplashGold else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = 26.sp)
    }
}
// SplashGold / SplashGoldBright are internal to the ui/screens package (defined in SplashScreen.kt)
// and are already used in JournalListScreen, AppLockScreen, NavGraph — no new imports needed.
```

Add imports if missing:
```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
```

---

## 5. Update MoodSelector call site

**File:** `ui/screens/NewEditEntryScreen.kt` (around line 464–468)

Find:
```kotlin
MoodSelector(selectedMood = mood, onMoodSelected = { mood = it })
```

Replace with (add `val customMoods by viewModel.customMoods.collectAsState()` near the top of
the composable alongside other state collection):

```kotlin
val customMoods by viewModel.customMoods.collectAsState()

// … further down at the MoodSelector call site:
MoodSelector(
    selectedMood = mood,
    customMoods = customMoods,
    onMoodSelected = { mood = it },
    onAddCustomMood = { emoji -> viewModel.addCustomMood(emoji) }
)
```

---

## 6. New string keys (×11 languages)

| Key | EN value |
|-----|----------|
| `mood_add_custom_title` | Add your own mood |
| `mood_add_custom_placeholder` | Paste an emoji… |
| `mood_add_custom_cd` | Add custom mood |

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Replace `MOODS` list with refreshed feeling-first emoji set | `ui/screens/NewEditEntryScreen.kt` |
| 2 | Add `KEY_CUSTOM_MOODS`, `customMoods` flow, `addCustomMood()` | `data/PreferencesManager.kt` |
| 3 | Expose `customMoods: StateFlow` and `addCustomMood()` | `ui/viewmodel/JournalViewModel.kt` |
| 4 | Update `MoodSelector` to show custom moods + "+" add button with dialog; extract `MoodChip` | `ui/screens/NewEditEntryScreen.kt` |
| 5 | Update `MoodSelector` call site to pass `customMoods` and `onAddCustomMood` | `ui/screens/NewEditEntryScreen.kt` |
| 6 | Add 3 string keys | `res/values*/strings.xml` (×11) |
