# Macaco — Home-Screen Widget: Fix "Couldn't Add Widget" Crash

Covers `AndroidManifest.xml`. The "On This Day" home-screen widget (`OnThisDayWidgetProvider`)
fails to add from the launcher with a "Couldn't add widget" error. Root cause: its `<receiver>`
declaration is `exported="false"`, which blocks the launcher app (a separate process/app) from
binding to it and broadcasting `APPWIDGET_UPDATE` — the exact interaction required to place a
widget on the home screen. This is a manifest-only fix; `OnThisDayWidgetProvider.kt`,
`widget_on_this_day.xml`, and `on_this_day_widget_info.xml` were all checked and are correct.

---

## 1. Widget receiver blocks cross-app binding

**Problem:** `AndroidManifest.xml` (~line 72-81):

```xml
<!-- BEFORE -->
<!-- "On This Day" home-screen widget. -->
<receiver
    android:name=".ui.widget.OnThisDayWidgetProvider"
    android:exported="false">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/on_this_day_widget_info" />
</receiver>
```

`exported="false"` means only components within Macaco's own app/UID can send it broadcasts or
bind to it. The home-screen launcher is a different app entirely, so `AppWidgetManager` can never
deliver `ACTION_APPWIDGET_UPDATE`/`ACTION_APPWIDGET_OPTIONS_CHANGED` to it, and the system refuses
to let the user add the widget at all — hence "Couldn't add widget" at the moment of drop, before
`onUpdate` ever runs.

**Fix:** Flip to `exported="true"`. This is standard and required for every `AppWidgetProvider`
on Android (Google's own widget samples and docs specify `exported="true"`); it is not a security
regression — the intent-filter is scoped to the single `APPWIDGET_UPDATE` action, and widget
providers are designed to be invoked cross-process by the system/launcher.

```xml
<!-- AFTER -->
<!-- "On This Day" home-screen widget. -->
<receiver
    android:name=".ui.widget.OnThisDayWidgetProvider"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/on_this_day_widget_info" />
</receiver>
```

**File:** `app/src/main/AndroidManifest.xml`

---

## Verification steps for Code

Static manifest fix — no runtime rendering possible here, but Code should note in the worklog
that this needs an on-device check after the next build: long-press home screen → Widgets →
Macaco → drag "On This Day" onto the home screen → confirm it lands without an error and shows
either a memory, the empty state, or the signed-out state depending on account status.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `OnThisDayWidgetProvider` receiver: `exported="false"` → `exported="true"` | `AndroidManifest.xml` |
