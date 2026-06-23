# Macaco — ProfileScreen: Use Google Account Photo as Fallback

When no custom profile photo is set (or after sign-out clears the local photo), the profile
screen falls back to a circle with the user's initials. Firebase already has the user's Google
account avatar in `currentUser.photoUrl` — use it as an intermediate fallback before initials.

File touched: `ui/screens/ProfileScreen.kt`.

---

## Change: three-tier photo display (custom → Google → initials)

**Problem:** `profilePhotoUri` holds a locally stored custom photo (`file://` URI persisted to
`filesDir`). It is cleared on sign-out so a new account doesn't inherit the previous user's
photo. After clearing, the profile shows initials even though Firebase has a perfectly good
Google account photo available in `user.photoUrl`. The user sees their avatar disappear after
every sign-out / sign-in cycle.

**Fix:** Add a middle tier: if `profilePhotoUri` is null but `user.photoUrl` is not null,
display the Google account photo with Coil `AsyncImage`. Initials are only shown when both
are unavailable (email/password users with no custom photo).

Find the photo display block in `ProfileScreen.kt` — the `Box` that renders the 100 dp circle
avatar (around the `if (profilePhotoUri != null)` check):

```kotlin
// BEFORE:
if (profilePhotoUri != null) {
    AsyncImage(
        model = profilePhotoUri,
        contentDescription = stringResource(R.string.profile_photo_cd),
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape),
        contentScale = ContentScale.Crop
    )
} else {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            user.displayName.take(2).uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

// AFTER:
val displayPhotoModel: Any? = profilePhotoUri ?: user.photoUrl

if (displayPhotoModel != null) {
    AsyncImage(
        model = displayPhotoModel,
        contentDescription = stringResource(R.string.profile_photo_cd),
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape),
        contentScale = ContentScale.Crop,
        // If the Google URL fails to load (offline, revoked), fall through to initials.
        error = rememberVectorPainter(Icons.Default.Person)
    )
} else {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            user.displayName.take(2).uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
```

**Import needed** (add if missing):
```kotlin
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
```

**Scope notes:**
- `profilePhotoUri` is a `file://` URI (locally stored custom photo). It takes priority.
- `user.photoUrl` is the Google account photo URL (HTTPS). Coil fetches it normally.
- If the Google photo URL fails to load (device offline), Coil shows the `Person` icon — not
  a crash. The initials fallback requires the full `else` branch above; the `error` painter
  handles the offline case within the `AsyncImage` path.
- This change only affects the avatar circle. The camera badge overlay immediately below it
  is unchanged.
- Email/password users who have never uploaded a profile photo have `user.photoUrl == null`,
  so they continue to see initials. No change for that case.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Use `profilePhotoUri ?: user.photoUrl` as the Coil model; show initials only when both are null; add `error` painter for offline Google photo loads | `ui/screens/ProfileScreen.kt` |
