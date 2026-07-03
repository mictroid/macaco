package com.houseofmmminq.macaco.data.auth

import android.content.Context
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.data.model.AuthProvider
import com.houseofmmminq.macaco.data.model.UserProfile
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(appContext: Context) : AuthRepository {

    private val appContext = appContext.applicationContext

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    override val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    // google-services.json + plugin initialises Firebase automatically at app start
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        _currentUser.value = auth.currentUser?.toUserProfile()
        auth.addAuthStateListener { _currentUser.value = it.currentUser?.toUserProfile() }
        // Reload once at startup to pick up any Google Account name changes since last sign-in.
        // reload() refreshes the FirebaseUser object in-place but does not trigger the auth
        // listener, so _currentUser must be updated manually afterwards. Offline → keeps cached name.
        repositoryScope.launch {
            runCatching { auth.currentUser?.reload()?.await() }
            auth.currentUser?.let { _currentUser.value = it.toUserProfile() }
        }
    }

    // ── Google ────────────────────────────────────────────────────────────────

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<UserProfile> =
        runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).await().user!!.toUserProfile()
        }.mapFailure { e ->
            Exception(e.localizedMessage ?: "Google sign-in failed")
        }

    // ── Email / Password ──────────────────────────────────────────────────────

    override suspend fun signInWithEmail(email: String, password: String): Result<UserProfile> =
        runCatching {
            auth.signInWithEmailAndPassword(email, password).await().user!!.toUserProfile()
        }.mapFailure { e ->
            when (e) {
                is FirebaseAuthInvalidCredentialsException -> Exception("Wrong email or password")
                is FirebaseAuthInvalidUserException -> Exception("No account found for this email")
                else -> Exception(e.localizedMessage ?: "Sign-in failed")
            }
        }

    override suspend fun createAccount(email: String, password: String, displayName: String): Result<UserProfile> =
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user!!
            val name = displayName.ifBlank { email.substringBefore("@").replaceFirstChar { it.uppercase() } }
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
            user.reload().await()
            user.toUserProfile()
        }.mapFailure { e ->
            when (e) {
                is FirebaseAuthWeakPasswordException -> Exception("Password must be at least 6 characters")
                is FirebaseAuthUserCollisionException -> Exception("An account already exists for this email")
                else -> Exception(e.localizedMessage ?: "Account creation failed")
            }
        }

    // ── Password Reset ────────────────────────────────────────────────────────

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        runCatching {
            auth.sendPasswordResetEmail(email.trim()).await()
            Unit
        }.mapFailure { e ->
            Exception(e.localizedMessage ?: "Could not send reset email")
        }

    // ── Sign Out ──────────────────────────────────────────────────────────────

    override suspend fun signOut() {
        auth.signOut()
        // Also clear the cached GMS Google account. Without this, the GoogleSignInClient keeps the
        // last-selected account and the next sign-in silently reuses it instead of showing the
        // account picker, so the user can never switch Google accounts.
        runCatching {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            GoogleSignIn.getClient(appContext, gso).signOut().await()
        }
        _currentUser.value = null
    }

    // ── Account deletion (GDPR) ─────────────────────────────────────────────────

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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun FirebaseUser.toUserProfile(): UserProfile {
        val provider = when {
            providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } -> AuthProvider.Google
            providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID } -> AuthProvider.Email
            else -> AuthProvider.Email
        }
        return UserProfile(
            uid = uid,
            displayName = displayName
                ?: email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                ?: "User",
            email = email ?: "",
            photoUrl = photoUrl?.toString(),
            provider = provider,
            createdAt = metadata?.creationTimestamp
        )
    }
}

/** Re-authentication failed before any data was touched — message is already user-facing. */
private class ReauthException(message: String) : Exception(message)

private fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> =
    onFailure { return Result.failure(transform(it)) }.let { this }
