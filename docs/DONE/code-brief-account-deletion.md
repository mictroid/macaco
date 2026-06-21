# Brief: In-App Account Deletion (GDPR Right to Erasure)

**Priority:** High (required for Play Store production release)  
**Files:**
- `app/src/main/java/com/houseofmmminq/macaco/ui/screens/ProfileScreen.kt`
- `app/src/main/java/com/houseofmmminq/macaco/data/auth/AuthRepository.kt`
- `app/src/main/java/com/houseofmmminq/macaco/data/auth/FirebaseAuthRepository.kt`
- `app/src/main/java/com/houseofmmminq/macaco/ui/viewmodel/JournalViewModel.kt`

## Rationale

GDPR Article 17 grants users the right to erasure ("right to be forgotten"). Play Store policy also
requires apps with accounts to provide an in-app path to delete the account and associated data.
Currently, users can only request deletion via email — this must be automated.

## What deletion must do (in order)

1. Delete all entries in the user's Firestore subcollection (`users/{uid}/entries/*`)
2. Delete the Firestore user document itself if one exists (`users/{uid}`)
3. Delete the Firebase Auth user account
4. Sign out locally (clear all ViewModel state)

Drive photos and RevenueCat subscription data are NOT deleted server-side (beyond our control
and not required by Play policy), but this must be documented in the Privacy Policy.

## Changes

### 1. Add `deleteAccount()` to AuthRepository interface

```kotlin
// In AuthRepository.kt
suspend fun deleteAccount(): Result<Unit>
```

### 2. Implement in FirebaseAuthRepository

```kotlin
override suspend fun deleteAccount(): Result<Unit> = runCatching {
    val user = Firebase.auth.currentUser ?: error("Not signed in")
    val uid = user.uid
    val db = Firebase.firestore

    // Delete all entries in the user's subcollection
    val entriesRef = db.collection("users").document(uid).collection("entries")
    val snapshot = entriesRef.get().await()
    val batch = db.batch()
    snapshot.documents.forEach { batch.delete(it.reference) }
    // Also delete the user document itself
    batch.delete(db.collection("users").document(uid))
    batch.commit().await()

    // Delete the Firebase Auth account
    user.delete().await()
}
```

Add import: `import kotlinx.coroutines.tasks.await`

Note: `user.delete()` may throw `FirebaseAuthRecentLoginRequiredException` if the user's session
is old. In production this is rare (users just signed in), but wrap the whole thing in `runCatching`
(already done above) so the `Result` captures it cleanly. The ViewModel will surface the error via
snackbar.

### 3. Add `deleteAccount()` to JournalViewModel

```kotlin
fun deleteAccount(onComplete: (Result<Unit>) -> Unit) {
    viewModelScope.launch {
        val result = authRepository.deleteAccount()
        onComplete(result)
    }
}
```

### 4. Add delete account UI to ProfileScreen

**a) Add parameter:**
```kotlin
fun ProfileScreen(
    ...existing params...,
    onDeleteAccount: (() -> Unit) -> Unit,  // takes a callback for when deletion completes
)
```

**b) Add state and dialog:**

```kotlin
var showDeleteDialog by remember { mutableStateOf(false) }
var deleteInProgress by remember { mutableStateOf(false) }
```

Add a "Delete Account" button near the bottom of the profile (below sign-out, clearly separated):

```kotlin
HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
Spacer(Modifier.height(8.dp))
TextButton(
    onClick = { showDeleteDialog = true },
    modifier = Modifier.fillMaxWidth(),
    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
) {
    Icon(Icons.Outlined.DeleteForever, contentDescription = null)
    Spacer(Modifier.width(8.dp))
    Text(stringResource(R.string.profile_delete_account))
}
```

**c) Confirmation dialog:**

```kotlin
if (showDeleteDialog) {
    AlertDialog(
        onDismissRequest = { if (!deleteInProgress) showDeleteDialog = false },
        title = { Text(stringResource(R.string.profile_delete_account_title)) },
        text = { Text(stringResource(R.string.profile_delete_account_body)) },
        confirmButton = {
            TextButton(
                onClick = {
                    deleteInProgress = true
                    onDeleteAccount { result ->
                        deleteInProgress = false
                        showDeleteDialog = false
                        // On success the auth state change causes NavGraph to navigate to LoginScreen.
                        // On failure the ViewModel surfaces the error via snackbar — nothing extra needed.
                    }
                },
                enabled = !deleteInProgress,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                if (deleteInProgress) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.profile_delete_account_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { showDeleteDialog = false }, enabled = !deleteInProgress) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
```

### 5. Wire in NavGraph

```kotlin
ProfileScreen(
    ...
    onDeleteAccount = { callback ->
        viewModel.deleteAccount { result ->
            callback(result)
            if (result.isFailure) {
                // surface error; auth state change handles navigation on success
            }
        }
    }
)
```

## New string resources

```xml
<string name="profile_delete_account">Delete Account</string>
<string name="profile_delete_account_title">Delete your account?</string>
<string name="profile_delete_account_body">This permanently deletes your Macaco account and all your journal entries. Photos saved to your device gallery and Google Drive are not affected. This cannot be undone.</string>
<string name="profile_delete_account_confirm">Delete</string>
```

Add translations to all supported language `strings.xml` files (using the same keys).

## Notes

- After `user.delete()` succeeds, Firebase Auth's state listener fires and `authRepository.currentUser`
  emits `null`, which causes NavGraph to automatically navigate to LoginScreen. No explicit navigation
  call is needed in the callback.
- If `user.delete()` throws `FirebaseAuthRecentLoginRequiredException`, the error surfaces via the
  ViewModel's `syncErrors` flow → snackbar. In practice this should not happen immediately after login.
- This satisfies Google Play's account deletion requirement for apps submitted to production.
