# Macaco — Premium Gating: Drive Backup Gate + Help & About Corrections

This brief fixes a mismatch between what Help & About claims is premium-gated and what the code
actually enforces. It touches `SettingsScreen.kt` (gate Google Drive photo backup behind
purchase) and `strings.xml` (correct the "Premium unlocks..." benefits copy and the plain-text
Backup & Restore FAQ answer). It does **not** cover the trip-share-link retirement — that's
already fully scoped in `docs/code-brief-retire-trip-share-link.md`; implement that brief
separately (or alongside this one, since both touch Help & About's FAQ list, just different
sections).

Context: an audit found that `help_faq_premium_benefits_a` claims Premium unlocks "the Adventures
map, ... swipe between entries, custom themes, and Google Drive photo backup" — but only Drive
backup is being moved behind the paywall now. The Adventures map, swipe-between-entries, and
custom themes are staying free on purpose (low/no infra cost, good for retention, no reason to
gate) — the copy just needs to stop claiming otherwise.

---

## Change 1 — Gate Google Drive photo backup behind purchase

**Problem:** `DriveBackupCard` in `SettingsScreen.kt` has no `isPurchased` check anywhere —
free users can connect Google Drive and sync entry photos today, even though Help & About
describes it as a premium benefit. Drive backup is the one "free" feature with a real ongoing
cost (Drive API calls, storage, support burden if sync breaks), so it should follow the same
gating pattern already used for Backup/Restore (`BackupFileCard`) and Print Book
(`SettingsClickRow` for `print_book_title`) just above and below it in the same file.

**Fix:** Add a `premium: Boolean` param to `DriveBackupCard`, pass `isPurchased == true` at the
call site (matching `BackupFileCard`'s existing `premium = isPurchased == true` one section
below), and branch `onConnect` so unpurchased users are routed to the paywall instead of the
Google sign-in flow. When not purchased, show the same "premium required" subtitle pattern used
by `print_premium_required` / `settings_backup_premium_required`, and disable the connect button
tap-through to sign-in.

Call site — `SettingsScreen.kt` (around line 734, just above `DriveBackupCard(...)`):

```kotlin
// BEFORE
DriveBackupCard(
    connected = driveConnected,
    connectedEmail = connectedEmail,
    syncState = driveSyncState,
    onConnect = { driveSignInLauncher.launch(driveSignInClient.signInIntent) },
    onSyncNow = { viewModel.syncPhotosToGoogleDrive() },
    onDisconnect = {
        driveSignInClient.signOut().addOnCompleteListener {
            driveConnected = false
        }
    }
)

// AFTER
DriveBackupCard(
    connected = driveConnected,
    connectedEmail = connectedEmail,
    syncState = driveSyncState,
    premium = isPurchased == true,
    onConnect = {
        if (isPurchased == true) {
            driveSignInLauncher.launch(driveSignInClient.signInIntent)
        } else {
            onNavigateToPaywall()
        }
    },
    onSyncNow = { viewModel.syncPhotosToGoogleDrive() },
    onDisconnect = {
        driveSignInClient.signOut().addOnCompleteListener {
            driveConnected = false
        }
    }
)
```

Composable definition — `SettingsScreen.kt` (around line 1047, `private fun DriveBackupCard`):

```kotlin
// BEFORE
private fun DriveBackupCard(
    connected: Boolean,
    connectedEmail: String?,
    syncState: DrivePhotoSyncState,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit
) {

// AFTER
private fun DriveBackupCard(
    connected: Boolean,
    connectedEmail: String?,
    syncState: DrivePhotoSyncState,
    premium: Boolean,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit
) {
```

Subtitle text — same function, around line 1077 (`Column` holding the connected/not-connected
title + subtitle):

```kotlin
// BEFORE
Text(
    if (connected && connectedEmail != null)
        stringResource(R.string.settings_drive_connected_as, connectedEmail)
    else if (connected)
        stringResource(R.string.settings_drive_connected_subtitle)
    else
        stringResource(R.string.settings_drive_not_connected_subtitle),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

// AFTER
Text(
    if (connected && connectedEmail != null)
        stringResource(R.string.settings_drive_connected_as, connectedEmail)
    else if (connected)
        stringResource(R.string.settings_drive_connected_subtitle)
    else if (!premium)
        stringResource(R.string.settings_drive_premium_required)
    else
        stringResource(R.string.settings_drive_not_connected_subtitle),
    style = MaterialTheme.typography.bodySmall,
    color = if (!connected && !premium) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
)
```

Connect button — same function, around line 1129 (`if (!connected) { Button(onClick = onConnect...`):
no code change needed here — `onConnect` already branches to the paywall at the call site above,
so the button itself stays as-is. It will read as "Connect Google Drive" regardless of purchase
state, and tapping it routes to the paywall instead of triggering sign-in when unpurchased. This
matches how `BackupFileCard`'s export/import buttons behave (label doesn't change, tap routes to
paywall).

New string — add next to `settings_backup_premium_required` (`strings.xml` line 240) and
`print_premium_required` (line 262):

| Key | EN value |
|-----|----------|
| `settings_drive_premium_required` | A Premium feature — unlock it from Subscription |

All 11 supported languages need this key added, matching the existing translations of
`settings_backup_premium_required` / `print_premium_required` in each locale file.

**File(s):** `SettingsScreen.kt`, `strings.xml` (×11 locales)

---

## Change 2 — Correct the "Premium unlocks..." benefits FAQ

**Problem:** `help_faq_premium_benefits_a` (`strings.xml` line 84) lists the Adventures map,
swipe-between-entries, and custom themes as premium benefits. They are not gated in code and are
staying free by design — only unlimited entries, Adventure Reel, Print Book, local
Backup/Restore, and (as of Change 1) Google Drive backup are actually behind the paywall.

**Fix:** Rewrite the answer to list only what's actually gated.

```xml
<!-- BEFORE -->
<string name="help_faq_premium_benefits_a">Premium unlocks the full Macaco experience: unlimited journal entries, the Adventures map, Adventure Reel video slideshows, swipe between entries, custom themes, and Google Drive photo backup. Available as Monthly, Annual (with 7-day free trial), or Lifetime.</string>

<!-- AFTER -->
<string name="help_faq_premium_benefits_a">Premium unlocks unlimited journal entries, Adventure Reel video slideshows, printed photo books, Google Drive photo backup, and full backup/restore to a file. The Adventures map, swiping between entries, and custom themes are free for everyone. Available as Monthly, Annual (with 7-day free trial), or Lifetime.</string>
```

**File(s):** `strings.xml` (×11 locales — this key's translated copy needs the same correction
in each locale file, not just `values/strings.xml`)

---

## Change 3 — Disclose the Google Drive paywall in the Backup & Restore FAQ

**Problem:** `help_faq_a_backup` (`strings.xml` line 46) describes local Backup & Restore
without mentioning it's premium-only, which is accurate as-is (that FAQ answer is specifically
about the local `.zip` export, which is already gated) — but there's no separate FAQ entry
telling users Drive photo backup is now premium-gated too (Change 1). Left undocumented, this
will read as a bug report once Change 1 ships.

**Fix:** Add one clarifying sentence to the existing answer to cover both backup paths in one
place, since they live in the same Settings section and are easy to conflate.

```xml
<!-- BEFORE -->
<string name="help_faq_a_backup">Settings → Backup &amp; Restore lets you export all entries and photos to a single .zip file you can re-import anytime.</string>

<!-- AFTER -->
<string name="help_faq_a_backup">Settings → Backup &amp; Restore lets you export all entries and photos to a single .zip file you can re-import anytime. Both this and automatic Google Drive photo backup are Premium features.</string>
```

**File(s):** `strings.xml` (×11 locales)

---

## Out of scope

- Trip-share-link removal — covered in `docs/code-brief-retire-trip-share-link.md`, not this
  brief. If implementing both in the same pass, note that brief also edits the FAQ list in
  `HelpAboutScreen.kt` (removes the "Trip Sharing" `FaqSection`) — no conflict with the edits
  here since this brief only changes string *content*, not the FAQ section list.
- Adventures map, swipe-between-entries, custom themes — intentionally left free. No code
  changes needed for these; Change 2 just corrects the copy that overclaimed them as premium.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Gate Google Drive photo backup behind `isPurchased`, add premium-required subtitle + paywall routing | `SettingsScreen.kt`, `strings.xml` (×11) |
| 2 | Correct "Premium unlocks..." FAQ to drop map/swipe/themes, add Drive backup + print book | `strings.xml` (×11) |
| 3 | Disclose Drive backup is premium in the Backup & Restore FAQ answer | `strings.xml` (×11) |
