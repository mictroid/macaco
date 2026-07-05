# Macaco — Date Picker: Lock Out the Broken Calendar Grid in Landscape (v2)

Follow-up to `docs/DONE/code-brief-datepicker-landscape.md`, which made the date picker default
to `DisplayMode.Input` (month/day/year text fields) on short/landscape screens, since the M3
calendar grid overflows there (weekday header row overlaps the first date row, confirm/cancel
buttons overlap the last row — reproduced again on the A53 landscape). That fix only set the
*initial* mode — the M3 dialog still shows its built-in mode-toggle icon (pencil ↔ calendar) in
the header, which lets the user manually switch back to the grid at any time, immediately
reproducing the exact overflow the v1 fix was meant to prevent. This v2 hides that toggle when
the picker is in its landscape/short-screen state, so the grid mode is unreachable there.
Touches `NewEditEntryScreen.kt` only.

---

## Change: hide the mode-toggle icon in landscape so users can't switch back to the broken grid

**Problem:** `DatePicker(state = datePickerState, colors = pickerColors)` uses the default
`showModeToggle = true`, so the header's pencil/calendar icon is always visible and tappable. In
landscape, `datePickerState` is initialised to `DisplayMode.Input` (v1 fix), but the toggle icon
still lets the user switch the *live* display mode back to `Picker` (the calendar grid) — at which
point the same short-screen overflow from before reappears, since nothing constrains the grid's
rendered height to the dialog's available space. This is exactly what's shown in the report: a
grid view, with the weekday header (M T W T F S S) overlapping the first date row, and the
confirm/cancel row overlapping the last date row.

**Fix:** Pass `showModeToggle = !isDatePickerLandscape` to `DatePicker`. In landscape/short
screens, this removes the toggle icon entirely — the picker is locked into `DisplayMode.Input`, so
the broken grid state becomes unreachable. In portrait, behavior is fully unchanged (toggle stays
visible, both modes available, matching current behavior).

```
BEFORE (landscape — toggle still visible,      AFTER (landscape — toggle hidden,
user can switch back into the broken grid)     locked into the working Input mode)
┌────────────────────────────────────┐         ┌────────────────────────────────────┐
│ Jul 5, 2026                    ✎  │ ← tap    │ Jul 5, 2026                        │
├────────────────────────────────────┤   this   ├────────────────────────────────────┤
│ M  T  W  T  F  S  S                │   to     │  Month   Day    Year               │
│ [overlapping grid — the bug]       │   break  │  [ 07 ] [ 05 ] [ 2026 ]            │
└────────────────────────────────────┘   it     └────────────────────────────────────┘
```

### BEFORE (`NewEditEntryScreen.kt`, ~line 523)

```kotlin
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
```

### AFTER

```kotlin
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
            DatePicker(
                state = datePickerState,
                colors = pickerColors,
                // Hide the mode-toggle icon in landscape/short screens: the M3 calendar grid
                // overflows there (this is the v1 fix's initialDisplayMode = Input), but without
                // this the toggle icon still lets the user switch back into the broken grid.
                // Portrait is unaffected — toggle stays available, both modes still reachable.
                showModeToggle = !isDatePickerLandscape
            )
        }
```

`isDatePickerLandscape` is already computed above (line 265, from the v1 fix) — no new state, no
new imports.

**File:** `NewEditEntryScreen.kt`

---

## Out of scope / intentional

- Portrait is completely unchanged — `showModeToggle` stays `true`, users can still freely switch
  between calendar and text-input entry there.
- This doesn't attempt to make the grid itself fit a short landscape dialog (not practically
  possible — the grid layout is internal to M3's `DatePicker` and not sized by callers). Locking
  out the toggle is the M3-native way to avoid the broken state, matching the approach the v1
  brief already established.
- No new strings; no localization impact.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `DatePicker` gets `showModeToggle = !isDatePickerLandscape`, hiding the pencil/calendar toggle in landscape so the broken calendar-grid mode can no longer be manually re-entered | `NewEditEntryScreen.kt` |
