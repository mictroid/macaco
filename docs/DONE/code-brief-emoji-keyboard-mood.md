# Macaco — MoodSelector: Auto-open emoji keyboard when adding a custom mood

When the user taps the "+" button to add a custom mood, an `AlertDialog` appears with an
`OutlinedTextField`. The keyboard does not open automatically, so the user must tap the field
manually before they can type or switch to the emoji panel. Fix: auto-focus the field so the
keyboard opens the moment the dialog appears.

---

## What to change

**File:** wherever `MoodSelector` is defined (the composable called from `NewEditEntryScreen`
at `ui/screens/NewEditEntryScreen.kt` line 483).

Inside the `AlertDialog` that collects the custom emoji input, add a `FocusRequester` to the
`OutlinedTextField` and request focus via `LaunchedEffect` when the dialog opens:

```kotlin
// Add at the top of the composable scope that contains the AlertDialog:
val focusRequester = remember { FocusRequester() }

// On the OutlinedTextField, add the focusRequester modifier and set keyboard type:
OutlinedTextField(
    value = input,
    onValueChange = { ... },
    placeholder = { Text(stringResource(R.string.mood_add_custom_placeholder)) },
    modifier = Modifier
        .fillMaxWidth()
        .focusRequester(focusRequester),
    keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Done
    ),
    keyboardActions = KeyboardActions(onDone = { /* confirm */ }),
    singleLine = true
)

// Immediately below the OutlinedTextField (still inside the dialog content):
LaunchedEffect(Unit) {
    focusRequester.requestFocus()
}
```

`requestFocus()` triggers the keyboard as soon as the dialog enters composition. On Gboard and
Samsung Keyboard the user can then switch to the emoji panel via the smiley/emoji button on the
keyboard — no second tap needed.

---

## Required imports (add if missing)

```kotlin
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
```

---

## Summary

| File | Change |
|------|--------|
| Wherever `MoodSelector` is defined | Add `FocusRequester` + `LaunchedEffect(Unit) { focusRequester.requestFocus() }` to auto-open the keyboard when the custom mood `AlertDialog` appears |
