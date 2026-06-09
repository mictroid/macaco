package com.example.myapplication.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.content.ContextWrapper
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current

    // Auto-trigger the biometric prompt as soon as this screen appears.
    LaunchedEffect(Unit) {
        showBiometricPrompt(context, onUnlocked)
    }

    // Same branded backdrop as the launch splash, so a locked launch feels continuous.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(macacoBrandBackground()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(150.dp)
            )
            Text(
                text = "macaco",
                color = SplashGoldBright,
                fontSize = 36.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 7.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))
            Text("🔒", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.app_lock_locked),
                color = SplashGold.copy(alpha = 0.82f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = { showBiometricPrompt(context, onUnlocked) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SplashGold,
                    contentColor = SplashTealEdge
                )
            ) {
                Text(stringResource(R.string.app_lock_unlock), modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

fun showBiometricPrompt(context: android.content.Context, onSuccess: () -> Unit) {
    val activity = context.findFragmentActivity() ?: return
    val executor = ContextCompat.getMainExecutor(context)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
    }
    val prompt = BiometricPrompt(activity, executor, callback)
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(context.getString(R.string.app_lock_biometric_title))
        .setSubtitle(context.getString(R.string.app_lock_biometric_subtitle))
        .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        .build()
    prompt.authenticate(info)
}

/** Returns true if the device has any usable authentication method (biometric or screen lock). */
fun isBiometricAvailable(context: android.content.Context): Boolean =
    BiometricManager.from(context)
        .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

/** Unwraps a Compose ContextWrapper chain to find the underlying FragmentActivity. */
private fun android.content.Context.findFragmentActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
