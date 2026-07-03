# Macaco — Account Deletion: Re-authenticate Before Deleting (fixes half-deleted accounts)

Fixes QA finding M5 (`docs/qa-code-review-2026-07-03.md`): `deleteAccount()` wipes all
Firestore data first and only then calls `user.delete()` — which Firebase rejects with
`FirebaseAuthRecentLoginRequiredException` when the last sign-in is old (typically > 5 min).
Result today: journal data gone, auth account still exists, user sees a raw error. Touches
`AuthRepository.kt`, `FirebaseAuthRepository.kt`, `MockAuthRepository.kt`,
`JournalViewModel.kt`, `NavGraph.kt`, `ProfileScreen.kt`, `strings.xml` ×11.

**Approach:** re-authenticate FIRST, then delete data, then delete the account. With a fresh
re-auth, `user.delete()` cannot hit the recent-login error, and data is never wiped unless the
deletion can complete.

- **Google users:** silent re-auth — `GoogleSignIn.silentSignIn()` for a fresh idToken, no UI.
- **Email/password users:** the delete-confirmation dialog gains a password field; the
  password is passed down and used for `reauthenticate`.

---

## Change 1 — AuthRepository: deleteAccount takes an optional password

```kotlin
// BEFORE (AuthRepository.kt, ~line 15)
    /**
     * GDPR right to erasure: deletes the user's Firestore entries + user document, then the
     * Firebase Auth account. May fail with FirebaseAuthRecentLoginRequiredException if the
     * session is stale; the Result captures it for the caller to surface.
     */
    suspend fun deleteAccount(): Result<Unit>

// AFTER
    /**
     * GDPR right to erasure. Re-authenticates FIRST (silently for Google; with [password] for
     * email accounts), then deletes the user's Firestore entries + user document, then the
     * Firebase Auth account — so data is never wiped unless the deletion can complete.
     * [password] is required for email/password accounts and ignored for Google accounts.
     */
    suspend fun deleteAccount(password: String? = null): Result<Unit>
```

Update `MockAuthRepository`'s override to the new signature (body unchanged).

**Files:** `AuthRepository.kt`, `MockAuthRepository.kt`

---

## Change 2 — FirebaseAuthRepository: reauthenticate, then delete

```kotlin
// BEFORE (~line 116)
    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in")
        val uid = user.uid
        val db = FirebaseFirestore.getInstance()

        // Delete all entries in the user's subcollection. Chunk under Firestore's 500-op batch
        // limit in case a heavy user has many entries.
        val entries = db.collection("users").document(uid).collection("entries").get().await()
        entries.documents.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        // Delete the user document itself if present.
        db.collection("users").document(uid).delete().await()

        // Finally delete the Firebase Auth account. The auth state listener then emits null and
        // NavGraph navigates back to LoginScreen.
        user.delete().await()
        Unit
    }.mapFailure { e ->
        Exception(e.localizedMessage ?: "Could not delete account")
    }

// AFTER
    override suspend fun deleteAccount(password: String?): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in")

        // Re-authenticate FIRST. user.delete() requires a recent sign-in; doing this up front
        // guarantees the final step can't fail AFTER the data is already wiped.
        val isGoogle = user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        if (isGoogle) {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(FirebaseConfig.GOOGLE_WEB_CLIENT_ID)
                .requestEmail()
                .build()
            val account = GoogleSignIn.getClient(appContext, gso).silentSignIn().await()
            val idToken = account.idToken
                ?: throw ReauthException(appContext.getString(R.string.profile_delete_reauth_google_failed))
            user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
        } else {
            val email = user.email ?: error("No email on account")
            if (password.isNullOrBlank()) {
                throw ReauthException(appContext.getString(R.string.profile_delete_password_required))
            }
            user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        }

        val uid = user.uid
        val db = FirebaseFirestore.getInstance()

        // Delete all entries in the user's subcollection. Chunk under Firestore's 500-op batch
        // limit in case a heavy user has many entries.
        val entries = db.collection("users").document(uid).collection("entries").get().await()
        entries.documents.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        // Delete the user document itself if present.
        db.collection("users").document(uid).delete().await()

        // Finally delete the Firebase Auth account. The auth state listener then emits null and
        // NavGraph navigates back to LoginScreen.
        user.delete().await()
        Unit
    }.mapFailure { e ->
        when (e) {
            is ReauthException -> e // already user-facing + localized
            is FirebaseAuthInvalidCredentialsException ->
                Exception(appContext.getString(R.string.profile_delete_wrong_password))
            else -> Exception(e.localizedMessage ?: appContext.getString(R.string.profile_delete_account_error))
        }
    }
```

Add near the bottom of the file:

```kotlin
/** Re-authentication failed before any data was touched — message is already user-facing. */
private class ReauthException(message: String) : Exception(message)
```

New imports: `com.google.android.gms.auth.api.signin.GoogleSignInOptions` (already imported),
`com.google.firebase.auth.EmailAuthProvider` (already imported), `com.houseofmmminq.macaco.R`,
`com.houseofmmminq.macaco.data.auth.FirebaseConfig` (same package — no import needed).
`silentSignIn().await()` throws `ApiException` if interactive sign-in would be required —
that lands in the `else` branch of `mapFailure`, acceptable; the user can sign out/in and retry.

**File:** `FirebaseAuthRepository.kt`

---

## Change 3 — Plumb the password through ViewModel and NavGraph

```kotlin
// BEFORE (JournalViewModel, ~line 318)
    fun deleteAccount(onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onComplete(authRepository.deleteAccount()) }
    }

// AFTER
    fun deleteAccount(password: String? = null, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onComplete(authRepository.deleteAccount(password)) }
    }
```

```kotlin
// BEFORE (NavGraph.kt, Profile route, ~line 317)
                        onDeleteAccount = { callback -> viewModel.deleteAccount(callback) }

// AFTER
                        onDeleteAccount = { password, callback -> viewModel.deleteAccount(password, callback) }
```

**Files:** `JournalViewModel.kt`, `NavGraph.kt`

---

## Change 4 — ProfileScreen: password field in the delete dialog for email accounts

**Problem:** Email/password users have no way to supply their password for re-auth.

**Fix:** Change the callback signature and add a password field to the existing
delete-confirmation dialog, shown only when `provider == AuthProvider.Email`. Google users see
the dialog unchanged (silent re-auth needs no input).

```
┌──────────────────────────────────────┐
│ Delete account?                      │
│ This permanently deletes …           │
│ ┌──────────────────────────────────┐ │  ← only for Email accounts
│ │ 🔒 Confirm your password         │ │
│ └──────────────────────────────────┘ │
│ [error line, if any]                 │
│                  Cancel   DELETE     │
└──────────────────────────────────────┘
```

```kotlin
// BEFORE (parameter, ~line 96)
    onDeleteAccount: ((Result<Unit>) -> Unit) -> Unit

// AFTER
    onDeleteAccount: (String?, (Result<Unit>) -> Unit) -> Unit
```

```kotlin
// BEFORE (~line 224)
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { if (!deleteInProgress) { showDeleteAccountDialog = false; deleteError = null } },
            title = { Text(stringResource(R.string.profile_delete_account_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.profile_delete_account_body))
                    deleteError?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteInProgress = true
                        deleteError = null
                        onDeleteAccount { result ->

// AFTER
    var deletePassword by remember { mutableStateOf("") }
    if (showDeleteAccountDialog) {
        val isEmailAccount = currentUser?.provider == AuthProvider.Email
        AlertDialog(
            onDismissRequest = {
                if (!deleteInProgress) {
                    showDeleteAccountDialog = false; deleteError = null; deletePassword = ""
                }
            },
            title = { Text(stringResource(R.string.profile_delete_account_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.profile_delete_account_body))
                    if (isEmailAccount) {
                        Spacer(Modifier.height(12.dp))
                        // Re-auth is required before deletion; email accounts confirm with
                        // their password (Google accounts re-auth silently).
                        androidx.compose.material3.OutlinedTextField(
                            value = deletePassword,
                            onValueChange = { deletePassword = it; deleteError = null },
                            label = { Text(stringResource(R.string.profile_delete_password_label)) },
                            singleLine = true,
                            enabled = !deleteInProgress,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    deleteError?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteInProgress = true
                        deleteError = null
                        onDeleteAccount(deletePassword.takeIf { isEmailAccount }) { result ->
```

Also gate the confirm button so email users must type something:

```kotlin
// BEFORE (~line 252)
                    enabled = !deleteInProgress,

// AFTER
                    enabled = !deleteInProgress && (!isEmailAccount || deletePassword.isNotBlank()),
```

And clear the password on success/dismiss (`onSuccess = { showDeleteAccountDialog = false }` →
also `deletePassword = ""`; same in the dismissButton handler).

**File:** `ProfileScreen.kt`

---

## Change 5 — New strings (×11 languages)

| Key | EN value |
|-----|----------|
| `profile_delete_password_label` | Confirm your password |
| `profile_delete_password_required` | Enter your password to confirm deletion |
| `profile_delete_wrong_password` | Wrong password — please try again |
| `profile_delete_reauth_google_failed` | Couldn't verify your Google sign-in. Sign out, sign back in, then try again. |

(`profile_delete_account_error` already exists and stays the generic fallback.)

---

## Scope notes

- Deleting the user's **Drive "Macaco" folder** during account deletion is a separate decision
  (the photos live in *their* Drive, arguably theirs to keep) — out of scope, matches QA L2.
- RevenueCat data isn't deleted here; subscription management/erasure goes through
  Play/RevenueCat dashboards — out of scope.
- Test matrix: email account with correct password, wrong password (data must be untouched),
  Google account happy path, Google with revoked GMS session (expect the reauth-failed message,
  data untouched).

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `deleteAccount(password: String? = null)` in the interface | `AuthRepository.kt`, `MockAuthRepository.kt` |
| 2 | Re-auth first (silent Google / password email), then wipe, then delete; localized error mapping | `FirebaseAuthRepository.kt` |
| 3 | Pass password through | `JournalViewModel.kt`, `NavGraph.kt` |
| 4 | Password field + gating in the delete dialog for email accounts | `ProfileScreen.kt` |
| 5 | 4 new keys ×11 | `res/values*/strings.xml` |
