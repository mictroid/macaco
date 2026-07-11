# Macaco — Shared View-Only Trip Links

New feature: generate a read-only web link for a trip that anyone can open without the app or an
account — for family following along or after the fact. **This is the biggest and riskiest brief
of this batch — read this section before starting.**

Macaco has no backend today (per `CLAUDE.md`): everything is client + Firebase/Drive, with no
Cloud Functions and no `firestore.rules`/`storage.rules` file tracked in this repo. A public,
unauthenticated link is a real architecture and privacy departure from that model, not a small
UI addition:

1. **Photos must leave the private model.** Entry photos are local `content://` URIs or
   OAuth-gated Drive files — neither is fetchable by an anonymous browser. This brief copies a
   capped subset of photos to a new public-read Storage path per share.
2. **Two security-rule changes are required outside this repo.** A new `shared_trips/{shareId}`
   Firestore collection and matching Storage path need rules allowing unauthenticated read. This
   repo has no rules file to edit — Change 5 below gives the proposed rule text, but **applying
   it requires the Firebase console (or adopting the Firebase CLI or a project this repo doesn't
   currently have)**. This is not something Code can complete by itself inside Android Studio.
3. **This needs a privacy-policy update before shipping** — the same conclusion reached earlier
   when discussing print-fulfillment integrations: a new data flow to a new place needs disclosure.
   Not attempted in this brief; flag it back to whoever owns `privacy-policy.html`.
4. **The actual public viewer page doesn't exist yet.** This brief produces a link
   (`https://mictroid.github.io/macaco/trip/?id={shareId}`) pointing at a page that has to be
   authored and deployed to the existing GitHub Pages pipeline (same one serving
   `privacy-policy.html` / `terms-of-service.html` / `r/index.html`) — a static-page/web task, not
   Android/Kotlin work, and out of scope for this brief (see Scope).

Given all that, this brief implements everything on the **Android app side**: creating a share
(capped photo upload + redacted Firestore doc), copying/sharing the link, listing and revoking
the user's own shares, and a default 30-day expiry (not "forever" by default) baked into the UI.
Five files touched, one new: `build.gradle.kts` (app module), `data/sync/TripShareManager.kt`
(NEW), `ui/viewmodel/JournalViewModel.kt`, `ui/screens/JournalListScreen.kt` (the `TripHeader`
composable), `strings.xml`.

---

## Change 1 — Add the Firebase Storage dependency

**Problem:** `DrivePhotoSync` talks to the Google Drive REST API directly for the private backup
feature — Firebase Storage isn't a dependency yet, but is needed here for the public photo copies
(same Firebase BOM already in use for Firestore/Auth/Analytics, so this is one more BOM-managed
line, not a version to pin manually).

```kotlin
// app/build.gradle.kts — ADD alongside the existing firebase-firestore / firebase-auth lines
implementation("com.google.firebase:firebase-storage-ktx")
```

**File:** `app/build.gradle.kts`.

---

## Change 2 — `TripShareManager` (NEW FILE)

Writes to a **top-level** `shared_trips/{shareId}` collection — deliberately not nested under
`users/{uid}` — so it can carry its own, much more permissive rules (Change 5) without touching
the private per-user rules the rest of the app relies on. Photos are capped at 12 per share: this
is a public souvenir page, not a full backup, and keeping the cap small bounds both Storage cost
and how much of a private trip a link exposes.

```kotlin
package com.houseofmmminq.macaco.data.sync

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.houseofmmminq.macaco.data.model.TravelEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Read-only, unauthenticated web links for a trip. See docs/code-brief-shared-trip-links.md for
 * why this is architecturally different from everything else in the app (public data, a new
 * Storage path, security rules this repo doesn't own) before changing this file.
 */
class TripShareManager {

    data class ShareResult(val shareId: String, val url: String)

    private companion object { private const val MAX_PHOTOS = 12 }

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val sharesCollection get() = firestore.collection("shared_trips")

    suspend fun createShareLink(
        ownerUid: String,
        tripName: String,
        entries: List<TravelEntry>,
        expiryDays: Int?   // null = never expires — the UI should not default to this, see Change 4
    ): Result<ShareResult> = withContext(Dispatchers.IO) {
        runCatching {
            val shareId = UUID.randomUUID().toString()
            val photoUrls = entries.flatMap { it.photoUris }.take(MAX_PHOTOS)
                .mapIndexedNotNull { i, uriString ->
                    runCatching {
                        val ref = storage.reference.child("shared_trips/$shareId/photo_$i.jpg")
                        ref.putFile(Uri.parse(uriString)).await()
                        ref.downloadUrl.await().toString()
                    }.getOrNull()
                }
            val publicEntries = entries.map { entry ->
                mapOf(
                    "title" to entry.title,
                    "location" to entry.location,
                    "dateMillis" to entry.dateMillis,
                    // Truncated — a public page, not the full private journal entry.
                    "description" to entry.description.take(400)
                )
            }
            val expiresAt = expiryDays?.let { System.currentTimeMillis() + TimeUnit.DAYS.toMillis(it.toLong()) }
            sharesCollection.document(shareId).set(
                mapOf(
                    "ownerUid" to ownerUid,
                    "tripName" to tripName,
                    "createdAt" to System.currentTimeMillis(),
                    "expiresAt" to expiresAt,
                    "photoUrls" to photoUrls,
                    "entries" to publicEntries
                )
            ).await()
            // trip/index.html (the actual viewer) is a separate web task — see this brief's Scope.
            ShareResult(shareId, "https://mictroid.github.io/macaco/trip/?id=$shareId")
        }
    }

    suspend fun revokeShareLink(shareId: String, photoCount: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            repeat(photoCount) { i ->
                runCatching { storage.reference.child("shared_trips/$shareId/photo_$i.jpg").delete().await() }
            }
            sharesCollection.document(shareId).delete().await()
        }
    }

    /** This user's own active shares (shareId to tripName), so the UI can show "already shared"
     *  per trip and offer revoke. Works under the Change 5 read rule, which doesn't restrict
     *  reads by owner — public link access requires that — so an authenticated owner querying
     *  their own docs by ownerUid is just an ordinary allowed read, no separate index needed. */
    suspend fun listMyShares(ownerUid: String): Result<List<Pair<String, String>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                sharesCollection.whereEqualTo("ownerUid", ownerUid).get().await()
                    .documents.map { it.id to (it.getString("tripName") ?: "") }
            }
        }
}
```

**File:** `data/sync/TripShareManager.kt` (new).

---

## Change 3 — ViewModel wiring

```kotlin
// ui/viewmodel/JournalViewModel.kt — ADD

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

New import: `com.houseofmmminq.macaco.data.sync.TripShareManager`.

**File:** `ui/viewmodel/JournalViewModel.kt`.

---

## Change 4 — "Share trip" dialog on `TripHeader`

**Problem:** no UI entry point exists. Add it next to the existing "Create Reel" affordance on
`TripHeader` (the same composable already wired to `viewModel.startReel`, ~line 440-448 in
`JournalListScreen.kt`).

```
┌─────────────────────────────────────┐
│  Share "Lisbon 2026"                 │
│                                       │
│  Anyone with this link can view      │
│  these entries and photos — even     │
│  without the app. You can revoke     │
│  access anytime.                     │
│                                       │
│  Link expires:                       │
│  ( ) 7 days  (•) 30 days  ( ) Never   │
│                                       │
│         [ Cancel ]  [ Create link ]  │
└─────────────────────────────────────┘
```

The disclosure text above is not optional — it's the in-app equivalent of the consent moment
this feature needs, independent of whatever the privacy-policy update (Change 5's third bullet)
ends up saying.

```kotlin
// ui/screens/JournalListScreen.kt — TripHeader gets a new onShare: () -> Unit parameter and a
// new IconButton (Icons.Filled.Share) next to the existing reel button. onShare opens a new
// private @Composable ShareTripDialog(tripName, entries, tripShareState, onCreate, onDismiss)
// AlertDialog with:
//   - the disclosure text above
//   - a RadioButton row for expiryDays: 7 / 30 / null, DEFAULT SELECTED = 30 (never-expire must
//     be an explicit choice, not the default)
//   - Creating state: a small progress indicator in place of the buttons
//   - Ready state: the URL in a read-only field + a "Copy" button (ClipboardManager) + a native
//     ACTION_SEND share button, same intent shape as the reel/year-recap share actions elsewhere
//   - Error state: the message + a Retry
```

**File:** `ui/screens/JournalListScreen.kt`.

---

## Change 5 — Security rules (proposed text — NOT applied by this brief)

These need to be pasted into the Firebase console (Firestore → Rules, and Storage → Rules) or
deployed via the Firebase CLI if this project ever adopts one. **No file in this repo currently
holds Firestore/Storage rules** — this brief doesn't create one, since a rules file this repo
doesn't deploy anywhere would be misleading. Treat the blocks below as the spec to hand to
whoever has console access.

```
// Firestore rules — add alongside whatever already governs /users/{uid}/...
match /shared_trips/{shareId} {
  allow read: if resource.data.expiresAt == null
    || request.time.toMillis() < resource.data.expiresAt;
  allow create: if request.auth != null && request.resource.data.ownerUid == request.auth.uid;
  allow update, delete: if request.auth != null && resource.data.ownerUid == request.auth.uid;
}
```

```
// Storage rules — add alongside whatever already governs the private per-user photo paths
match /shared_trips/{shareId}/{fileName} {
  allow read: if true;
  allow write, delete: if request.auth != null;
}
```

**Not a file in this repo — hand this text to whoever manages the Firebase console.**

---

## Localization

| Key | EN value |
|-----|----------|
| `trip_share_action` | Share |
| `trip_share_title` | Share "%1$s" |
| `trip_share_disclosure` | Anyone with this link can view these entries and photos — even without the app. You can revoke access anytime. |
| `trip_share_expiry_label` | Link expires |
| `trip_share_expiry_7d` | 7 days |
| `trip_share_expiry_30d` | 30 days |
| `trip_share_expiry_never` | Never |
| `trip_share_create` | Create link |
| `trip_share_copy` | Copy |
| `trip_share_copied` | Link copied |
| `trip_share_revoke` | Revoke access |
| `trip_share_revoked` | Link revoked |
| `trip_share_error_generic` | Couldn't create the link. Please try again. |

All 13 keys need all 11 languages.

---

## Scope

- **In (Android app only):** capped public photo upload, redacted Firestore doc, link
  create/copy/share/revoke UI with a mandatory expiry choice defaulting to 30 days, listing the
  user's own active shares per trip.
- **Out — not Android/Kotlin work:** the actual `trip/index.html` viewer page that renders a
  `shared_trips/{shareId}` document for an anonymous browser (Firebase JS SDK + the app's already-
  public Web API config — the same kind of static-page task as the existing
  `privacy-policy.html`/`r/index.html`, not something to build inside Android Studio).
- **Out — not something Code can apply:** the Firestore/Storage security rule changes in Change
  5. Until they're applied, `createShareLink()` will fail with a permission-denied error every
  time — **this brief is not shippable/testable until that manual step happens.**
- **Out — not attempted here:** the privacy-policy update disclosing this capability. Flag back
  to whoever owns `privacy-policy.html`/`terms-of-service.html` before this reaches real users —
  same category of change discussed earlier for any third-party data flow.
- **Out:** editing an existing share's photo set or expiry after creation — v1 is create once,
  revoke, recreate if something needs to change.
- **Out:** view analytics ("3 people opened this link") — would need the viewer page to write
  back, which is more moving parts than a v1 read-only page should take on.

---

## Verification

**Cannot be meaningfully verified until Change 5's rules are applied in the Firebase console** —
until then, expect every `createShareLink()` call to fail with a Firestore/Storage
permission-denied error. Once applied:

1. From a trip with 2+ entries and photos, open Share, confirm the disclosure text renders, leave
   the default 30-day expiry, tap Create link. Confirm a Firestore doc appears under
   `shared_trips/{id}` in the console with the expected fields and capped photo count, and the
   matching Storage objects exist under `shared_trips/{id}/`.
2. Copy the link and confirm the URL shape matches `.../trip/?id={shareId}` (the page itself
   won't exist yet until the separate web task ships — that's expected, not a bug in this brief).
3. Reopen Share for the same trip — confirm it shows the existing link (via `listMyShares`)
   instead of silently creating a second one.
4. Tap Revoke — confirm the Firestore doc and Storage objects are deleted, and reopening Share
   for that trip goes back to the "no link yet" state.
5. Create a link with the 7-day expiry, then manually edit `expiresAt` in the Firebase console to
   a past timestamp — confirm a read attempt against that document (e.g. via the Firebase console
   itself, simulating the rule) is denied, proving the expiry condition in the rule actually works
   before the viewer page depends on it.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add Firebase Storage dependency | `app/build.gradle.kts` |
| 2 | `TripShareManager`: create/revoke/list public trip shares | `TripShareManager.kt` (new) |
| 3 | `TripShareState` + create/revoke wiring | `JournalViewModel.kt` |
| 4 | Share dialog on `TripHeader`, mandatory expiry choice | `JournalListScreen.kt` |
| 5 | Proposed Firestore + Storage rules (apply via console, not in this repo) | — |
| — | 13 new string keys × 11 languages | `strings.xml` × 11 |
