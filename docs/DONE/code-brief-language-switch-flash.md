# Macaco — themes.xml + MainActivity: black flash on first language switch

Two files: `res/values/themes.xml` and `app/src/main/java/.../MainActivity.kt`.

---

## The problem

When the user switches language for the first time in a session, the screen flashes black
briefly. Subsequent switches have no flash.

The root cause is a gap in theme inheritance. The Activity's manifest theme is
`Theme.Macaco.Splash`. This theme does NOT define `android:windowBackground` — it only
defines `windowSplashScreenBackground`. So `android:windowBackground` is inherited from the
`Theme.SplashScreen` parent, which evaluates to black/transparent in dark mode.

`Theme.MyApplication` (the `postSplashScreenTheme`) already sets
`android:windowBackground = @color/splash_background` — the comment in themes.xml says so
explicitly. But that theme only takes effect AFTER `installSplashScreen()` completes its
setup and applies `postSplashScreenTheme`. In the brief window between Activity creation
and that theme swap, `Theme.Macaco.Splash` is active and its inherited black windowBackground
shows through.

Why only the first switch? On cold JVM (first Recreation after a long app session),
the gap between window creation and the first Compose frame is slightly longer, making the
black window visible. On subsequent switches (warm JVM, seconds apart), the transition is
fast enough that the flash falls below the perception threshold.

---

## Fix 1 — Add `windowBackground` to `Theme.Macaco.Splash`

Match the `android:windowBackground` already present in `Theme.MyApplication`.
The same `@color/splash_background` (#0A4A58 dark teal) is the right value — it's the
brand colour that both themes should show during any transition.

```xml
<!-- BEFORE (res/values/themes.xml) -->
<style name="Theme.Macaco.Splash" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@color/splash_background</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>
    <item name="postSplashScreenTheme">@style/Theme.MyApplication</item>
</style>

<!-- AFTER — add the same windowBackground that Theme.MyApplication already has -->
<style name="Theme.Macaco.Splash" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@color/splash_background</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>
    <item name="postSplashScreenTheme">@style/Theme.MyApplication</item>
    <item name="android:windowBackground">@color/splash_background</item>
</style>
```

This ensures the window shows teal (#0A4A58) — not black — during the brief gap between
Activity creation and the first Compose frame on every recreate, including locale changes.

**File:** `res/values/themes.xml`

---

## Fix 2 — Skip `installSplashScreen()` on Activity recreation

`installSplashScreen()` is called unconditionally in `MainActivity.onCreate()`. On a cold
start (first launch from the launcher), this is correct — it shows the branded splash.
On an Activity recreation triggered by a locale change (or rotation), the visual splash
is not shown by the OS, but `installSplashScreen()` still runs setup code including the
`postSplashScreenTheme` transition. This transition itself involves a window background
update that can introduce a single-frame flicker.

**Fix:** Only call `installSplashScreen()` on a true cold start (when `savedInstanceState`
is null). On recreations, skip it — `postSplashScreenTheme` has already been applied
in the previous Activity instance, and Fix 1's `windowBackground` covers the window until
Compose renders.

```kotlin
// BEFORE (MainActivity.kt, line ~72)
override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    ...

// AFTER — only install the splash on a fresh cold start
override fun onCreate(savedInstanceState: Bundle?) {
    // installSplashScreen() shows the branded teal+monkey splash on cold start.
    // Skip it on recreations (locale change, rotation) — the splash isn't shown by the
    // OS in those cases anyway, and calling it causes an unnecessary theme-swap frame.
    // Fix 1 (android:windowBackground in Theme.Macaco.Splash) covers the window gap.
    if (savedInstanceState == null) {
        installSplashScreen()
    }
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    ...
```

**Note:** On a recreation, `postSplashScreenTheme` is still applied automatically by
the system because the Activity was already running under `Theme.MyApplication` before the
recreation. The `setTheme()` call that `installSplashScreen()` normally does is not needed
again.

**File:** `app/src/main/java/com/houseofmmminq/macaco/MainActivity.kt`

---

## Scope

- **In:** Black window flash during first locale change recreation.
- **Out:** The in-app Compose SplashScreen (the branded poster shown in `NavGraph`) —
  unchanged.
- **Out:** Cold-start splash (the system teal+monkey splash) — still shown on first launch.
- **Out:** `NavGraph`'s loading gates (`isPurchased == null` blank box already has
  `.background(MaterialTheme.colorScheme.background)` — correct and unchanged).

---

## Verification

1. Set the app to dark mode.
2. Go to Settings → Language → change to any language. First switch: no black flash —
   window should show teal (#0A4A58) during transition, then immediately the app content.
3. Change to another language. Also no flash.
4. Force-stop the app, reopen → cold-start splash (teal + monkey icon) should still appear
   as before.
5. Rotate the device on the journal screen → no black flash on rotation recreation either.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `android:windowBackground="@color/splash_background"` to `Theme.Macaco.Splash` | `res/values/themes.xml` |
| 2 | Guard `installSplashScreen()` with `if (savedInstanceState == null)` | `MainActivity.kt` |
