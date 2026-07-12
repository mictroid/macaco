# Macaco — vc69 Review: Reel Icon Reliability, Drive Backup Gating Gap, Year in Travel Recolor

Four fixes from a screenshot review of vc69 (`docs/screenshots/69/`). Touches `BillingManager.kt`,
`MainActivity.kt`, `JournalViewModel.kt`, `SettingsScreen.kt`, and `YearInTravelScreen.kt`.

---

## Change 1 — Adventure Reel button can go missing on a stale entitlement read

**Problem:** `docs/screenshots/69/Screenshot_20260712-151754_Macaco.jpg` and
`s8-Screenshot_20260712-151655_Macaco.jpg` both show the "Birthday Cruise" / "Capri" trip headers
in the journal list with **no** 🎬 reel icon, even though there's clearly room in the row for it.

This is *not* an app-wide paywall bug — by design (see `NavGraph.kt` line 156: "Premium is
enforced per-feature (backup, reel) and at entry creation beyond the free limit, not as an
app-wide wall"), free-tier users can reach the journal list at all, and `TripHeader` in
`JournalListScreen.kt` (~line 1385) correctly hides the `Videocam` icon when
`isPurchased != true`:

```kotlin
if (isPurchased) {
    Spacer(Modifier.width(8.dp))
    IconButton(onClick = onCreateReel, modifier = Modifier.size(28.dp)) {
        Icon(Icons.Filled.Videocam, ...)
    }
}
```

So the icon disappearing is either (a) correct — this account genuinely isn't premium right now —
or (b) a stale entitlement read. (b) is real and worth fixing regardless: `BillingManager.kt`
only calls `refreshEntitlement()` once, inside `init {}` (`BillingManager.kt` line 90) — i.e. once
per cold process start. After that, `_isPremium` only updates when RevenueCat's
`UpdatedCustomerInfoListener` push fires (purchase, restore, or a server-pushed change) — it is
**not** guaranteed to fire promptly just because the app was foregrounded again after sitting in
the background for a while (e.g. entitlement renewed/corrected server-side while backgrounded, or
the cached `CustomerInfo` from cold start was itself briefly stale). Since the reel icon, the
Drive backup gate (Change 2), and Print Book all key off this same `isPremium` StateFlow, a stale
read silently hides all three at once with no error shown anywhere — which matches "the reel
button just disappeared" exactly.

**Fix:** Re-run entitlement refresh on every app resume, not just cold start, so a stale read
self-heals within a few seconds of reopening the app instead of persisting for the rest of the
process lifetime.

`BillingManager.kt` (~line 126) — expose the existing private refresh:

```kotlin
// BEFORE
    private fun refreshEntitlement() {

// AFTER
    fun refreshEntitlement() {
```

`MainActivity.kt` (~line 188, `onResume`) — call it alongside the existing update check:

```kotlin
// BEFORE
    override fun onResume() {
        super.onResume()
        // ... existing checkForUpdate() call ...
    }

// AFTER
    override fun onResume() {
        super.onResume()
        // Re-check entitlement on every resume — RevenueCat's push listener isn't guaranteed to
        // fire just because the app came back to the foreground, so a stale premium read from
        // cold start (or a server-side entitlement change while backgrounded) could otherwise
        // silently hide reel/Drive-backup/Print-Book for the rest of the process lifetime.
        (application as TravelJournalApp).billingManager.refreshEntitlement()
        // ... existing checkForUpdate() call ...
    }
```

Adjust the exact accessor to however `MainActivity` currently reaches the service locator
(`TravelJournalApp`) for other managers — match the existing pattern used for
`checkForUpdate()`'s dependencies in the same method.

**Verification:** background the app, use RevenueCat's dashboard (or `adb shell` sandbox tools) to
flip the test account's entitlement, foreground the app, and confirm the reel icon / Drive backup
gating / Print Book access update within a resume cycle without needing a force-quit.

**If this account is genuinely not premium:** no code change is needed for the icon itself — ask
Michael to confirm the 12-memories test account's actual subscription/trial state before assuming
(a) is a bug.

**File(s):** `BillingManager.kt`, `MainActivity.kt`

---

## Change 2 — Google Drive backup is gated at "Connect" but not at actual sync time

**Problem:** `docs/screenshots/69/Screenshot_20260712_160642_Macaco.jpg` shows Settings →
Google Drive Backup fully connected and syncing ("Connected as mmartin1985t@gmail.com", "All
photos backed up", Sync Now / Disconnect both live) with zero premium indication anywhere on the
card — no lock icon, no "Premium" label, nothing distinguishing it from a free feature. Compare
against **Backup to file** directly below it in the same screenshot, which always shows a 🔒 lock
icon plus "A Premium feature — unlock it from Subscription" whenever the account isn't premium.

Digging into why: `SettingsScreen.kt`'s `DriveBackupCard` (~line 1053) *does* take a `premium`
param and *does* route the **Connect** button to the paywall when `isPurchased != true`
(`code-brief-premium-gating-corrections.md`, already shipped). But that's the only place premium
is checked. Once `driveConnected` is `true` — which persists via the Google account's granted
`DRIVE_FILE` OAuth scope, independent of the app's own purchase state (it survives
sign-out/reinstall/subscription lapse since it's a Google account grant, not app state) — none of
the actual sync code paths re-check premium:

- `JournalViewModel.saveEntry()` (~line 496-503) uploads new photos/videos to Drive on every save,
  unconditionally.
- The `cloudEntrySync.entries.collect { ... drivePhotoSync.downloadMissingPhotos(entryList) }`
  loop (~line 476-480) auto-downloads on every entry-list change, unconditionally.
- `JournalViewModel.syncPhotosToGoogleDrive()` (~line 651, wired to the Settings "Sync Now"
  button) runs a full sync pass, unconditionally.

So any account that has ever granted the Drive scope — whether currently premium or not — gets
full working Drive backup with no premium gate and no visual indication it's a paid feature. This
is the literal bug reported: **not labeled premium, not actually gated.**

**Fix:** Gate all three call sites on `isPurchased.value == true`, and give `DriveBackupCard` the
same persistent lock-icon treatment as `BackupFileCard` plus hide "Sync Now" (leaving only
Disconnect) when connected-but-not-premium, so a lapsed subscriber can disconnect but can't keep
syncing for free.

`JournalViewModel.kt` (~line 496, inside `saveEntry`):

```kotlin
// BEFORE
            if (entry.photoUris.isNotEmpty() || entry.videoUris.isNotEmpty()) {
                launch {
                    val newPhotoIds =
                        if (entry.photoUris.isNotEmpty()) drivePhotoSync.uploadEntryPhotosOrReport(entry)
                        else entry.driveFileIds

// AFTER
            if (isPurchased.value == true &&
                (entry.photoUris.isNotEmpty() || entry.videoUris.isNotEmpty())
            ) {
                launch {
                    val newPhotoIds =
                        if (entry.photoUris.isNotEmpty()) drivePhotoSync.uploadEntryPhotosOrReport(entry)
                        else entry.driveFileIds
```

`JournalViewModel.kt` (~line 476, auto-download loop):

```kotlin
// BEFORE
        viewModelScope.launch {
            cloudEntrySync.entries.collect { entryList ->
                drivePhotoSync.downloadMissingPhotos(entryList)
            }
        }

// AFTER
        viewModelScope.launch {
            cloudEntrySync.entries.collect { entryList ->
                if (isPurchased.value == true) {
                    drivePhotoSync.downloadMissingPhotos(entryList)
                }
            }
        }
```

`JournalViewModel.kt` (~line 651, manual "Sync Now"):

```kotlin
// BEFORE
    fun syncPhotosToGoogleDrive() {
        drivePhotoSync.syncAll(entries.value) { updated ->

// AFTER
    fun syncPhotosToGoogleDrive() {
        if (isPurchased.value != true) return
        drivePhotoSync.syncAll(entries.value) { updated ->
```

`SettingsScreen.kt` (`DriveBackupCard`, ~line 1069-1099) — add the lock icon to the title row,
matching `BackupFileCard`'s pattern one section below:

```kotlin
// BEFORE
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (connected) Icons.Filled.CloudUpload else Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (connected) stringResource(R.string.settings_drive_connected)
                        else stringResource(R.string.settings_drive_not_connected),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
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
                }
            }

// AFTER
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (connected) Icons.Filled.CloudUpload else Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (connected) stringResource(R.string.settings_drive_connected)
                        else stringResource(R.string.settings_drive_not_connected),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        // A connection can outlive the subscription that created it (the Google
                        // OAuth grant persists independently of app purchase state), so the
                        // premium-required message now also covers "connected but not premium".
                        if (connected && connectedEmail != null && premium)
                            stringResource(R.string.settings_drive_connected_as, connectedEmail)
                        else if (connected && premium)
                            stringResource(R.string.settings_drive_connected_subtitle)
                        else if (!premium)
                            stringResource(R.string.settings_drive_premium_required)
                        else
                            stringResource(R.string.settings_drive_not_connected_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!premium) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!premium) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
```

`SettingsScreen.kt` (`DriveBackupCard`, ~line 1141-1171) — hide Sync Now when connected but not
premium, keep Disconnect available:

```kotlin
// BEFORE
            if (!connected) {
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Text(stringResource(R.string.settings_drive_connect))
                }
            } else if (syncState !is DrivePhotoSyncState.Syncing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSyncNow, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_drive_sync_now))
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.settings_drive_disconnect))
                    }
                }
            }

// AFTER
            if (!connected) {
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Text(stringResource(R.string.settings_drive_connect))
                }
            } else if (!premium) {
                // Connected via a Google grant that outlived the subscription — allow disconnect,
                // but not further free syncing.
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.settings_drive_disconnect))
                }
            } else if (syncState !is DrivePhotoSyncState.Syncing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSyncNow, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_drive_sync_now))
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.settings_drive_disconnect))
                    }
                }
            }
```

No new strings needed — `settings_drive_premium_required` already exists (×11 locales,
`strings.xml` line 241) and now covers both the "not connected, not premium" and "connected, not
premium" cases.

**Out of scope:** forcibly revoking the Google OAuth grant server-side when premium lapses — the
grant itself is harmless without an app-side sync path willing to use it; this fix just makes sure
nothing actually uses it for free.

**File(s):** `JournalViewModel.kt`, `SettingsScreen.kt`

---

## Change 3 — Year in Travel: swap stat colors, add teal border, drop the duplicate branding block

**Problem (colors):** `docs/screenshots/69/Screenshot_20260712_165135_Macaco.jpg` shows the stat
grid with "9" (Memories) in gold and "4"/"6"/"118" (Trips/Locations/Media) in teal, while all four
labels ("Memories"/"Trips"/"Locations"/"Media") render in plain gray
(`colorScheme.onSurfaceVariant`). The ask is the reverse emphasis: all four **numbers** in teal,
all four **labels** in gold, plus a teal border around the stat card.

**Problem (duplicate branding):** The screen already has one full macaco header — the top banner
with the icon, "macaco" wordmark, and "Year in Travel" label (`MacacoBrandBlock`, ~line 102-113).
Just above the Share button, a second, smaller macaco block repeats it (~line 204-213):
`MacacoBrandBlock(isLandscape = false, collapsed = true)` + a standalone `"macaco"` gold text —
this is the doubled branding Michael flagged.

**Fix:**

```
BEFORE                                  AFTER
┌─────────────────────────────┐        ┌═════════════════════════════┐  ← teal border
│   9 (gold)     4 (teal)     │        │   9 (teal)     4 (teal)     │
│ Memories(gray) Trips(gray)  │        │ Memories(gold) Trips(gold)  │
│                              │        │                              │
│   6 (teal)   118 (teal)     │        │   6 (teal)   118 (teal)     │
│ Locations(gray) Media(gray) │        │ Locations(gold) Media(gold) │
└─────────────────────────────┘        └═════════════════════════════┘
...                                     ...
[small macaco icon]         ← remove
macaco                       ← remove
[Share button]                         [Share button]
```

`YearInTravelScreen.kt` (~line 155-172, stat card):

```kotlin
// BEFORE
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatItem(value = recap.entryCount.toString(), label = stringResource(R.string.profile_memories), valueColor = SplashGold)
                            StatItem(value = recap.tripCount.toString(), label = stringResource(R.string.profile_trips), valueColor = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatItem(value = recap.locationCount.toString(), label = stringResource(R.string.profile_locations), valueColor = MaterialTheme.colorScheme.primary)
                            StatItem(value = recap.mediaCount.toString(), label = stringResource(R.string.profile_media), valueColor = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

// AFTER
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatItem(value = recap.entryCount.toString(), label = stringResource(R.string.profile_memories), valueColor = MaterialTheme.colorScheme.primary, labelColor = SplashGold)
                            StatItem(value = recap.tripCount.toString(), label = stringResource(R.string.profile_trips), valueColor = MaterialTheme.colorScheme.primary, labelColor = SplashGold)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatItem(value = recap.locationCount.toString(), label = stringResource(R.string.profile_locations), valueColor = MaterialTheme.colorScheme.primary, labelColor = SplashGold)
                            StatItem(value = recap.mediaCount.toString(), label = stringResource(R.string.profile_media), valueColor = MaterialTheme.colorScheme.primary, labelColor = SplashGold)
                        }
                    }
                }
```

`StatItem` (`ProfileScreen.kt` line 737-747) already accepts a `labelColor` param — no signature
change needed there, just pass it from the call sites above.

New import needed in `YearInTravelScreen.kt`:

```kotlin
import androidx.compose.foundation.BorderStroke
```

`YearInTravelScreen.kt` (~line 203-213, remove the duplicate branding block):

```kotlin
// BEFORE
            Spacer(Modifier.height(24.dp))
            MacacoBrandBlock(isLandscape = false, collapsed = true)
            Text(
                "macaco",
                color = SplashGold,
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                textAlign = TextAlign.Center
            )
            Button(

// AFTER
            Spacer(Modifier.height(24.dp))
            Button(
```

Double-check after removing this block whether `sp` (used only by the removed `letterSpacing =
3.sp`) is still referenced elsewhere in the file — if not, drop the now-unused
`androidx.compose.ui.unit.sp` import.

**Out of scope:** the *exported* PNG (`YearRecapRenderer.kt`, what actually gets shared —
`docs/screenshots/69/year_recap_2026.png`) — that's a separate canvas-drawn render, not this
Compose screen, and wasn't part of the request. If Michael wants the same teal/gold swap + border
there too, that's a follow-up brief against `YearRecapRenderer.kt`.

**File(s):** `YearInTravelScreen.kt`

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Re-run `BillingManager.refreshEntitlement()` on every app resume, not just cold start, so stale entitlement reads self-heal (candidate cause of the missing reel icon) | `BillingManager.kt`, `MainActivity.kt` |
| 2 | Gate Drive auto-upload, auto-download, and manual "Sync Now" behind `isPurchased`; add lock icon + error-tinted premium messaging to `DriveBackupCard`; hide Sync Now (keep Disconnect) when connected-but-not-premium | `JournalViewModel.kt`, `SettingsScreen.kt` |
| 3 | Year in Travel stat grid: numbers → teal, labels → gold, `Card` gains a teal `BorderStroke` | `YearInTravelScreen.kt` |
| 4 | Remove the duplicate `MacacoBrandBlock` + "macaco" text block above the Share button (top header already provides the branding) | `YearInTravelScreen.kt` |
