# Macaco — Settings: Language Dialog Scrollable

The language picker `AlertDialog` lists 12 languages in a non-scrollable `Column`. In
landscape the dialog content overflows and the bottom entries (Português, Polski, Svenska,
日本語, 中文) are unreachable. The Cancel button (in the `confirmButton` slot) is always
visible — only the language list scrolls.

The fix is one modifier added to the Column. Smaller font or padding reduction are not
needed; scroll alone makes all 12 entries reachable without compromising readability.

`rememberScrollState` is already imported in `SettingsScreen.kt`.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/SettingsScreen.kt`

---

## Change — Make the language list Column scrollable (~line 160)

### BEFORE
```kotlin
        text = {
            Column {
                SUPPORTED_LANGUAGES.forEach { lang ->
```

### AFTER
```kotlin
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SUPPORTED_LANGUAGES.forEach { lang ->
```

No other changes. `rememberScrollState` is already imported.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `Modifier.verticalScroll(rememberScrollState())` to language list Column | `SettingsScreen.kt` |
