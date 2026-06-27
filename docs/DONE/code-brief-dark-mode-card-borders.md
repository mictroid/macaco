# Macaco — JournalListScreen: Dark mode card borders

Adds a subtle `outlineVariant` border to entry cards and "On This Day" cards in dark themes.
Touches `JournalListScreen.kt` only.

---

## Background

In MD3 dark themes, `surface` and `background` share nearly identical dark-gray values. The
`2.dp` shadow elevation on `EntryCard` produces a tonal overlay of only ~8% white tint — too
subtle to create a visible card edge. Users see entries as frameless blocks that bleed into the
background.

The fix: apply a `0.5.dp` `outlineVariant` border exclusively in dark mode, detected via
`MaterialTheme.colorScheme.background.luminance() < 0.5f`. This works correctly across all 7
app themes (the check reads the *actual* rendered scheme, not the system flag) and leaves light
mode entirely unchanged.

---

## Change 1 — `EntryCard`: add dark-mode border

```kotlin
// BEFORE — EntryCard composable, Card block (line ~773)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {

// AFTER
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isDark) Modifier.border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
```

**New imports required:**
```kotlin
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.luminance
```

File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/JournalListScreen.kt`

---

## Change 2 — "On This Day" card: same treatment

The horizontal "On This Day" scroll cards (small 140dp-wide cards) have the same issue — they
use `surface` with only `1.dp` elevation.

```kotlin
// BEFORE — OnThisDayCard composable, Card block (line ~1008)
    Card(
        onClick = onClick,
        modifier = Modifier.width(140.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {

// AFTER
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(140.dp)
            .then(
                if (isDark) Modifier.border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp)
                ) else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
```

Note: the imports added in Change 1 cover this too — no additional imports needed.

File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/JournalListScreen.kt`

---

## Scope

- **In:** `EntryCard` and the "On This Day" small card. Both use `surface`/low-elevation combinations that vanish in dark themes.
- **Out:** The "On This Day dismissed" notice card (line ~936) uses `primaryContainer` as its background — that color is always visually distinct, no border needed.
- **No theme tokens invented.** `outlineVariant` is the standard MD3 token for this use case (subtle secondary borders and dividers).
- **No light-mode change.** The `isDark` guard keeps `Modifier` (i.e., no border) in all light-mode themes.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `0.5.dp outlineVariant` border to `EntryCard` in dark mode | `JournalListScreen.kt` |
| 2 | Same border on "On This Day" small card | `JournalListScreen.kt` |
