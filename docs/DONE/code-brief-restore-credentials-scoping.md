# Macaco — Auth: Restore Credentials (Google Play zero-tap sign-in requirement) — Scoping Brief

This is a **scoping/decision brief, not a ready-to-implement code change**. Google's Aug 2026 Play
quality update (`android-developers.googleblog.com/2026/08/app-quality-memory-optimization-secure-onboarding.html`)
requires apps that support user authentication to automatically restore sign-in state on a new
device via the Android **Restore Credentials API**, enforced from **April 2027**. Read this before
starting any implementation — the real blocker isn't Kotlin code, it's a missing piece of backend
infrastructure. Do not write client code until the "Decision needed" section below is resolved
with Michael.

---

## Current auth architecture (confirmed by reading the source)

`app/src/main/java/com/houseofmmminq/macaco/ui/screens/LoginScreen.kt` (~line 103):

```kotlin
// Standard Google Sign-In via intent — avoids Credential Manager cancellation issues.
// DRIVE_FILE scope is requested here so photo sync is authorized on first sign-in.
val googleSignInClient = remember {
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(FirebaseConfig.GOOGLE_WEB_CLIENT_ID)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_FILE))
        .build()
    GoogleSignIn.getClient(context, gso)
}
```

`app/src/main/java/com/houseofmmminq/macaco/data/auth/FirebaseAuthRepository.kt` (~line 54):

```kotlin
override suspend fun signInWithGoogleIdToken(idToken: String): Result<UserProfile> =
    runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await().user!!.toUserProfile()
    }.mapFailure { e ->
        Exception(e.localizedMessage ?: "Google sign-in failed")
    }
```

Macaco uses the **legacy GMS `GoogleSignIn` intent**, deliberately chosen to avoid Credential
Manager cancellation issues, and the same GMS account backs both Firebase Auth and
`DrivePhotoSync.isDriveConnected()`. Email/password sign-in is also supported
(`signInWithEmail`). There is no custom backend — `firebase.json` shows only Firestore rules,
no Cloud Functions, no app server of any kind.

`minSdk = 24`, `targetSdk = 36` (`app/build.gradle.kts`).

---

## What Restore Credentials actually requires

Researched from `developer.android.com/identity/sign-in/restore-credentials` and
`restore-credentials-implementation` (Aug 2026):

- **Good news:** Restore Credentials is *independent* of the sign-in method. Google's own docs
  say it works alongside passwords, Sign in with Google, and legacy GoogleSignIn — **no migration
  off the legacy GoogleSignIn intent is required**. The "avoids Credential Manager cancellation
  issues" decision in `LoginScreen.kt` does not need to be revisited.
- **The real requirement:** Restore Credentials is built on WebAuthn/FIDO. To create a restore
  key, the client calls `credentialManager.createCredential()` with a
  `CreateRestoreCredentialRequest` whose `requestJson` is a
  `PublicKeyCredentialCreationOptionsJSON` — **this JSON has to come from a server that speaks
  the WebAuthn relying-party protocol**. On the new device, the client calls
  `credentialManager.getCredential()` with a `GetRestoreCredentialOption`, gets back a
  `RestoreCredential` (a public-key credential), and that has to be verified **server-side**
  against the original registration.
- Firebase Auth (Identity Platform) does not natively speak WebAuthn/FIDO for this purpose as
  far as this research could confirm. **Macaco has no backend to be that relying party.**
  Standing one up (or adopting a hosted FIDO/passkey relying-party service) is the actual scope
  of this work — not a Kotlin change.
- Requirements on the device side: Android 9+ (`minSdk = 24` is fine), Google Account signed in,
  Android data backup enabled, and a screen-lock mechanism set. Devices without those get
  `E2eeUnavailableException` and should fall back gracefully (`isCloudBackupEnabled = false`, or
  just skip — restore keys are additive, not a replacement for normal sign-in).
- Restore keys are deleted on uninstall, app-data clear, or an explicit
  `clearCredentialState(TYPE_CLEAR_RESTORE_CREDENTIAL)` call on sign-out.
- Only one account per app can be restored, and only on the first profile set up on a new device
  — fine for Macaco's single-account model.

---

## Decision needed (before any code is written)

The blocker is infrastructure, not UI. Michael needs to decide one of:

1. **Stand up a minimal WebAuthn relying-party backend** (e.g. a small Cloud Function using a
   FIDO2/WebAuthn library) purely to issue/verify restore-key challenges. Real but bounded scope
   — this does not need to be a full passkey system, just enough to satisfy Restore Credentials'
   registration/authentication ceremony.
2. **Use a hosted passkey/FIDO relying-party service** (there are third-party providers that
   handle the WebAuthn ceremony so Macaco doesn't have to run its own verifier) — trades a new
   vendor dependency for less backend work.
3. **Wait and re-check closer to the April 2027 deadline** — there's ~8 months of runway, Play's
   enforcement mechanics (what "reduced visibility" actually means for apps that don't comply)
   may get clearer, and Firebase/Google may ship an easier path (e.g. a Firebase-native restore
   credential integration) before then.

Given there's no urgency yet and the actual engineering cost is a backend build, not a quick
Compose change, **recommend option 3 for now** — revisit this brief in Q1 2027 once Play Console
starts surfacing compliance status for this requirement, and check whether Firebase has shipped
first-party support in the interim.

---

## Summary

| # | Change | File |
|---|--------|------|
| — | No code change in this brief — infra/backend decision required first | — |

Once a backend approach is chosen, this brief should be superseded by a real implementation
brief covering: the `androidx.credentials` / `androidx.credentials:credentials-play-services-auth`
dependency bump, the `createCredential()` call site (likely right after successful
`signInWithGoogleIdToken` / `signInWithEmail` in `FirebaseAuthRepository`), the `getCredential()`
call site on app launch (gated behind a "restore key exists?" check), and the
`clearCredentialState()` call in `signOut()`.
