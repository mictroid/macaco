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
}
