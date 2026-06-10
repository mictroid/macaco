package com.example.myapplication.data.auth

/**
 * Fill in these four values from Firebase Console (console.firebase.google.com):
 *
 *  PROJECT_ID        → Project Settings → General → Project ID
 *  APPLICATION_ID    → Project Settings → General → Your apps → App ID
 *                      (looks like  1:123456789:android:abcdef1234567890)
 *  API_KEY           → Project Settings → General → API key (Browser or Android key)
 *  GOOGLE_WEB_CLIENT_ID → Authentication → Sign-in method → Google →
 *                         Web SDK configuration → Web client ID
 *                         (looks like  123456789-xxxx.apps.googleusercontent.com)
 *
 * After filling these in, also:
 *  • Enable Email/Password, Google, and Apple in Firebase Console →
 *    Authentication → Sign-in method
 *  • For Google Sign-In: add your debug SHA-1 to Firebase Console →
 *    Project Settings → Your apps → Add fingerprint
 *    Run:  keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
 *  • For Apple Sign-In: requires an Apple Developer account and Service ID
 *    configured in both Firebase Console and Apple Developer Portal
 */
object FirebaseConfig {
    const val PROJECT_ID = "macaco-499016"
    const val APPLICATION_ID = "1:502845055894:android:0579d29d68f9e5421573b7"
    const val API_KEY = "AIzaSyBpHbabUV7hBeucnrQ4I-g1dNpr12LviXk"
    const val GOOGLE_WEB_CLIENT_ID = "502845055894-mcnohto1psitd5ls3burga1ncu5sk6ul.apps.googleusercontent.com"

    val isConfigured: Boolean
        get() = !APPLICATION_ID.startsWith("1:000000000000") && API_KEY != "your-api-key"
}
