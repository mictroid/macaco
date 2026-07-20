package com.houseofmmminq.macaco.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel
import kotlinx.coroutines.delay

@Composable
fun VerifyEmailScreen(
    viewModel: JournalViewModel,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    var isChecking by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var resendCooldown by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000)
            resendCooldown--
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "macaco",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.verify_email_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.verify_email_subtitle, currentUser?.email ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            if (errorMessage != null) {
                Text(
                    errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }
            if (infoMessage != null) {
                Text(
                    infoMessage!!,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    isChecking = true
                    errorMessage = null
                    viewModel.reloadAndCheckEmailVerified { result ->
                        isChecking = false
                        result.fold(
                            onSuccess = { verified ->
                                if (!verified) {
                                    errorMessage =
                                        context.getString(R.string.verify_email_still_unverified)
                                }
                            },
                            onFailure = { errorMessage = it.message }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isChecking
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.verify_email_continue))
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    isResending = true
                    viewModel.sendEmailVerification { result ->
                        isResending = false
                        result.fold(
                            onSuccess = {
                                infoMessage = null
                                resendCooldown = 30
                            },
                            onFailure = { errorMessage = it.message }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isResending && resendCooldown == 0
            ) {
                Text(
                    if (resendCooldown > 0)
                        stringResource(R.string.verify_email_resend_cooldown, resendCooldown)
                    else stringResource(R.string.verify_email_resend)
                )
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { viewModel.signOut(); onSignOut() }) {
                Text(stringResource(R.string.verify_email_sign_out))
            }
        }
    }
}
