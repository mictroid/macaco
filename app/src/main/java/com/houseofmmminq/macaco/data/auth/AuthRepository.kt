package com.houseofmmminq.macaco.data.auth

import com.houseofmmminq.macaco.data.model.UserProfile
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val currentUser: StateFlow<UserProfile?>

    /** idToken comes from GoogleSignIn intent result — composable handles the UI flow */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<UserProfile>
    suspend fun signInWithEmail(email: String, password: String): Result<UserProfile>
    suspend fun createAccount(email: String, password: String, displayName: String): Result<UserProfile>
    suspend fun signOut()

    /**
     * GDPR right to erasure. Re-authenticates FIRST (silently for Google; with [password] for
     * email accounts), then deletes the user's Firestore entries + user document, then the
     * Firebase Auth account — so data is never wiped unless the deletion can complete.
     * [password] is required for email/password accounts and ignored for Google accounts.
     */
    suspend fun deleteAccount(password: String? = null): Result<Unit>

    /**
     * Sends a Firebase password-reset email. Firebase deliberately reports success even when no
     * account exists for the address (to prevent email enumeration), so callers should use hedged
     * confirmation language.
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    /** Resends the verification email to the signed-in (unverified) user. */
    suspend fun sendEmailVerification(): Result<Unit>

    /** Reloads the current user from Firebase and returns the fresh isEmailVerified state. */
    suspend fun reloadAndCheckEmailVerified(): Result<Boolean>
}
