# Macaco — Settings: Backup Export/Import Navigates to Paywall Instead of Toast

Covers `SettingsScreen.kt` and `NavGraph.kt`. When a non-premium user taps Export or Import on
the Backup & Restore card, replace the current static Toast with actual navigation to the
purchase screen (`Screen.Paywall` → `PurchaseScreen`, the three-tier paywall).

---

## Problem

Today, tapping Export or Import while `isPurchased != true` just shows a Toast:
`"A Premium feature — unlock it from Subscription"` (`settings_backup_premium_required`). It
names a destination ("Subscription") but doesn't take the user there — there's no tap-through.
Contrast with the entry-creation paywall gate in `NavGraph.kt`, which already navigates straight
to `Screen.Paywall.route` when the free limit is hit. Backup/restore should behave the same way:
tapping either premium-gated action should route the user directly into the paywall, not just
tell them where to go.

Note: the destination should be `Screen.Paywall` (`PurchaseScreen`, which shows the three
RevenueCat tiers — Annual/Monthly/Lifetime — and lets the user buy), **not**
`Screen.Subscription` (`SubscriptionInfoScreen`, which is a post-purchase status page with no
purchase options and would be a dead end for a non-premium user).

## Fix

`SettingsScreen` needs a new navigation callback param, `onNavigateToPaywall: () -> Unit`, wired
in from `NavGraph.kt` to `navController.navigate(Screen.Paywall.route)` — matching the existing
`goToNewEntry` pattern at `NavGraph.kt:168-177`. The two `onExport` / `onImport` lambdas in
`BackupFileCard`'s call site swap their Toast branch for this callback. The Toast import/string
resource `settings_backup_premium_required` stays in use for the `BackupFileCard` subtitle text
(still shown as static "why this is locked" copy under the title) — only the *tap action* changes,
not the explanatory text or the lock icon.

```kotlin
// BEFORE — NavGraph.kt:327-332
composable(Screen.Settings.route) {
    SettingsScreen(
        viewModel = viewModel,
        onBack = { navController.popBackStack() }
    )
}

// AFTER
composable(Screen.Settings.route) {
    SettingsScreen(
        viewModel = viewModel,
        onBack = { navController.popBackStack() },
        onNavigateToPaywall = { navController.navigate(Screen.Paywall.route) }
    )
}
```

```kotlin
// BEFORE — SettingsScreen.kt:138-141
fun SettingsScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit
) {

// AFTER
fun SettingsScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit,
    onNavigateToPaywall: () -> Unit
) {
```

```kotlin
// BEFORE — SettingsScreen.kt:758-772
BackupFileCard(
    premium = isPurchased == true,
    busy = backupBusy,
    importProgress = importProgress,
    onExport = {
        if (isPurchased == true) showExportDialog = true
        else Toast.makeText(context, context.getString(R.string.settings_backup_premium_required), Toast.LENGTH_LONG).show()
    },
    onImport = {
        if (isPurchased == true) {
            pickerPending = true
            backupImportLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
        } else Toast.makeText(context, context.getString(R.string.settings_backup_premium_required), Toast.LENGTH_LONG).show()
    }
)

// AFTER
BackupFileCard(
    premium = isPurchased == true,
    busy = backupBusy,
    importProgress = importProgress,
    onExport = {
        if (isPurchased == true) showExportDialog = true
        else onNavigateToPaywall()
    },
    onImport = {
        if (isPurchased == true) {
            pickerPending = true
            backupImportLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
        } else onNavigateToPaywall()
    }
)
```

**Scope note:** `BackupFileCard`'s subtitle text and lock icon (lines 965-979) are left as-is —
they're a static "this is premium" indicator visible at a glance, independent of the tap
behavior. Only the Toast-on-tap is replaced. The Restore-purchase row (`SettingsClickRow` at
line 809-834) is untouched — it's already reachable regardless of premium status and isn't part
of this gate.

**File(s):** `SettingsScreen.kt`, `NavGraph.kt`

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `onNavigateToPaywall` param to `SettingsScreen`, wire from `NavGraph.kt` to `Screen.Paywall.route` | `NavGraph.kt`, `SettingsScreen.kt` |
| 2 | Replace premium-required Toast with `onNavigateToPaywall()` on Export/Import tap in `BackupFileCard` call site | `SettingsScreen.kt` |
