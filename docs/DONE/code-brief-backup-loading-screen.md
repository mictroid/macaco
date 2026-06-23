# Macaco — SettingsScreen + JournalBackup: Branded Loading Screen During Backup Operations

During export and import, the app shows no visual feedback beyond a small busy indicator on the
backup card — the settings screen appears frozen, and on long operations the screen looks black.
The fix replaces this with a full-screen branded overlay (Macaco logo + spinner + status text) for
the duration of the backup operation. Touches `ui/screens/SettingsScreen.kt` only.

---

## Change: full-screen branded overlay when backupBusy = true

**Problem:** When `backupBusy = true` (export or import running), the SettingsScreen remains
visible but unresponsive. On long operations (681 MB import, large compact export) the screen
appears black or frozen, with no clear signal to the user that the app is working.

**Fix:** Add a second local boolean `backupIsImport` to distinguish export from import, then
overlay a full-screen branded `Box` (using `macacoBrandBackground()`) whenever `backupBusy`
is true. The overlay shows the Macaco logo, a `CircularProgressIndicator`, and contextual
status text. During import it shows the `importProgress` phase text; during export it shows
a generic "Preparing your backup…" message.

```
┌──────────────────────────────────────┐
│                                      │
│                                      │
│           [Macaco logo 80dp]         │
│               macaco                 │
│         Roam Freely. Forget Nothing. │
│                                      │
│           ◯  (spinner)              │
│                                      │
│       Preparing your backup...       │
│    (or: "Downloading backup..."      │
│         "Restoring entries...")      │
│                                      │
│                                      │
└──────────────────────────────────────┘
```

**Step 1 — add `backupIsImport` flag** near the existing `backupBusy` declaration:

```kotlin
// Existing (keep as-is):
var backupBusy by remember { mutableStateOf(false) }

// ADD immediately below:
// true = import is running; false = export is running. Used to tailor overlay text.
var backupIsImport by remember { mutableStateOf(false) }
```

**Step 2 — set `backupIsImport` at each launch site.**

In `backupImportLauncher` (the `onImport` callback, where `backupBusy = true` is set):
```kotlin
backupBusy = true
backupIsImport = true    // ADD this line
backupImportLauncher.launch(...)
```

In `backupExportLauncher` (both compact and full export paths, where `backupBusy = true` is set):
```kotlin
backupBusy = true
backupIsImport = false   // ADD this line (for both compact and full export clicks)
backupExportLauncher.launch(...)
```

Also clear it when busy ends (in the export and import coroutine callbacks, after `backupBusy = false`):
```kotlin
backupBusy = false
backupIsImport = false   // ADD after every backupBusy = false
```

**Step 3 — add the overlay.** In the outermost layout of `SettingsScreen` (the `Scaffold` or
its content lambda), wrap the existing content in a `Box(modifier = Modifier.fillMaxSize())` and
add the overlay as the last child so it renders on top:

```kotlin
Box(modifier = Modifier.fillMaxSize()) {

    // ... existing Scaffold / LazyColumn content unchanged ...

    // Branded loading overlay — covers the entire screen during backup operations.
    if (backupBusy) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(macacoBrandBackground())      // reuses existing branded gradient
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp)
                )
                Text(
                    text = "macaco",
                    color = SplashGoldBright,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 5.sp
                )
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(4.dp))
                // Contextual status text: show import phase or generic export message.
                val statusText = if (backupIsImport) {
                    when (importProgress?.phase) {
                        JournalBackup.ImportPhase.DOWNLOADING ->
                            stringResource(R.string.settings_backup_status_downloading)
                        JournalBackup.ImportPhase.RESTORING ->
                            stringResource(R.string.settings_backup_status_restoring)
                        else -> stringResource(R.string.settings_backup_status_importing)
                    }
                } else {
                    stringResource(R.string.settings_backup_status_exporting)
                }
                Text(
                    text = statusText,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }
}
```

`macacoBrandBackground()`, `SplashGoldBright`, `painterResource`, and `Image` are already used in
this file. `JournalBackup.ImportPhase` is already imported (used in the progress card below).

**Step 4 — add strings.** Four new keys needed (all 11 languages):

| Key | EN value |
|-----|----------|
| `settings_backup_status_exporting` | Preparing your backup… |
| `settings_backup_status_importing` | Restoring your backup… |
| `settings_backup_status_downloading` | Downloading backup… |
| `settings_backup_status_restoring` | Restoring entries… |

**File:** `ui/screens/SettingsScreen.kt`

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `backupIsImport` bool to distinguish export from import | `ui/screens/SettingsScreen.kt` |
| 2 | Add full-screen branded `Box` overlay (logo + spinner + status text) shown when `backupBusy = true` | `ui/screens/SettingsScreen.kt` |
| 3 | Add 4 string keys for overlay status text | `res/values/strings.xml` ×11 languages |
