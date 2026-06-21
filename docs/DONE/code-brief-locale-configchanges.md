# Brief: Fix Language-Switch Black Screen (API 33+)

**Priority:** Medium  
**File:** `app/src/main/AndroidManifest.xml`

## Problem

On API 33+ (A53, Android 13), switching languages via `AppCompatDelegate.setApplicationLocales()`
causes a visible black-screen flash. On API <33 (S8, Android 8.1) this is unavoidable — the system
always recreates the Activity. On API 33+ it CAN be avoided if the Activity declares it handles
locale changes itself, but our `MainActivity` is missing that declaration.

Without the declaration, Android assumes the Activity can't handle locale changes and kills+recreates
it, producing the black screen. With the declaration, AppCompat applies the locale in-place with no
recreation — the UI updates instantly.

## Change

In `app/src/main/AndroidManifest.xml`, find the `<activity android:name=".MainActivity" ...>`
declaration and add `locale` and `layoutDirection` to its `android:configChanges` attribute.

### If `android:configChanges` does not yet exist on MainActivity:

```xml
<activity
    android:name=".MainActivity"
    android:configChanges="locale|layoutDirection"
    ... (other existing attributes) >
```

### If `android:configChanges` already exists (e.g. with `orientation|screenSize`):

Append `|locale|layoutDirection` to the existing value:

```xml
android:configChanges="orientation|screenSize|locale|layoutDirection"
```

## Notes

- This is a manifest-only change; no Kotlin code changes required.
- `locale` handles the locale change notification; `layoutDirection` prevents a separate recreation
  when RTL/LTR direction changes as a result of the locale switch.
- On API <33 (the S8), `setApplicationLocales()` still recreates the Activity — this is a platform
  limitation that cannot be worked around. The black screen on the S8 is expected and unfixable
  without dropping to the legacy `Resources.updateConfiguration()` path, which we should not do.
- On the A53 (API 33), after this change, language switching should be instant with no black screen.
