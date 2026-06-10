package com.example.myapplication.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.ui.viewmodel.JournalViewModel

@Composable
fun PurchaseScreen(viewModel: JournalViewModel) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val price by viewModel.priceLabel.collectAsState()

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val strUnableCheckout = stringResource(R.string.purchase_unable_checkout)
    val strNoPreviousPurchase = stringResource(R.string.purchase_no_previous)

    Box(modifier = Modifier.fillMaxSize()) {
        // Branded header band: the splash deep-teal radial behind the monkey icon and gold
        // "macaco" wordmark, matching the journal header, drawer, and login.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(330.dp)
                .background(macacoBrandBackground())
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))

            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(112.dp)
            )

            // Pull the wordmark up into the adaptive icon's padding so it sits snug.
            Column(
                modifier = Modifier.offset(y = (-16).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "macaco",
                    color = SplashGoldBright,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 8.sp
                )
                Text(
                    text = "Roam Freely. Forget Nothing.",
                    color = SplashGold.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                stringResource(R.string.purchase_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    FeatureRow("✓", stringResource(R.string.purchase_feature_unlimited))
                    FeatureRow("✓", stringResource(R.string.purchase_feature_photos))
                    FeatureRow("✓", stringResource(R.string.purchase_feature_sync))
                    FeatureRow("✓", stringResource(R.string.purchase_feature_no_ads))
                    FeatureRow("✓", stringResource(R.string.purchase_feature_no_sub))
                }
            }

            Spacer(Modifier.height(20.dp))

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
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = {
                    val act = activity
                    if (act == null) {
                        errorMessage = strUnableCheckout
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    viewModel.purchase(act) { result ->
                        isLoading = false
                        result.fold(
                            onSuccess = { /* entitlement state flips NavGraph automatically */ },
                            onFailure = { errorMessage = it.message }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        stringResource(R.string.purchase_unlock_for, price),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                stringResource(R.string.purchase_one_time),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    viewModel.restorePurchase { result ->
                        isLoading = false
                        result.fold(
                            onSuccess = { restored ->
                                if (!restored) errorMessage = strNoPreviousPurchase
                            },
                            onFailure = { errorMessage = it.message }
                        )
                    }
                },
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.purchase_restore), color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun FeatureRow(icon: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            icon,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
