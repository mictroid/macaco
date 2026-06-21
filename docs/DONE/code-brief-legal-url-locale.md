# Macaco — AppActions: Pass Current Locale to Legal URLs

Both the Terms of Service and Privacy Policy pages are now multilingual HTML files that accept
a `?lang=` URL parameter to show the user's language. This brief wires the app's current locale
into those links. Touches only `util/AppActions.kt`.

---

## Add locale-aware URL helper and update legal link calls

**Problem:** `AppActions.PRIVACY_POLICY_URL` and `AppActions.TERMS_URL` are opened as plain
strings with no language parameter. The new HTML pages auto-detect the browser language, but
inside Android WebView / Chrome Custom Tabs the browser language may differ from the app's
selected language (set via `AppCompatDelegate`). Passing `?lang=` explicitly ensures the page
always matches the in-app language setting.

**Fix:** Add a private helper `legalUrl(baseUrl, context)` that appends the current locale tag
as a `?lang=` parameter, then update every call site that opens a legal URL to use it.

### 1. Add helper in AppActions.kt

Find the `object AppActions` body and add after the existing constants:

```kotlin
/**
 * Appends the current app locale as a ?lang= query parameter so the multilingual
 * legal pages (ToS / Privacy Policy) open in the user's chosen language.
 *
 * Uses AppCompatDelegate.getApplicationLocales() so it reflects the in-app language
 * setting rather than the system locale. Falls back to Locale.getDefault().language
 * if no explicit app language is set. Both return BCP 47 tags; we use only the primary
 * subtag (e.g. "de" from "de-DE") to match the HTML page's supported lang codes.
 */
private fun legalUrl(baseUrl: String, context: Context): String {
    val locales = AppCompatDelegate.getApplicationLocales()
    val lang = if (locales.isEmpty) {
        Locale.getDefault().language
    } else {
        locales[0]?.language ?: Locale.getDefault().language
    }
    // Supported lang codes in the HTML pages:
    // en, de, fr, es, it, nl, pt, pl, sv, ja, zh
    // Any unsupported code falls back to English inside the page itself — safe to pass anything.
    return "$baseUrl?lang=$lang"
}
```

Required imports (add if not already present):
```kotlin
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale
```

### 2. Update every call that opens a legal URL

There are two call sites to update. Both are inside `AppActions.kt` itself or the screens that
call `AppActions.openUrl(context, ...)`.

Search the entire project for `PRIVACY_POLICY_URL` and `TERMS_URL` and update each call:

```kotlin
// BEFORE — wherever PRIVACY_POLICY_URL is passed to openUrl:
AppActions.openUrl(context, AppActions.PRIVACY_POLICY_URL)

// AFTER:
AppActions.openUrl(context, AppActions.legalUrl(AppActions.PRIVACY_POLICY_URL, context))
```

```kotlin
// BEFORE — wherever TERMS_URL is passed to openUrl:
AppActions.openUrl(context, AppActions.TERMS_URL)

// AFTER:
AppActions.openUrl(context, AppActions.legalUrl(AppActions.TERMS_URL, context))
```

Because `legalUrl` is `private` inside the object, if any call site is in a *different* file
(e.g. `HelpAboutScreen.kt` or `LoginScreen.kt`), change the visibility to `internal`:

```kotlin
internal fun legalUrl(baseUrl: String, context: Context): String { ... }
```

Call sites in other files then become:
```kotlin
AppActions.legalUrl(AppActions.PRIVACY_POLICY_URL, context)
AppActions.legalUrl(AppActions.TERMS_URL, context)
```

### Known call sites (verify with a project-wide search)

| Location | Which URL |
|----------|-----------|
| `HelpAboutScreen.kt` — Privacy Policy row | `PRIVACY_POLICY_URL` |
| `HelpAboutScreen.kt` — Terms of Service row | `TERMS_URL` |
| `LoginScreen.kt` — ToS/PP acknowledgement links | both |

---

## Scope notes

- No new strings needed — this is a URL parameter change only.
- The HTML pages already handle unknown lang codes by falling back to English, so passing an
  unsupported locale (e.g. "ca") is safe.
- `legalUrl` does not add a `?lang=` parameter if `lang` is empty; add a guard if needed:
  ```kotlin
  return if (lang.isBlank()) baseUrl else "$baseUrl?lang=$lang"
  ```

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `legalUrl(baseUrl, context)` helper (internal visibility) | `util/AppActions.kt` |
| 2 | Update all `openUrl(context, PRIVACY_POLICY_URL)` calls to use `legalUrl` | `HelpAboutScreen.kt`, `LoginScreen.kt` (verify with search) |
| 3 | Update all `openUrl(context, TERMS_URL)` calls to use `legalUrl` | `HelpAboutScreen.kt`, `LoginScreen.kt` (verify with search) |
