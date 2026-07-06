# Macaco — Settings Permissions Row Wrap & Profile Tablet-Landscape Scroll

Two independent fixes: the "App Permissions" row title breaks into three ragged lines in
Settings, and the Profile screen forces a scroll to reach "Delete Account" on tablets in
landscape. Touches `SettingsScreen.kt` and `ProfileScreen.kt` — one change each.

---

## Change 1 — Settings: "App Permissions" title wraps into 3 broken lines

**Problem:** `SettingsClickRow` (`SettingsScreen.kt:1255-1280`) lays title and value out in a
single `Row`: the title `Text` gets `Modifier.weight(1f)` but the value `Text` gets no weight and
no line/width limit at all. Row measures the unweighted value first at its full intrinsic width,
then gives whatever's left to the weighted title. For the Language row the value is short
("System default"), so this is invisible. For the App Permissions row (~line 720-730) the value is
the long sentence `settings_permissions_subtitle` ("Camera, location, photos, notifications") —
that eats almost the whole row width, leaving barely anything for the title, which then wraps
mid-word into "App" / "Permis" / "sions" (confirmed on Samsung A53, portrait).

**Fix:** Add an opt-in stacked layout to `SettingsClickRow` — title on its own full-width line,
value on the line below it (indented to align under the title, past the icon) — and use it only
for the App Permissions row. Language and Restore Purchase keep the existing single-line inline
layout unchanged (their values are short/empty, so the current layout is correct for them).

```
BEFORE (inline, squeezed)              AFTER (stacked, only for App Permissions)
┌──────────────────────────────┐       ┌──────────────────────────────┐
│ 🛡  App   Camera, location,   │       │ 🛡  App Permissions            │
│     Permis photos,            │       │     Camera, location, photos, │
│     sions  notifications      │       │     notifications             │
└──────────────────────────────┘       └──────────────────────────────┘
```

### BEFORE (`SettingsScreen.kt:1255-1280`)

```kotlin
private fun SettingsClickRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

### AFTER

```kotlin
private fun SettingsClickRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    // True for rows whose value is a full descriptive sentence (e.g. App Permissions listing every
    // runtime permission) rather than a short inline value (e.g. Language: "System default").
    // Inline layout gives the value Text no width/line limit, so a long value squeezes the weighted
    // title down until it wraps mid-word. Stacked layout puts the value on its own full-width line
    // below the title instead — same idea as how BackupFileCard already renders longer descriptions.
    stackedValue: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick
    ) {
        if (stackedValue) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 40.dp) // 24dp icon + 16dp spacer — aligns under the title
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
```

Then update the App Permissions call site to opt in:

### BEFORE (`SettingsScreen.kt:720-730`)

```kotlin
SettingsClickRow(
    icon = Icons.Filled.Security,
    title = stringResource(R.string.settings_permissions),
    value = stringResource(R.string.settings_permissions_subtitle),
    onClick = {
        val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }
)
```

### AFTER

```kotlin
SettingsClickRow(
    icon = Icons.Filled.Security,
    title = stringResource(R.string.settings_permissions),
    value = stringResource(R.string.settings_permissions_subtitle),
    stackedValue = true,
    onClick = {
        val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }
)
```

The Language (~line 645) and Restore Purchase (~line 808) call sites are unchanged — they don't
pass `stackedValue`, so they keep the default `false` (current inline behavior). No new imports —
`Column` and `Alignment` are already used elsewhere in this file.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/SettingsScreen.kt`

---

## Change 2 — Profile: tablet landscape forces a scroll to reach Delete Account

**Problem:** `docs/DONE/code-brief-profile-single-column.md` deliberately made tablets — even in
landscape — always use the single-column layout (the old 2-pane layout "looked cramped on large
screens"). That's the right call for width, but the single column's total height (92dp avatar +
name/email/chip + stats card + divider + a 3-row × 2-col action-tile grid + Delete Account +
"Member since") was originally sized for tall, narrow phone screens. On a tablet in landscape,
available height is comparatively short, so that same stack now overflows and the screen falls
back on `verticalScroll` to stay usable — which pushes Delete Account (and sometimes the footer)
below the fold on first open.

**Fix:** Don't reintroduce the 2-pane layout. Instead, reclaim vertical space inside the existing
single column on wide screens by laying the 6 action tiles out in **one row of 6** instead of
**3 rows of 2** — tablet landscape has width to spare, so trading it for the ~140dp of height two
extra grid rows cost is a direct win. Phones (portrait or landscape-short) are unaffected and keep
the current 3-row grid.

```
Tablet landscape — BEFORE (3 rows, forces scroll)   AFTER (1 row, fits without scrolling)
┌─────────────────────────────┐                     ┌─────────────────────────────────────┐
│ [Settings]      [Help]       │                     │ [Set][Help][Share][Rate][Sub][Sign] │
│ [Share]         [Rate]       │  ← 3 rows            └─────────────────────────────────────┘
│ [Subscription]  [Sign Out]   │                       Delete Account  ← now on-screen
├─────────────────────────────┤
│ ...scroll needed for below...│
│ Delete Account                │
└─────────────────────────────┘
```

### BEFORE (`ProfileScreen.kt`, ~lines 552-610)

```kotlin
val user = currentUser
if (user != null) {
    val gridSpacing = 6.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(gridSpacing)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
            modifier = Modifier.fillMaxWidth()
        ) {
            ProfileActionTile(
                Icons.Filled.Settings,
                stringResource(R.string.common_settings),
                onClick = onSettings
            )
            ProfileActionTile(
                Icons.AutoMirrored.Filled.HelpOutline,
                stringResource(R.string.drawer_help),
                onClick = onHelp
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
            modifier = Modifier.fillMaxWidth()
        ) {
            ProfileActionTile(
                Icons.Filled.Share,
                stringResource(R.string.drawer_share_app),
                onClick = { AppActions.shareApp(context, entries.size) }
            )
            ProfileActionTile(
                Icons.Filled.StarRate,
                stringResource(R.string.drawer_rate_us),
                onClick = { AppActions.requestReview(context) }
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
            modifier = Modifier.fillMaxWidth()
        ) {
            ProfileActionTile(
                Icons.Outlined.WorkspacePremium,
                stringResource(R.string.common_subscription),
                onClick = onSubscription
            )
            ProfileActionTile(
                Icons.AutoMirrored.Filled.Logout,
                stringResource(R.string.common_sign_out),
                tint = MaterialTheme.colorScheme.error,
                onClick = { showSignOutDialog = true }
            )
        }
    }
```

### AFTER

```kotlin
val user = currentUser
if (user != null) {
    val gridSpacing = 6.dp
    // Tablets stay single-column even in landscape (see code-brief-profile-single-column.md — the
    // old 2-pane layout looked cramped), but that column was sized for tall/narrow phone screens.
    // On a tablet in landscape the available height is comparatively short, so the 3-row action
    // grid below was forcing the whole screen to scroll just to reach Delete Account. Reclaim the
    // space by using the tablet's spare width instead: one row of 6 tiles rather than 3 rows of 2.
    val isWideLayout = LocalConfiguration.current.screenWidthDp >= 600 &&
        LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = if (isWideLayout) 760.dp else 560.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(gridSpacing)
    ) {
        if (isWideLayout) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                ProfileActionTile(Icons.Filled.Settings, stringResource(R.string.common_settings), onClick = onSettings)
                ProfileActionTile(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.drawer_help), onClick = onHelp)
                ProfileActionTile(Icons.Filled.Share, stringResource(R.string.drawer_share_app), onClick = { AppActions.shareApp(context, entries.size) })
                ProfileActionTile(Icons.Filled.StarRate, stringResource(R.string.drawer_rate_us), onClick = { AppActions.requestReview(context) })
                ProfileActionTile(Icons.Outlined.WorkspacePremium, stringResource(R.string.common_subscription), onClick = onSubscription)
                ProfileActionTile(
                    Icons.AutoMirrored.Filled.Logout,
                    stringResource(R.string.common_sign_out),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { showSignOutDialog = true }
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                ProfileActionTile(Icons.Filled.Settings, stringResource(R.string.common_settings), onClick = onSettings)
                ProfileActionTile(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.drawer_help), onClick = onHelp)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                ProfileActionTile(Icons.Filled.Share, stringResource(R.string.drawer_share_app), onClick = { AppActions.shareApp(context, entries.size) })
                ProfileActionTile(Icons.Filled.StarRate, stringResource(R.string.drawer_rate_us), onClick = { AppActions.requestReview(context) })
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                ProfileActionTile(Icons.Outlined.WorkspacePremium, stringResource(R.string.common_subscription), onClick = onSubscription)
                ProfileActionTile(
                    Icons.AutoMirrored.Filled.Logout,
                    stringResource(R.string.common_sign_out),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { showSignOutDialog = true }
                )
            }
        }
    }
```

The rest of the block (Delete Account `TextButton`, "Member since" footer) is unchanged — it
already sits below this `Column` at the same indentation level.

**Out of scope:** not touching the phone-landscape (`isLandscape`, `screenHeightDp < 480`) branch
elsewhere in this file, and not reintroducing the 2-pane layout — this only changes how the action
grid arranges itself on wide screens. If a tablet still needs to scroll after this (very large
avatar/stats stack, very small tablet), that's a separate, larger layout question worth flagging
back rather than solving here.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/ProfileScreen.kt`

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add opt-in stacked title/value layout to `SettingsClickRow`; use it for App Permissions so the title no longer wraps mid-word | `SettingsScreen.kt` |
| 2 | Lay the 6 Profile action tiles out in 1 row instead of 3 on wide (tablet landscape) screens, reclaiming vertical space so Delete Account is visible without scrolling | `ProfileScreen.kt` |
