package com.example.myapplication.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.auth.FirebaseConfig
import com.example.myapplication.ui.viewmodel.JournalViewModel
import com.example.myapplication.ui.theme.isLightTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isCreatingAccount by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

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

    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLoading = false
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        runCatching {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken == null) {
                errorMessage = "Google sign-in failed: no ID token returned"
                return@rememberLauncherForActivityResult
            }
            isLoading = true
            viewModel.signInWithGoogleIdToken(idToken) { authResult ->
                isLoading = false
                authResult.fold(onSuccess = { onBack() }, onFailure = { errorMessage = it.message })
            }
        }.onFailure { e ->
            val apiException = e as? ApiException
            errorMessage = when (apiException?.statusCode) {
                12501 -> null // user cancelled — show nothing
                12500 -> "Google sign-in failed. Check your internet connection."
                10 -> "Google sign-in not configured. Verify the SHA-1 fingerprint in Firebase."
                else -> "Google sign-in failed (${apiException?.statusCode ?: e.message})"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
        ) {
            // Hero header. Light mode: vibrant accent band with light content; dark mode unchanged.
            val light = isLightTheme()
            val heroColors = if (light) {
                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
            } else {
                listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background)
            }
            val onHero = if (light) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Brush.verticalGradient(heroColors)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (light) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✈️", fontSize = 36.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Wanderlog",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = onHero
                    )
                    Text(
                        if (isCreatingAccount) "Create your account" else "Sign in to sync your memories",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (light) onHero.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Social sign-in
                OutlinedButton(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text("G  Continue with Google", fontWeight = FontWeight.Medium)
                    }
                }

                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        viewModel.signInWithApple(context) { result ->
                            isLoading = false
                            result.fold(onSuccess = { onBack() }, onFailure = { errorMessage = it.message })
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface
                    ),
                    enabled = !isLoading
                ) {
                    Text("  Continue with Apple", fontWeight = FontWeight.Medium)
                }

                // Error shown right below social buttons — always visible
                if (errorMessage != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            errorMessage!!,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text("or", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                // Email form
                AnimatedVisibility(
                    visible = isCreatingAccount,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Full name") },
                        leadingIcon = { Icon(Icons.Filled.Person, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Filled.Email, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Filled.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password"
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            errorMessage = "Please fill in all fields"
                            return@Button
                        }
                        isLoading = true
                        errorMessage = null
                        if (isCreatingAccount) {
                            viewModel.createAccount(email.trim(), password, displayName.trim()) { result ->
                                isLoading = false
                                result.fold(onSuccess = { onBack() }, onFailure = { errorMessage = it.message })
                            }
                        } else {
                            viewModel.signInWithEmail(email.trim(), password) { result ->
                                isLoading = false
                                result.fold(onSuccess = { onBack() }, onFailure = { errorMessage = it.message })
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            if (isCreatingAccount) "Create Account" else "Sign In",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                TextButton(
                    onClick = { isCreatingAccount = !isCreatingAccount; errorMessage = null },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        if (isCreatingAccount) "Already have an account? Sign in"
                        else "Don't have an account? Create one"
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
