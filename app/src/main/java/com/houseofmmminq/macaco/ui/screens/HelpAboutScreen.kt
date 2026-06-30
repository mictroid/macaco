package com.houseofmmminq.macaco.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.pm.PackageInfoCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.util.AppActions

/** A named FAQ section: a teal section label and its Q&A pairs (question id to answer id). */
private data class FaqSection(val titleRes: Int, val items: List<Pair<Int, Int>>)

/** FAQ grouped into named sections. String ids kept in sync with strings.xml. */
private val FAQ_SECTIONS = listOf(
    FaqSection(
        R.string.help_section_getting_started,
        listOf(
            R.string.help_faq_create_entry_q to R.string.help_faq_create_entry_a,
            R.string.help_faq_trips_q to R.string.help_faq_trips_a,
            R.string.help_faq_reset_password_q to R.string.help_faq_reset_password_a,
            R.string.help_faq_use_tags_q to R.string.help_faq_use_tags_a,
            R.string.help_faq_swipe_entries_q to R.string.help_faq_swipe_entries_a,
            R.string.help_faq_adventures_map_q to R.string.help_faq_adventures_map_a,
            R.string.help_faq_map_pins_q to R.string.help_faq_map_pins_a,
            R.string.help_faq_on_this_day_q to R.string.help_faq_on_this_day_a,
        )
    ),
    FaqSection(
        R.string.help_section_photos,
        listOf(
            R.string.help_faq_q_photos to R.string.help_faq_a_photos,
            R.string.help_faq_drive_connect_q to R.string.help_faq_drive_connect_a,
            R.string.help_faq_reorder_photos_q to R.string.help_faq_reorder_photos_a,
            R.string.help_faq_cover_q to R.string.help_faq_cover_a,
            R.string.help_faq_q_backup to R.string.help_faq_a_backup,
        )
    ),
    FaqSection(
        R.string.help_section_sync,
        listOf(
            R.string.help_faq_q_sync to R.string.help_faq_a_sync,
            R.string.help_faq_transfer_device_q to R.string.help_faq_transfer_device_a,
        )
    ),
    FaqSection(
        R.string.help_section_privacy,
        listOf(
            R.string.help_faq_q_lock to R.string.help_faq_a_lock,
        )
    ),
    FaqSection(
        R.string.help_section_account,
        listOf(
            R.string.help_faq_delete_account_q to R.string.help_faq_delete_account_a,
        )
    ),
    FaqSection(
        R.string.help_section_premium,
        listOf(
            R.string.help_faq_free_trial_q to R.string.help_faq_free_trial_a,
            R.string.help_faq_reel_q to R.string.help_faq_reel_a,
            R.string.help_faq_premium_benefits_q to R.string.help_faq_premium_benefits_a,
            R.string.help_faq_premium_broken_q to R.string.help_faq_premium_broken_a,
            // Question renamed; the existing billing/cancel answer copy is reused unchanged.
            R.string.help_faq_cancel_plan_q to R.string.help_faq_a_billing,
        )
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpAboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionLabel = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = PackageInfoCompat.getLongVersionCode(info)
            "${info.versionName} ($versionCode)"
        }.getOrNull().orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Branded header band: the Macaco splash identity (teal radial + gold wordmark)
            //    with the icon, slogan, and app version. ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(macacoBrandBackground())
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(88.dp)
                )
                Column(
                    modifier = Modifier.offset(y = (-12).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "macaco",
                        color = SplashGoldBright,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 6.sp
                    )
                    Text(
                        text = "Roam Freely. Forget Nothing.",
                        color = SplashGold.copy(alpha = 0.82f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        stringResource(R.string.settings_version_value, versionLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            // ── Feedback CTAs: quick paths to request a feature or report an issue ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeedbackCard(
                    icon = Icons.Filled.Lightbulb,
                    label = stringResource(R.string.help_request_feature),
                    modifier = Modifier.weight(1f),
                    onClick = { AppActions.requestFeature(context) }
                )
                FeedbackCard(
                    icon = Icons.Filled.BugReport,
                    label = stringResource(R.string.help_report_issue),
                    modifier = Modifier.weight(1f),
                    onClick = { AppActions.reportIssue(context) }
                )
            }

            // ── FAQ, grouped into named sections ──
            FAQ_SECTIONS.forEach { section ->
                SectionHeader(stringResource(section.titleRes))
                section.items.forEach { (q, a) ->
                    FaqCard(question = stringResource(q), answer = stringResource(a))
                }
            }

            // ── Get in touch ──
            SectionHeader(stringResource(R.string.help_get_in_touch))
            HelpActionRow(
                icon = Icons.Filled.Email,
                title = stringResource(R.string.help_contact),
                subtitle = AppActions.SUPPORT_EMAIL,
                onClick = { AppActions.contactSupport(context) }
            )
            HelpActionRow(
                icon = Icons.Filled.PrivacyTip,
                title = stringResource(R.string.help_privacy_policy),
                subtitle = stringResource(R.string.help_privacy_policy_subtitle),
                onClick = { AppActions.openUrl(context, AppActions.legalUrl(AppActions.PRIVACY_POLICY_URL)) }
            )
            HelpActionRow(
                icon = Icons.Outlined.Gavel,
                title = stringResource(R.string.help_terms_of_service),
                subtitle = stringResource(R.string.help_terms_of_service_subtitle),
                onClick = { AppActions.openUrl(context, AppActions.legalUrl(AppActions.TERMS_URL)) }
            )
            HelpActionRow(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = stringResource(R.string.help_open_listing),
                subtitle = stringResource(R.string.help_open_listing_subtitle),
                onClick = { AppActions.openPlayStoreListing(context) }
            )

            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
    )
}

@Composable
private fun FeedbackCard(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FaqCard(question: String, answer: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(question, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                answer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HelpActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
