# Macaco — Settings: App Permissions Row

Add a "Permissions" row in the Privacy section of SettingsScreen. Tapping it opens the
Android system app-details page (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`) where the
user can review and adjust all runtime permissions in one place (Camera, Location, Photos,
Notifications).

Android does not allow apps to grant permissions silently — the user must approve via the
system prompt. The canonical pattern is to link to the system app settings, which surfaces
all permissions with their current Allow/Deny status and lets the user toggle them directly.
Builds on API 24+ with no extra libraries.

**Files:**
- `app/src/main/java/com/houseofmmminq/macaco/ui/screens/SettingsScreen.kt`
- `app/src/main/res/values/strings.xml` (new string resources)

---

## Change 1 — Add imports to SettingsScreen.kt

Only add those not already present:

```kotlin
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.material.icons.filled.Security
```

Note: `android.provider.Settings` is aliased to `AndroidSettings` to avoid clashing with
the `SettingsScreen` composable name.

---

## Change 2 — Add string resources to strings.xml

```xml
<string name="settings_permissions">App Permissions</string>
<string name="settings_permissions_subtitle">Camera, location, photos, notifications</string>
```

---

## Change 3 — Add Permissions row in the Privacy section (~line 699)

Insert after the existing App Lock `SettingsRow`, before the closing of the Privacy section
(i.e. before the `SettingsSectionHeader(stringResource(R.string.settings_drive_backup))`
line).

```kotlin
            SettingsRow(
                title = stringResource(R.string.settings_permissions),
                subtitle = stringResource(R.string.settings_permissions_subtitle),
                icon = Icons.Filled.Security,
                onClick = {
                    val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )
```

---

## Behaviour

Tapping "App Permissions" opens the Android system "App info" screen for Macaco, with the
Permissions row visible. From there the user can toggle Camera, Location, Photos, and
Notifications individually. This works on all API levels supported by the app (24+) and
requires no additional permissions in the manifest.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `Intent`, `Uri`, `AndroidSettings`, `Icons.Filled.Security` imports | `SettingsScreen.kt` |
| 2 | Add `settings_permissions` and `settings_permissions_subtitle` strings | `strings.xml` |
| 3 | Add `SettingsRow` for Permissions in the Privacy section | `SettingsScreen.kt` |
