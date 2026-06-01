package com.example.myapplication.data.model

data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val provider: AuthProvider = AuthProvider.Guest
)

enum class AuthProvider { Google, Apple, Email, Guest }
