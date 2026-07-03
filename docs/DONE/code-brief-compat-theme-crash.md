# Macaco — MainActivity: AppCompat Theme Crash on Activity Recreation

Fixes `IllegalStateException: You need to use a Theme.AppCompat theme` crash introduced in
vc48. Touches one file: `MainActivity.kt`.

---

## Root cause

`installSplashScreen()` does two things:
1. Shows the animated splash (cold-start only — OS ignores it on recreations)
2. Calls `setTheme(postSplashScreenTheme)` to swap the activity theme from
   `Theme.Macaco.Splash` → `Theme.MyApplication`

The vc48 language-flash fix guarded this call with `if (savedInstanceState == null)` to
skip it on Activity recreations. The intent was to avoid a one-frame theme-swap flash.
But skipping `installSplashScreen()` on recreation also skips the theme swap — leaving the
Activity in `Theme.Macaco.Splash`, whose parent is `Theme.SplashScreen` (not AppCompat).
`AppCompatActivity.super.onCreate()` checks the theme and throws.

**Crash path:**
- User rotates the phone (orientation not yet in configChanges until rotation-crash.md ships)
- User changes language in Settings → `AppCompatDelegate.setApplicationLocales()` → `Activity.recreate()`
- Any other config change that recreates the Activity
- → `onCreate(savedInstanceState != null)` → guard skips `installSplashScreen()` → theme stays
  `Theme.Macaco.Splash` → `super.onCreate()` throws `IllegalStateException`

## Why removing the guard is now safe

The original reason to guard it was to prevent a one-frame black flash during recreation.
That flash was caused by `Theme.Macaco.Splash` having no `android:windowBackground`
(inheriting black from `Theme.SplashScreen`). The vc48 `language-switch-flash` brief already
fixed this by adding `android:windowBackground="@color/splash_background"` to
`Theme.Macaco.Splash`. Both themes now have the same teal windowBackground:

- `Theme.Macaco.Splash` → `android:windowBackground="@color/splash_background"` ✓ (added vc48)
- `Theme.MyApplication` → `android:windowBackground="@color/splash_background"` ✓ (was already set)

So the theme swap on recreation is now teal → teal — zero visible flash. The guard is no
longer needed and is actively causing the crash.

---

## Fix

```kotlin
// BEFORE (MainActivity.kt ~line 69)
override fun onCreate(savedInstanceState: Bundle?) {
    // System splash (teal brand colour + monkey) covers the cold-start window, then the
    // in-app Compose SplashScreen continues the branded poster. Only install it on a true
    // cold start: on a recreation (locale change, rotation) the OS doesn't show the splash
    // anyway, and installSplashScreen()'s postSplashScreenTheme swap causes a one-frame
    // black flash. The windowBackground on Theme.Macaco.Splash covers the gap otherwise.
    if (savedInstanceState == null) {
        installSplashScreen()
    }
    super.onCreate(savedInstanceState)

// AFTER — call unconditionally; the windowBackground fix (vc48) already prevents the flash
override fun onCreate(savedInstanceState: Bundle?) {
    // installSplashScreen() must run on every onCreate — it performs the theme swap from
    // Theme.Macaco.Splash (Theme.SplashScreen parent, non-AppCompat) to Theme.MyApplication
    // (AppCompat). Skipping it on recreations leaves a non-AppCompat theme active and
    // crashes AppCompatActivity. The one-frame flash that motivated the old guard is no longer
    // possible: both themes now share android:windowBackground=@color/splash_background (teal),
    // so the swap is visually seamless. On recreations the OS doesn't show the splash animation
    // regardless — installSplashScreen() just does the theme swap.
    installSplashScreen()
    super.onCreate(savedInstanceState)
```

No other changes required.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Remove `if (savedInstanceState == null)` guard from `installSplashScreen()` | `MainActivity.kt` |
