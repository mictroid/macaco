package com.houseofmmminq.macaco.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel
import com.revenuecat.purchases.Package

private enum class PlanSelection { Monthly, Annual, Lifetime }

@Composable
fun PurchaseScreen(
    viewModel: JournalViewModel,
    onBack: (() -> Unit)? = null,   // null = not dismissible (kept for any gated usage)
    showFreeLimitNote: Boolean = false
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val offerings by viewModel.offerings.collectAsState()

    var selectedPlan by remember { mutableStateOf(PlanSelection.Annual) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val strUnableCheckout = stringResource(R.string.purchase_unable_checkout)
    val strNoPreviousPurchase = stringResource(R.string.purchase_no_previous)

    // Prices from RevenueCat offerings; fall back to display strings if not loaded yet
    val monthlyPrice = offerings?.current?.monthly?.product?.price?.formatted ?: "$2.99"
    val annualPrice  = offerings?.current?.annual?.product?.price?.formatted  ?: "$17.99"
    val lifetimePrice = offerings?.current?.lifetime?.product?.price?.formatted ?: "$39.99"

    val selectedPkg: Package? = when (selectedPlan) {
        PlanSelection.Annual   -> offerings?.current?.annual
        PlanSelection.Monthly  -> offerings?.current?.monthly
        PlanSelection.Lifetime -> offerings?.current?.lifetime
    }

    val ctaLabel = when (selectedPlan) {
        PlanSelection.Monthly  -> stringResource(R.string.purchase_cta_monthly, monthlyPrice)
        PlanSelection.Annual   -> stringResource(R.string.purchase_cta_annual)
        PlanSelection.Lifetime -> stringResource(R.string.purchase_cta_lifetime, lifetimePrice)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(macacoBrandBackground())
        )

        Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(100.dp)
            )

            Column(
                modifier = Modifier.offset(y = (-12).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "macaco",
                    color = SplashGoldBright,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 7.sp
                )
                Text(
                    text = "Roam Freely. Forget Nothing.",
                    color = SplashGold.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.purchase_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            if (showFreeLimitNote) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.purchase_free_limit_reached),
                    style = MaterialTheme.typography.bodySmall,
                    color = SplashGoldBright,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(8.dp))

            val featureItems = listOf(
                Icons.Filled.AllInclusive to R.string.purchase_feature_unlimited,
                Icons.Filled.PhotoLibrary  to R.string.purchase_feature_photos,
                Icons.Filled.CloudSync     to R.string.purchase_feature_sync,
                Icons.Filled.Block         to R.string.purchase_feature_no_ads,
            )
            featureItems.forEach { (icon, labelRes) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = Color(0xFFE8B020), modifier = Modifier.size(28.dp))
                    Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Annual — highlighted as Best Value
            PlanCard(
                title = stringResource(R.string.purchase_plan_annual),
                subtitle = stringResource(R.string.purchase_free_trial, annualPrice),
                detail = stringResource(R.string.purchase_save_50),
                badge = stringResource(R.string.purchase_best_value),
                note = stringResource(R.string.purchase_trial_note),
                selected = selectedPlan == PlanSelection.Annual,
                isRecommended = true,
                onClick = { selectedPlan = PlanSelection.Annual }
            )

            Spacer(Modifier.height(6.dp))

            // Monthly
            PlanCard(
                title = stringResource(R.string.purchase_plan_monthly),
                subtitle = "$monthlyPrice ${stringResource(R.string.purchase_per_month)}",
                detail = stringResource(R.string.purchase_cancel_anytime),
                badge = null,
                note = null,
                selected = selectedPlan == PlanSelection.Monthly,
                onClick = { selectedPlan = PlanSelection.Monthly }
            )

            Spacer(Modifier.height(6.dp))

            // Lifetime
            PlanCard(
                title = stringResource(R.string.purchase_plan_lifetime),
                subtitle = "$lifetimePrice ${stringResource(R.string.purchase_once)}",
                detail = stringResource(R.string.purchase_own_forever),
                badge = null,
                note = if (selectedPlan == PlanSelection.Lifetime)
                    stringResource(R.string.purchase_one_time) else null,
                selected = selectedPlan == PlanSelection.Lifetime,
                onClick = { selectedPlan = PlanSelection.Lifetime }
            )

            Spacer(Modifier.height(12.dp))

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
                    if (act == null) { errorMessage = strUnableCheckout; return@Button }
                    val pkg = selectedPkg
                    if (pkg == null) { errorMessage = strUnableCheckout; return@Button }
                    isLoading = true
                    errorMessage = null
                    viewModel.purchase(act, pkg) { result ->
                        isLoading = false
                        result.fold(
                            onSuccess = { /* entitlement flip drives NavGraph */ },
                            onFailure = { errorMessage = it.message }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0E5A6B)
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        ctaLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            } // end scrollable content

            // Fixed footer — pinned below the scroll area, always visible.
            // Restore is merged in here as an inline link to save a full row.
            // No branding icon here — the wordmark + full icon already sit at the
            // top, and ic_macaco_small loses all detail at this size.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 8.dp, bottom = 16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.purchase_footer_no_fees),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                    Text(
                        " · ",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                    Text(
                        stringResource(R.string.purchase_restore),
                        style = MaterialTheme.typography.bodySmall,
                        color = SplashGoldBright,
                        modifier = Modifier.clickable(enabled = !isLoading) {
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
                        }
                    )
                }
            }
        }

        // Drawn last so it sits above the scrollable content Column and stays tappable.
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    subtitle: String,
    detail: String,
    badge: String?,
    note: String?,
    selected: Boolean,
    isRecommended: Boolean = false,
    onClick: () -> Unit
) {
    val border = if (selected) BorderStroke(2.dp, Color(0xFF0E5A6B))
                 else BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))

    // Every card gets an explicit dark background so they read correctly against the
    // dark teal paywall regardless of the system light/dark theme. `isRecommended`
    // is the Annual card, which keeps a subtle passive highlight when unselected.
    val containerColor = when {
        selected && isRecommended -> Color(0xFF1A3A45)  // Annual selected
        isRecommended             -> Color(0xFF112830)  // Annual unselected — subtle highlight
        selected                  -> Color(0xFF1A3A45)  // Monthly / Lifetime selected
        else                      -> Color(0xFF0D1F25)  // Monthly / Lifetime unselected — darkest
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(border, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondary)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = SplashGoldBright)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            if (note != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
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
