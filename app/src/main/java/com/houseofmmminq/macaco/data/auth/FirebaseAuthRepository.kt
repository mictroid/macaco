package com.houseofmmminq.macaco.data.auth

import android.content.Context
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(appContext: Context) : AuthRepository {

    private val appContext = appContext.applicationContext

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    override val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    // google-services.json + plugin initialises Firebase automatically at app start
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    init {
        _currentUser.value = auth.currentUser?.toUserProfile()
        auth.addAuthStateListener { _currentUser.value = it.currentUser?.toUserProfile() }
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

private fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> =
    onFailure { return Result.failure(transform(it)) }.let { this }
