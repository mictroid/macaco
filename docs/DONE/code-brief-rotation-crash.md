# Macaco — AndroidManifest: Fix Rotation Crash

Prevents the Activity from crashing when the user rotates the phone between portrait and
landscape. Touches one file: `app/src/main/AndroidManifest.xml`.

---

## Fix: Add orientation config changes to MainActivity

**Problem:** `MainActivity` declares `android:configChanges="locale|layoutDirection"`. This tells
Android "handle these config changes yourself; don't recreate the Activity for them." But
`orientation` and `screenSize` are NOT in the list, so Android destroys and recreates the
Activity on every rotation — and the current app crashes during that recreation (likely a race in
the `viewModel.Factory` or an SDK component that doesn't survive recreation cleanly).

**Fix:** Extend the `configChanges` attribute to include `orientation`, `screenSize`, and
`screenLayout` so the Activity is never recreated on rotation. Compose recomposes automatically
when `LocalConfiguration.current` changes (it already reads `screenHeightDp`/`screenWidthDp` for
landscape detection), so no `onConfigurationChanged()` override is needed — the existing Compose
layout code already handles every orientation.

```xml
<!-- BEFORE — app/src/main/AndroidManifest.xml, line 41 -->
android:configChanges="locale|layoutDirection"

<!-- AFTER -->
android:configChanges="locale|layoutDirection|orientation|screenSize|screenLayout"
```

Full activity element for context:

```xml
<activity
    android:name=".MainActivity"
    android:configChanges="locale|layoutDirection|orientation|screenSize|screenLayout"
    android:exported="true"
    android:label="@string/app_name"
    android:launchMode="singleTop"
    android:theme="@style/Theme.Macaco.Splash">
```

**Why `screenLayout` too?** On some Samsung devices the layout "category" (compact / normal /
large) is reported as changed alongside `screenSize` on rotation; including it silences spurious
recreations on those devices.

**No Kotlin changes required.** `Composable`s that read `LocalConfiguration.current` (landscape
detection in `ProfileScreen`, `JournalListScreen`, `SettingsScreen`, `MapScreen`) already
recompose when the configuration changes — this is exactly how Compose handles orientation
without Activity recreation.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `orientation\|screenSize\|screenLayout` to `android:configChanges` | `app/src/main/AndroidManifest.xml` |
