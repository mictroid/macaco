# Macaco — Settings: Backup Export/Import Must Survive Leaving the Screen

Fixes backups being cancelled mid-write when the user navigates away from Settings, which can
leave a truncated/corrupt zip at the chosen destination or a half-finished import. Touches
`JournalViewModel.kt` and `SettingsScreen.kt`.

**Background:** `SettingsScreen` runs `viewModel.exportBackup(...)` / `importBackup(...)` in
`backupScope = rememberCoroutineScope()`. That scope dies with the composable — back-press,
sign-out, anything that pops Settings cancels the file operation mid-stream. The `backupBusy`
overlay also doesn't intercept the system back button, so backing out during a backup is easy
to do accidentally.

---

## Change 1 — ViewModel: own the backup coroutines and the busy state

**Problem:** Backup work runs in a composition-scoped coroutine; busy state (`backupBusy`,
`backupIsImport`) is `remember`ed in the screen and dies with it.

**Fix:** Hoist both into the ViewModel. Add a `backupBusy: StateFlow<BackupBusy?>` (null = no
operation running) and fire-and-forget functions that run in `viewModelScope` and report the
result through a callback. Keep the existing suspend `exportBackup`/`importBackup` as the
internals.

```kotlin
// NEW — add to JournalViewModel (near importProgress, ~line 71)
    /** Kind of backup operation currently running, or null when idle. */
    enum class BackupBusy { EXPORT, IMPORT }

    private val _backupBusy = MutableStateFlow<BackupBusy?>(null)
    val backupBusy: StateFlow<BackupBusy?> = _backupBusy.asStateFlow()

    /** Runs the export in viewModelScope so leaving Settings can't cancel it mid-write. */
    fun exportBackupInBackground(
        dest: android.net.Uri,
        compact: Boolean,
        onDone: (Result<JournalBackup.ExportResult>) -> Unit
    ) {
        if (_backupBusy.value != null) return
        _backupBusy.value = BackupBusy.EXPORT
        viewModelScope.launch {
            val result = exportBackup(dest, compact)
            _backupBusy.value = null
            onDone(result)
        }
    }

    /** Runs the import in viewModelScope so leaving Settings can't cancel it mid-restore. */
    fun importBackupInBackground(
        src: android.net.Uri,
        onDone: (Result<Int>) -> Unit
    ) {
        if (_backupBusy.value != null) return
        _backupBusy.value = BackupBusy.IMPORT
        viewModelScope.launch {
            val result = importBackup(src)
            _backupBusy.value = null
            onDone(result)
        }
    }
```

Note: `exportBackup` and `importBackup` stay `suspend` and public (unchanged bodies) but
screens should no longer call them directly.

**File:** `JournalViewModel.kt`

---

## Change 2 — SettingsScreen: use the hoisted state, drop the local scope, block back

**Problem:** Local `backupScope`, `backupBusy`, `backupIsImport` as described. Also the picker
launchers set `backupBusy = true` before launching SAF, which the ViewModel can't know about —
keep a small local `pickerPending` flag for that window only.

**Fix:**

```kotlin
// BEFORE (~line 250)
    val backupScope = rememberCoroutineScope()
    var backupBusy by remember { mutableStateOf(false) }
    // true = import is running; false = export is running. Used to tailor the overlay status text.
    var backupIsImport by remember { mutableStateOf(false) }

// AFTER
    val backupBusyState by viewModel.backupBusy.collectAsState()
    // Covers only the SAF picker window (between button tap and picker result), before the
    // ViewModel-owned operation starts.
    var pickerPending by remember { mutableStateOf(false) }
    val backupBusy = backupBusyState != null || pickerPending
    val backupIsImport = backupBusyState == JournalViewModel.BackupBusy.IMPORT
    // A backup is writing to user-picked storage; swallow back presses so navigation can't
    // interrupt the overlay (the operation itself now survives in viewModelScope regardless).
    androidx.activity.compose.BackHandler(enabled = backupBusy) { }
```

Export launcher:

```kotlin
// BEFORE (~line 274)
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) {
            backupBusy = false
            backupIsImport = false
            return@rememberLauncherForActivityResult
        }
        val isCompact = pendingCompact // capture before the coroutine
        backupScope.launch {
            val result = viewModel.exportBackup(uri, compact = isCompact)
            backupBusy = false
            backupIsImport = false
            Toast.makeText(
                ...
            ).show()
        }
    }

// AFTER
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        pickerPending = false
        if (uri == null) return@rememberLauncherForActivityResult
        val appContext = context.applicationContext
        viewModel.exportBackupInBackground(uri, compact = pendingCompact) { result ->
            Toast.makeText(
                appContext,
                result.fold(
                    { r ->
                        if (r.photosSkipped > 0) {
                            appContext.getString(
                                R.string.settings_backup_export_done_warn, r.entries, r.photosSkipped
                            )
                        } else {
                            appContext.getString(R.string.settings_backup_export_done, r.entries)
                        }
                    },
                    { it.message ?: appContext.getString(R.string.settings_backup_failed) }
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }
```

Import launcher — same pattern (`pickerPending = false`; null → return;
`viewModel.importBackupInBackground(uri) { result -> Toast … }` using `appContext`).

Button handlers: replace every `backupBusy = true; backupIsImport = …` with
`pickerPending = true` (export-dialog confirm/dismiss buttons at ~line 799/810 and the import
button at ~line 784). The `DisposableEffect(backupBusy)` keep-screen-on block works unchanged
against the new `backupBusy` val.

**File:** `SettingsScreen.kt`

---

## Scope notes

- Toasts use `applicationContext`, so completion feedback still appears even if the user left
  Settings. The full-screen `backupBusy` overlay only renders while Settings is visible —
  acceptable, since back is blocked while it's up.
- Process death during a backup still aborts it (WorkManager would be the bulletproof fix) —
  explicitly out of scope for this pass.

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `BackupBusy` StateFlow + `exportBackupInBackground` / `importBackupInBackground` in viewModelScope | `JournalViewModel.kt` |
| 2 | Collect hoisted busy state, `pickerPending` for the SAF window, `BackHandler` guard, launchers call the new functions | `SettingsScreen.kt` |
