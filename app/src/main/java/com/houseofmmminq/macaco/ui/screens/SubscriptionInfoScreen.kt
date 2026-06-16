package com.houseofmmminq.macaco.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel
import com.houseofmmminq.macaco.util.AppActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionInfoScreen(viewModel: JournalViewModel, onBack: () -> Unit) {
    val currentPlanId by viewModel.currentPlanId.collectAsState()
    val context = LocalContext.current
    // "Manage subscription" shows only for an actual Play subscription (monthly/annual). Lifetime
    // is a one-time purchase with nothing to cancel. This comes from the entitlement's store +
    // expiry, not the product id — both base plans share the one "macaco_premium" product id.
    val manageableSubscription by viewModel.manageableSubscription.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.common_subscription)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // Premium status band on the Macaco splash identity: teal radial behind the monkey
            // icon and gold "macaco" wordmark, with the premium/active/lifetime status beneath.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(macacoBrandBackground())
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(76.dp)
                )
                Text(
                    text = "macaco",
                    color = SplashGoldBright,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 5.sp,
                    modifier = Modifier.offset(y = (-6).dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.subscription_premium),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.subscription_active),
                    style = MaterialTheme.typography.labelLarge,
                    color = SplashGold,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(4.dp))
                val planLabel = when {
                    currentPlanId?.contains("annual") == true   -> stringResource(R.string.purchase_plan_annual)
                    currentPlanId?.contains("monthly") == true  -> stringResource(R.string.purchase_plan_monthly)
                    currentPlanId?.contains("lifetime") == true -> stringResource(R.string.purchase_plan_lifetime)
                    // A subscription whose cadence isn't encoded in the product id — don't mislabel
                    // it as "Lifetime"; show a neutral subscription label instead.
                    manageableSubscription -> stringResource(R.string.subscription_plan_recurring)
                    else -> stringResource(R.string.subscription_lifetime)
                }
                Text(
                    planLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                stringResource(R.string.subscription_whats_included),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    listOf(
                        stringResource(R.string.subscription_feature_unlimited),
                        stringResource(R.string.subscription_feature_photos),
                        stringResource(R.string.subscription_feature_no_ads),
                        stringResource(R.string.subscription_feature_local),
                        stringResource(R.string.subscription_feature_offline),
                        stringResource(R.string.subscription_feature_no_sub),
                        stringResource(R.string.subscription_feature_updates)
                    ).forEach { feature ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(feature, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                stringResource(R.string.subscription_thank_you),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (manageableSubscription) {
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = { AppActions.manageSubscriptions(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.subscription_manage))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
