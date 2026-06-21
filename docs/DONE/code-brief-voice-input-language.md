# Brief: Fix Voice Input Language (Dictation Only Accepts English)

**Priority:** Medium  
**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/NewEditEntryScreen.kt`

## Problem

The `RecognizerIntent` for voice dictation in `NewEditEntryScreen` does not specify a language.
The speech recognition engine defaults to the device's primary system language, which on many
Android setups is English — even when the user has installed voice input for other languages.
The app supports 11 languages, so voice dictation should recognise speech in whatever language
the app is currently using.

## Change

Find the `RecognizerIntent` builder in `NewEditEntryScreen.kt`. It currently looks like:

```kotlin
val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.new_entry_dictate_prompt))
    // EXTRA_LANGUAGE is missing
}
```

Add the following two extras:

```kotlin
val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.new_entry_dictate_prompt))
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault().toLanguageTag())
}
```

Add the import at the top of the file if not already present:
```kotlin
import java.util.Locale
```

## Notes

- `Locale.getDefault()` returns the locale that `AppCompatDelegate.setApplicationLocales()` has set
  for the app, so it will match the in-app language the user selected in Settings.
- `EXTRA_LANGUAGE_PREFERENCE` is the preferred language; `EXTRA_LANGUAGE` is the required language.
  Using both maximises compatibility across recognition engine implementations.
- If the user's chosen voice input model does not support the requested language, the recognition
  engine may fall back to English or show an error — this is expected behaviour on the device side
  and not something we can control.
