# Macaco — UI Polish: date picker brand colours + map landscape header alignment

Two files: `NewEditEntryScreen.kt` (date picker colours), `MapScreen.kt` (landscape header icon).

---

## Fix 1 — Date picker brand colours

**Problem:** The date picker dialog uses Material 3 defaults, which sets the dialog background
to `surfaceContainerHigh` — a light lavender/mauve that has no relationship to macaco's teal/gold
palette. The picker appears as a foreign system widget inside an otherwise branded app.

**Fix:** Pass `DatePickerDefaults.colors()` with overrides to both `DatePickerDialog` and
`DatePicker`. Use `surface` for the dialog background (clean white/dark surface that matches the
rest of the app), and `primary` (macaco teal) for the headline, weekday labels, selected day
circle, and today marker — the same teal accent used everywhere else.

```kotlin
// BEFORE (line ~282 in NewEditEntryScreen.kt)
if (showDatePicker) {
    DatePickerDialog(
        onDismissRequest = { showDatePicker = false },
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { dateMillis = it }
                showDatePicker = false
            }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.common_cancel)) }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

// AFTER — explicit brand colours passed to both the dialog shell and the picker
if (showDatePicker) {
    val pickerColors = DatePickerDefaults.colors(
        containerColor           = MaterialTheme.colorScheme.surface,
        headlineContentColor     = MaterialTheme.colorScheme.primary,
        weekdayContentColor      = MaterialTheme.colorScheme.primary,
        selectedDayContainerColor = MaterialTheme.colorScheme.primary,
        selectedDayContentColor  = MaterialTheme.colorScheme.onPrimary,
        todayContentColor        = MaterialTheme.colorScheme.primary,
        todayDateBorderColor     = MaterialTheme.colorScheme.primary,
    )
    DatePickerDialog(
        onDismissRequest = { showDatePicker = false },
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { dateMillis = it }
                showDatePicker = false
            }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.common_cancel)) }
        },
        colors = pickerColors
    ) {
        DatePicker(state = datePickerState, colors = pickerColors)
    }
}
```

**File:** `ui/screens/NewEditEntryScreen.kt`

---

## Fix 2 — Map landscape header: icon vertical alignment

**Problem:** In the landscape Adventures header, the macaco icon and "macaco" text sit in a
`Row` with `verticalAlignment = Alignment.CenterVertically`. The icon is `ic_launcher_foreground`
at 24dp — an adaptive icon foreground that carries ~18% transparent inset on all sides per the
Android adaptive icon spec, meaning the visible monkey content is only ~16dp tall inside the
24dp layout box. Row centering aligns layout boxes, so the visible icon center (at 12dp) is
~3–4dp below the text cap-height center (~7–8dp from top of the 14sp line box). On Samsung
devices like the A53, font metrics can widen this gap further.

**Fix:** Reduce the image to 20dp and add a small upward offset so the visible icon content
visually aligns with the text. The `offset(y = (-2).dp)` shifts draw position without affecting
layout bounds, which keeps the Row's touch and spacing unchanged.

```
Before:  [   icon 24dp   ] macaco · Adventures · ...
          ↑ visible ~16dp           ↑ cap height ~10dp
          centres don't match (icon sits ~4dp low)

After:   [ icon 20dp ↑2dp ] macaco · Adventures · ...
          ↑ visible ~13dp   ↑ cap height ~10dp
          visual centres aligned
```

```kotlin
// BEFORE (line ~344 in MapScreen.kt, landscape Row)
Image(
    painter = painterResource(R.drawable.ic_launcher_foreground),
    contentDescription = null,
    modifier = Modifier.size(24.dp)
)

// AFTER
Image(
    painter = painterResource(R.drawable.ic_launcher_foreground),
    contentDescription = null,
    modifier = Modifier
        .size(20.dp)
        .offset(y = (-2).dp)
)
```

The portrait layout (`Modifier.size(44.dp).offset(y = 4.dp)`) is a separate composable branch
and is unchanged.

**File:** `ui/screens/MapScreen.kt`

---

## Scope

- **In:** Date picker background + accent colours; landscape map header icon size and alignment.
- **Out:** Date picker typography, shape, or interaction behaviour changes.
- **Out:** Portrait map header layout — correct as-is.
- **Out:** Dark mode date picker — the `surface` and `primary` tokens adapt automatically.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Date picker uses `surface` container + `primary` accents via `DatePickerDefaults.colors()` | `NewEditEntryScreen.kt` |
| 2 | Landscape map header icon: `size(24dp)` → `size(20dp).offset(y = -2dp)` | `MapScreen.kt` |
