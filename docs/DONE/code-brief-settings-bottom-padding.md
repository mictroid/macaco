# Macaco — Settings: Bottom Navigation Bar Padding

On phones using gesture navigation (e.g. Samsung A53), the system navigation bar overlaps the
bottom of the Settings scrollable content, cutting off the Version row. The fix is one line:
add `navigationBarsPadding()` to the scrollable column so content scrolls above the nav bar.

---

## Fix

```kotlin
// BEFORE — SettingsScreen.kt (line 379)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

// AFTER
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
```

**File:** `ui/screens/SettingsScreen.kt`

---

## Import

`navigationBarsPadding` is not yet imported in this file:

```kotlin
// BEFORE — SettingsScreen.kt imports (line 28, after statusBarsPadding)
import androidx.compose.foundation.layout.statusBarsPadding

// AFTER
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
```

**File:** `ui/screens/SettingsScreen.kt`

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `navigationBarsPadding` import | `ui/screens/SettingsScreen.kt` |
| 2 | Add `.navigationBarsPadding()` to scrollable Column | `ui/screens/SettingsScreen.kt` |

No string resources, no ViewModel changes, no other files.
