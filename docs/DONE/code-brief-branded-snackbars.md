# Macaco — Snackbars: Suppress Reel-Cancel Error + Branded MacacoSnackbar

Cancelling an Adventure Reel currently pops a raw "StandaloneCoroutine was cancelled" snackbar
(screenshot-confirmed on device, vc49). Root-cause fix plus a branded snackbar component so all
in-app messages match the Macaco look. Touches `JournalViewModel.kt`, a new
`ui/components/MacacoSnackbar.kt`, `MainActivity.kt`, `JournalListScreen.kt`, and
`strings.xml` ×11.

**Background:** `AdventureReelEncoder.encode` wraps its whole body in `runCatching`, which
also catches the `CancellationException` thrown when `cancelReel()` cancels the job. The
coroutine then *keeps running*: `result.fold` maps it to
`ReelState.Error("StandaloneCoroutine was cancelled")`, overwriting the `Idle` that
`cancelReel()` just set, and `JournalListScreen`'s reel `LaunchedEffect` snackbars the raw
message. Separately, genuine encoder failures surface `e.message` — raw exception text — in a
stock Material snackbar.

---

## Change 1 — Don't turn cancellation into an error; localize real failures

**Problem:** As above. Two failure modes conflated: user-initiated cancel (should be silent)
and real encode failure (should show a friendly, localized message — not exception text).

**Fix:** In `JournalViewModel.startReel`, special-case `CancellationException` (stay `Idle`)
and map any other failure to a localized generic string unless it's our own no-photos message
(thrown via `check(...)` with `reel_no_photos_error` per `code-brief-qa-polish.md` Change 3 —
if that brief is implemented, its message is already localized and can pass through).

```kotlin
// BEFORE (JournalViewModel.startReel, ~line 133)
        reelEncoderJob = viewModelScope.launch(Dispatchers.IO) {
            _reelState.value = ReelState.Generating(tripName, 0f)
            // Plain ViewModel (manual DI) — appContext is injected at construction, not getApplication().
            val result = AdventureReelEncoder(appContext).encode(
                photos = reelPhotos,
                outputName = "reel_${tripName.replace(" ", "_")}.mp4",
                onProgress = { p -> _reelState.value = ReelState.Generating(tripName, p) }
            )
            _reelState.value = result.fold(
                onSuccess = { uri -> ReelState.Ready(tripName, uri) },
                onFailure = { e -> ReelState.Error(e.message ?: "Reel generation failed.") }
            )
        }

// AFTER
        reelEncoderJob = viewModelScope.launch(Dispatchers.IO) {
            _reelState.value = ReelState.Generating(tripName, 0f)
            // Plain ViewModel (manual DI) — appContext is injected at construction, not getApplication().
            val result = AdventureReelEncoder(appContext).encode(
                photos = reelPhotos,
                outputName = "reel_${tripName.replace(" ", "_")}.mp4",
                onProgress = { p -> _reelState.value = ReelState.Generating(tripName, p) }
            )
            _reelState.value = result.fold(
                onSuccess = { uri -> ReelState.Ready(tripName, uri) },
                onFailure = { e ->
                    when {
                        // User cancelled via cancelReel(): encode's runCatching swallowed the
                        // CancellationException. cancelReel already set Idle — keep it, and
                        // never surface cancellation as an error.
                        e is kotlinx.coroutines.CancellationException -> ReelState.Idle
                        // Our own guard messages (e.g. no-photos) are already user-facing.
                        e is IllegalStateException && !e.message.isNullOrBlank() ->
                            ReelState.Error(e.message!!)
                        // Anything else: friendly localized copy, never raw exception text.
                        else -> ReelState.Error(appContext.getString(R.string.reel_error_generic))
                    }
                }
            )
        }
```

New string key (all 11 languages):

| Key | EN value |
|-----|----------|
| `reel_error_generic` | Couldn't create the reel. Please try again. |

**File:** `JournalViewModel.kt`, `res/values*/strings.xml`

---

## Change 2 — MacacoSnackbar component

**Problem:** Default M3 snackbars (`inverseSurface` grey pill) look foreign against the Macaco
UI — see screenshot: a flat light-grey bar over the teal journal.

**Fix:** New reusable composable in `ui/components/MacacoSnackbar.kt`, used as the content
slot of every `SnackbarHost`. Brand language: teal container, monkey icon leading, gold-tinted
action — all via theme tokens so all 7 app themes work.

```
┌──────────────────────────────────────────────┐
│ 🐒  Couldn't create the reel.        ACTION  │   ← primary container, onPrimary text,
│     Please try again.                        │     secondary(action), 12dp corners
└──────────────────────────────────────────────┘
```

```kotlin
// NEW FILE — app/src/main/java/com/houseofmmminq/macaco/ui/components/MacacoSnackbar.kt
package com.houseofmmminq.macaco.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.houseofmmminq.macaco.R

/**
 * Branded snackbar: teal (primary) container with the monkey mark and a gold-leaning
 * (secondary-token) action, replacing the stock inverseSurface pill. Pass as the content
 * slot of SnackbarHost: `SnackbarHost(state) { data -> MacacoSnackbar(data) }`.
 */
@Composable
fun MacacoSnackbar(data: SnackbarData) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = 6.dp)
            )
            data.visuals.actionLabel?.let { label ->
                TextButton(onClick = { data.performAction() }) {
                    Text(
                        label,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
```

Token note: on the Macaco default theme `primary` is the brand teal and `secondaryContainer`
the amber family, echoing the teal-header + gold-wordmark brand moments; on the other 6 themes
the same tokens keep contrast guarantees (`onPrimary` pairs with `primary`).

**File:** `ui/components/MacacoSnackbar.kt` (new)

---

## Change 3 — Wire it into both hosts

**MainActivity.kt** (update-ready "Restart" snackbar, ~line 159):

```kotlin
// BEFORE
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                    )

// AFTER
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                    ) { data -> MacacoSnackbar(data) }
```

**JournalListScreen.kt** (sync errors + reel errors, ~line 724):

```kotlin
// BEFORE
            snackbarHost = { SnackbarHost(snackbarHostState) },

// AFTER
            snackbarHost = { SnackbarHost(snackbarHostState) { data -> MacacoSnackbar(data) } },
```

Add `import com.houseofmmminq.macaco.ui.components.MacacoSnackbar` to both files.

**Files:** `MainActivity.kt`, `JournalListScreen.kt`

---

## Scope notes

- Settings/backup/restore feedback uses `Toast`s — system UI that can't be themed; migrating
  them to snackbars is a separate decision, out of scope here.
- If `code-brief-qa-polish.md` (Change 3: `check(framesRendered > 0)`) isn't implemented yet,
  the `IllegalStateException` branch in Change 1 simply never matches — no dependency.

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Reel cancel → stay Idle; real failures → localized `reel_error_generic` | `JournalViewModel.kt`, `strings.xml` ×11 |
| 2 | New branded `MacacoSnackbar` component (token-only colours) | `ui/components/MacacoSnackbar.kt` |
| 3 | Use it in both `SnackbarHost`s | `MainActivity.kt`, `JournalListScreen.kt` |
