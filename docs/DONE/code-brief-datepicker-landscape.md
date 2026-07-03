# Macaco — Date Picker: Input Mode in Landscape

In landscape on A53 and S8, the M3 calendar grid is too tall for the dialog — the weekday
header row (M T W T F S S) overlaps the first row of dates. Reducing the internal padding is
not possible (it's buried inside M3's `DatePicker` composable). The M3-native solution is to
initialise the picker in `DisplayMode.Input` when the screen height is compact: this replaces
the calendar grid with month/day/year text fields that fit comfortably in any landscape dialog.
The user can still switch back to the calendar via the pencil icon in the dialog header.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/NewEditEntryScreen.kt`

---

## Change 1 — Add DisplayMode import (~line 65)

`DisplayMode` is in the same `material3` package as the other date-picker imports.

```kotlin
// ADD after the existing rememberDatePickerState import:
import androidx.compose.material3.DisplayMode
```

---

## Change 2 — Initialise picker state with landscape-aware display mode (~line 183)

`LocalConfiguration` is already imported. `screenHeightDp < 480` is the existing Macaco
threshold for "landscape on phone" (matches the rest of the app).

### BEFORE
```kotlin
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
```

### AFTER
```kotlin
    val isDatePickerLandscape = LocalConfiguration.current.screenHeightDp < 480
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dateMillis,
        initialDisplayMode = if (isDatePickerLandscape) DisplayMode.Input else DisplayMode.Picker,
    )
```

No other changes required. The `DatePickerDialog`, `DatePicker`, and `pickerColors` blocks
are all unchanged — `DisplayMode.Input` is rendered by the same composable automatically.

---

## Behaviour

| Mode | Display |
|---|---|
| Portrait (screenHeightDp ≥ 480) | Calendar grid, unchanged |
| Landscape (screenHeightDp < 480) | Month/Day/Year text fields — no grid overflow |

The pencil ↔ calendar toggle icon in the M3 dialog header lets the user switch modes manually
if they prefer the grid even in landscape. No UX regression: portrait is completely unchanged.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `import androidx.compose.material3.DisplayMode` | `NewEditEntryScreen.kt` |
| 2 | `rememberDatePickerState` gains `initialDisplayMode` based on screen height | `NewEditEntryScreen.kt` |
