# Macaco — SettingsScreen: Add Restore Purchase Row

"Restore purchases" only exists on PurchaseScreen (shown before purchase). Once a user has
premium, they lose access to it. The FAQ answer for "Premium not working?" tells them to go to
"Settings → Premium → Restore purchases" — a path that doesn't exist. Fix: add a Subscription
section to SettingsScreen with a restore row, and update the FAQ string to match.

---

## 1. Add Subscription section to SettingsScreen

**Problem:** No subscription/restore option exists in Settings. A user who reinstalls or switches
devices has no recovery path once they're past PurchaseScreen.

**Fix:** Add a new section header + `SettingsClickRow` just before the existing "About" section
(which starts at the `SettingsSectionHeader(stringResource(R.string.settings_about))` call).
Use `Toast` for feedback — consistent with how `BackupFileCard` shows results.

**File:** `ui/screens/SettingsScreen.kt`

`viewModel.restorePurchase` already exists and takes a `(Result<Boolean>) -> Unit` callback
(see `JournalViewModel.kt` line 205). `context` and `isPurchased` are already in scope.

Find the line:
```kotlin
// ── About ─────────────────────────────────────────────────────────
Spacer(Modifier.height(4.dp))
SettingsSectionHeader(stringResource(R.string.settings_about))
```

Insert before it:
```kotlin
// ── Subscription ─────────────────────────────────────────────────
Spacer(Modifier.height(4.dp))
SettingsSectionHeader(stringResource(R.string.settings_subscription))

SettingsClickRow(
    icon = Icons.Filled.Restore,
    title = stringResource(R.string.settings_restore_purchase),
    value = "",
    onClick = {
        viewModel.restorePurchase { result ->
            result.fold(
                onSuccess = { restored ->
                    Toast.makeText(
                        context,
                        if (restored) context.getString(R.string.settings_restore_success)
                        else context.getString(R.string.settings_restore_not_found),
                        Toast.LENGTH_LONG
                    ).show()
                },
                onFailure = {
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_restore_error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }
)
```

Add import:
```kotlin
import androidx.compose.material.icons.filled.Restore
```

---

## 2. Fix FAQ answer string

**Problem:** `help_faq_premium_broken_a` in `strings.xml` currently reads:
> "Try restoring your purchase: go to Settings → Premium and tap Restore purchases."

"Settings → Premium" doesn't exist. After change #1 it becomes "Settings → Subscription".

**File:** `app/src/main/res/values/strings.xml` (and all locale variants ×11)

Find and update:
```xml
<string name="help_faq_premium_broken_a">Try restoring your purchase: go to Settings → Premium and tap Restore purchases. ...</string>
```

Change to:
```xml
<string name="help_faq_premium_broken_a">Try restoring your purchase: open Settings, scroll to Subscription, and tap Restore purchase. Make sure you\'re signed in with the same account used when you bought. If it still doesn\'t work, contact us at houseofmmminq@gmail.com.</string>
```

---

## 3. New string keys (×11 languages)

**File:** `app/src/main/res/values/strings.xml` and all locale variants

| Key | EN value |
|-----|----------|
| `settings_subscription` | Subscription |
| `settings_restore_purchase` | Restore purchase |
| `settings_restore_success` | Purchase restored successfully |
| `settings_restore_not_found` | No previous purchase found for this account |
| `settings_restore_error` | Couldn\'t restore — please try again |

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add Subscription section + `SettingsClickRow` calling `viewModel.restorePurchase` before About | `ui/screens/SettingsScreen.kt` |
| 2 | Update `help_faq_premium_broken_a` to point to Settings → Subscription | `res/values*/strings.xml` (×11) |
| 3 | Add 5 new string keys | `res/values*/strings.xml` (×11) |
