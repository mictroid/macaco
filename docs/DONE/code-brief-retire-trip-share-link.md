# Macaco — Retire trip-share-link, Share button triggers Adventure Reel instead

Removes the blocked `/shared_trips` link-sharing feature entirely and consolidates trip sharing
onto the Adventure Reel flow, which already works end-to-end with no backend, no Firestore/Storage
security rules, and no Blaze plan requirement. Five files touched, one deleted.

**Why:** `TripShareManager.createShareLink()` has never been shippable — it requires
Firestore + Storage security rules for `/shared_trips` published in the Firebase console
(`docs/DONE/code-brief-shared-trip-links.md`), and as of Feb 3 2026 provisioning a Cloud Storage
bucket at all requires the paid Blaze plan. Rather than carry that unfinished, blocked feature
(plus its outstanding privacy-policy update) alongside Adventure Reel — which already renders a
local video and hands off to the OS share sheet, no account or backend needed on either end —
this collapses `TripHeader`'s two icons (Share link + Reel) into the one that already works.

---

## Change 1 — `TripHeader`: remove the Share-link icon, keep only Reel

**Problem:** `TripHeader` renders two icon buttons when `isPurchased`: a Share icon that opens
`ShareTripDialog` (dead end — always fails with permission-denied) and a Videocam icon that starts
Adventure Reel (works today). Keep only the working one.

```
BEFORE                                      AFTER
┌───────────────────────────────┐          ┌───────────────────────────────┐
│ ▾ Italy 2025      4 memories 🔗 🎥│    →     │ ▾ Italy 2025      4 memories  🎥│
└───────────────────────────────┘          └───────────────────────────────┘
```

```kotlin
// JournalListScreen.kt — TripHeader signature, BEFORE (~line 1460-1467):
@Composable
private fun TripHeader(
    tripName: String,
    entryCount: Int,
    isPurchased: Boolean,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onCreateReel: () -> Unit,
    onShare: () -> Unit
) {

// AFTER — drop onShare
@Composable
private fun TripHeader(
    tripName: String,
    entryCount: Int,
    isPurchased: Boolean,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onCreateReel: () -> Unit
) {
```

```kotlin
// JournalListScreen.kt — inside TripHeader, BEFORE (~line 1499-1524):
if (isPurchased) {
    Spacer(Modifier.width(8.dp))
    IconButton(
        onClick = onShare,
        modifier = Modifier.size(28.dp)
    ) {
        Icon(
            Icons.Filled.Share,
            contentDescription = stringResource(R.string.trip_share_action),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp)
        )
    }
    Spacer(Modifier.width(4.dp))
    IconButton(
        onClick = onCreateReel,
        modifier = Modifier.size(28.dp)
    ) {
        Icon(
            Icons.Filled.Videocam,
            contentDescription = stringResource(R.string.reel_create_button),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp)
        )
    }
}

// AFTER — Reel button only
if (isPurchased) {
    Spacer(Modifier.width(8.dp))
    IconButton(
        onClick = onCreateReel,
        modifier = Modifier.size(28.dp)
    ) {
        Icon(
            Icons.Filled.Videocam,
            contentDescription = stringResource(R.string.reel_create_button),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp)
        )
    }
}
```

**File:** `JournalListScreen.kt`.

---

## Change 2 — Remove the `TripHeader` call site's `onShare`, the dialog state, and `ShareTripDialog`

**Problem:** The call site still wires `onShare = { shareDialogTrip = trip to sectionEntries }`,
and the screen still holds `shareDialogTrip` state plus the whole `ShareTripDialog`/`ExpiryOption`
composables that render it.

```kotlin
// JournalListScreen.kt — call site, BEFORE (~line 624-627):
TripHeader(
    /* ... */
    onCreateReel = { viewModel.startReel(trip, sectionEntries) },
    onShare = { shareDialogTrip = trip to sectionEntries }
)

// AFTER
TripHeader(
    /* ... */
    onCreateReel = { viewModel.startReel(trip, sectionEntries) }
)
```

```kotlin
// JournalListScreen.kt — DELETE this block entirely (~line 238-250):
// ── Shared view-only trip links ─────────────────────────────────────────────────────────────
// Which trip's share dialog is open (name to its entries), or null.
var shareDialogTrip by remember { mutableStateOf<Pair<String, List<TravelEntry>>?>(null) }
val tripShareState by viewModel.tripShareState.collectAsState()
shareDialogTrip?.let { (name, tripEntries) ->
    ShareTripDialog(
        tripName = name,
        state = tripShareState,
        onCreate = { expiryDays -> viewModel.createTripShare(name, tripEntries, expiryDays) },
        onDismiss = {
            viewModel.tripShareConsumed()
            shareDialogTrip = null
        }
    )
}
```

```kotlin
// JournalListScreen.kt — DELETE the whole ShareTripDialog composable and ExpiryOption
// (~line 1026-1120): both only exist to serve the removed dialog.
```

**File:** `JournalListScreen.kt`.

---

## Change 3 — `JournalViewModel`: remove trip-share state and functions

**Problem:** `tripShareManager`, `TripShareState`, `tripShareState`, `createTripShare`,
`revokeTripShare`, and `tripShareConsumed` all exist only to serve the removed dialog.

```kotlin
// JournalViewModel.kt — DELETE this whole block (~line 348-381):
// ── Shared view-only trip links ─────────────────────────────────────────────────────────────
// NOTE: not shippable/testable until the /shared_trips Firestore + Storage security rules are
// applied in the Firebase console (see docs/code-brief-shared-trip-links.md). Until then every
// createTripShare falls into the Error branch with a permission-denied message.
private val tripShareManager = TripShareManager()

sealed class TripShareState {
    object Idle : TripShareState()
    object Creating : TripShareState()
    data class Ready(val url: String) : TripShareState()
    data class Error(val message: String) : TripShareState()
}
private val _tripShareState = MutableStateFlow<TripShareState>(TripShareState.Idle)
val tripShareState: StateFlow<TripShareState> = _tripShareState.asStateFlow()

fun createTripShare(tripName: String, entries: List<TravelEntry>, expiryDays: Int?) {
    val uid = authRepository.currentUser.value?.uid ?: return
    viewModelScope.launch {
        _tripShareState.value = TripShareState.Creating
        val result = tripShareManager.createShareLink(uid, tripName, entries, expiryDays)
        _tripShareState.value = result.fold(
            onSuccess = { TripShareState.Ready(it.url) },
            onFailure = {
                TripShareState.Error(it.message ?: appContext.getString(R.string.trip_share_error_generic))
            }
        )
    }
}

fun revokeTripShare(shareId: String, photoCount: Int) {
    viewModelScope.launch { tripShareManager.revokeShareLink(shareId, photoCount) }
}

fun tripShareConsumed() { _tripShareState.value = TripShareState.Idle }
```

```kotlin
// JournalViewModel.kt — remove the now-unused import (~line 25):
import com.houseofmmminq.macaco.data.sync.TripShareManager
```

**File:** `JournalViewModel.kt`.

---

## Change 4 — Delete `TripShareManager.kt`

The file's entire purpose was the removed feature — nothing else references it after Changes 1-3.

**File:** delete `data/sync/TripShareManager.kt`.

---

## Change 5 — Strings cleanup (all 11 `strings.xml`, optional but recommended)

These 13 keys become unused. Not required to remove immediately (dead strings don't break
anything), but worth pruning in the same pass so `docs/code-brief-shared-trip-links.md`'s
localization table doesn't mislead a future reader:

`trip_share_action`, `trip_share_title`, `trip_share_disclosure`, `trip_share_expiry_label`,
`trip_share_expiry_7d`, `trip_share_expiry_30d`, `trip_share_expiry_never`, `trip_share_create`,
`trip_share_copy`, `trip_share_copied`, `trip_share_revoke`, `trip_share_revoked`,
`trip_share_error_generic`.

**File:** `strings.xml` × 11 languages (skip if you'd rather do a separate cleanup pass later).

---

## Change 6 — Help & About FAQ: drop the "trip sharing" section

The `help_section_trip_sharing` FAQ entry (added in `code-brief-help-about-update-v3` /
`-collapsible-and-new-features`, one section in `HelpAboutScreen.kt`'s `FAQ_SECTIONS` list)
documents the link feature being removed here. Delete that `FaqSection` entry so Help & About
doesn't describe a feature that no longer exists.

```kotlin
// HelpAboutScreen.kt — DELETE from FAQ_SECTIONS (~line 164-171):
// NEW: shared trip links
FaqSection(
    R.string.help_section_trip_sharing,
    Icons.Filled.Share,
    listOf(
        R.string.help_faq_trip_sharing_q to R.string.help_faq_trip_sharing_a,
    )
),
```

Corresponding `help_section_trip_sharing`, `help_faq_trip_sharing_q`, `help_faq_trip_sharing_a`
string keys can be pruned alongside Change 5, or left unused if you're skipping that cleanup pass.

**File:** `HelpAboutScreen.kt` (this is a separate file from `code-brief-help-about-and-year-in-travel-fixes.md`'s
edits — apply both briefs' `HelpAboutScreen.kt` changes; they touch different, non-overlapping
parts of the file).

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `TripHeader` drops the Share-link icon, keeps only the working Reel icon | `JournalListScreen.kt` |
| 2 | Remove dialog state, call site's `onShare`, `ShareTripDialog`/`ExpiryOption` composables | `JournalListScreen.kt` |
| 3 | Remove `TripShareState`, `createTripShare`, `revokeTripShare`, `tripShareConsumed`, the `TripShareManager` import | `JournalViewModel.kt` |
| 4 | Delete the file | `TripShareManager.kt` |
| 5 | Prune 13 unused `trip_share_*` string keys (optional) | `strings.xml` × 11 |
| 6 | Drop the "trip sharing" FAQ section describing the removed feature | `HelpAboutScreen.kt` |

**Not touched:** Adventure Reel itself (`AdventureReelEncoder.kt`, `startReel`, `reelState`) —
unchanged, it's the thing everything now routes through.
