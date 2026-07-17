package com.houseofmmminq.macaco.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.pm.PackageInfoCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.ui.components.MacacoBrandBlock
import com.houseofmmminq.macaco.ui.theme.macacoContentGutter
import com.houseofmmminq.macaco.util.AppActions

/** A named FAQ section: a themed icon, a teal section label, and its Q&A pairs (question id to answer id). */
private data class FaqSection(val titleRes: Int, val icon: ImageVector, val items: List<Pair<Int, Int>>)

/** FAQ grouped into named sections. String ids kept in sync with strings.xml. */
private val FAQ_SECTIONS = listOf(
    FaqSection(
        R.string.help_section_getting_started,
        Icons.Filled.Explore,
        listOf(
            R.string.help_faq_create_entry_q to R.string.help_faq_create_entry_a,
            R.string.help_faq_trips_q to R.string.help_faq_trips_a,
            R.string.help_faq_reset_password_q to R.string.help_faq_reset_password_a,
            R.string.help_faq_use_tags_q to R.string.help_faq_use_tags_a,
            R.string.help_faq_swipe_entries_q to R.string.help_faq_swipe_entries_a,
            R.string.help_faq_adventures_map_q to R.string.help_faq_adventures_map_a,
            R.string.help_faq_map_pins_q to R.string.help_faq_map_pins_a,
            R.string.help_faq_on_this_day_q to R.string.help_faq_on_this_day_a,
            R.string.help_faq_widget_q to R.string.help_faq_widget_a,
            // NEW: entry search
            R.string.help_faq_search_q to R.string.help_faq_search_a,
            // NEW: weather stamp
            R.string.help_faq_weather_q to R.string.help_faq_weather_a,
            // NEW: camera-roll suggested entries
            R.string.help_faq_suggestions_q to R.string.help_faq_suggestions_a,
        )
    ),
    FaqSection(
        R.string.help_section_media,
        Icons.Filled.Photo,
        listOf(
            R.string.help_faq_q_photos to R.string.help_faq_a_photos,
            R.string.help_faq_video_add_q to R.string.help_faq_video_add_a,
            R.string.help_faq_video_length_q to R.string.help_faq_video_length_a,
            R.string.help_faq_drive_connect_q to R.string.help_faq_drive_connect_a,
            R.string.help_faq_delete_drive_q to R.string.help_faq_delete_drive_a,
            R.string.help_faq_reorder_photos_q to R.string.help_faq_reorder_photos_a,
            R.string.help_faq_cover_q to R.string.help_faq_cover_a,
            R.string.help_faq_q_backup to R.string.help_faq_a_backup,
        )
    ),
    FaqSection(
        R.string.help_section_sync,
        Icons.Filled.Sync,
        listOf(
            R.string.help_faq_q_sync to R.string.help_faq_a_sync,
            R.string.help_faq_transfer_device_q to R.string.help_faq_transfer_device_a,
        )
    ),
    FaqSection(
        R.string.help_section_privacy,
        Icons.Outlined.Shield,
        listOf(
            R.string.help_faq_q_lock to R.string.help_faq_a_lock,
        )
    ),
    FaqSection(
        R.string.help_section_account,
        Icons.Filled.AccountCircle,
        listOf(
            // NEW: email verification gate (vc72)
            R.string.help_faq_verify_email_q to R.string.help_faq_verify_email_a,
            R.string.help_faq_delete_account_q to R.string.help_faq_delete_account_a,
        )
    ),
    FaqSection(
        R.string.help_section_premium,
        Icons.Filled.WorkspacePremium,
        listOf(
            R.string.help_faq_free_trial_q to R.string.help_faq_free_trial_a,
            R.string.help_faq_reel_q to R.string.help_faq_reel_a,
            R.string.help_faq_premium_benefits_q to R.string.help_faq_premium_benefits_a,
            R.string.help_faq_premium_broken_q to R.string.help_faq_premium_broken_a,
            // Question renamed; the existing billing/cancel answer copy is reused unchanged.
            R.string.help_faq_cancel_plan_q to R.string.help_faq_a_billing,
        )
    ),
    // NEW: Print Book export
    FaqSection(
        R.string.help_section_print_export,
        Icons.Filled.Print,
        listOf(
            R.string.help_faq_print_q to R.string.help_faq_print_a,
        )
    ),
    // NEW: Year in Travel recap
    FaqSection(
        R.string.help_section_year_recap,
        Icons.Filled.CalendarMonth,
        listOf(
            R.string.help_faq_year_recap_q to R.string.help_faq_year_recap_a,
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

    val scrollState = rememberScrollState()
    val collapsed by remember {
        derivedStateOf { scrollState.value > 24 }
    }
    val isLandscape = LocalConfiguration.current.screenHeightDp < 480

    // Collapsible FAQ sections: which section keys (FaqSection.titleRes) are currently collapsed.
    // Session-scoped only (remember, not rememberSaveable/DataStore) — matches the journal list's
    // trip/month collapse convention: a browsing convenience, not a persisted setting. Every fresh
    // screen open starts fully expanded.
    var collapsedSections by remember {
        mutableStateOf(FAQ_SECTIONS.map { it.titleRes }.toSet())
    }
    fun toggleSection(key: Int) {
        collapsedSections =
            if (key in collapsedSections) collapsedSections - key else collapsedSections + key
    }

    Scaffold(
        topBar = {
            // Collapsing brand header, matching the journal list: tall brand block at rest,
            // slim centred row when scrolled or in landscape; scroll-hides in landscape.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(macacoBrandBackground())
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .animateContentSize()
            ) {
                when {
                    collapsed -> {
                        // ── Collapsed (any orientation): slim bar, the macaco icon centred in
                        //    the brand fade — no wordmark. ──
                        MacacoBrandBlock(isLandscape = isLandscape, collapsed = true)
                    }
                    isLandscape -> {
                        // ── Landscape at rest: icon on its own centred row (matching Adventures
                        //    & the portrait block), wordmark + page label beneath it; back rides
                        //    the top-start corner. ──
                        Box(modifier = Modifier.fillMaxWidth()) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.align(Alignment.TopStart).size(40.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.common_back),
                                    tint = Color.White
                                )
                            }
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                MacacoBrandBlock(
                                    isLandscape = true,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = " · " + stringResource(R.string.help_title),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SplashGold.copy(alpha = 0.7f),
                                        maxLines = 1
                                    )
                                }
                                if (versionLabel.isNotEmpty()) {
                                    Text(
                                        text = stringResource(R.string.settings_version_value, versionLabel),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.65f)
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        // ── Portrait at rest: full expanded brand block. ──
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = Color.White
                            )
                        }
                        MacacoBrandBlock(
                            isLandscape = false,
                            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.help_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = SplashGold.copy(alpha = 0.85f)
                            )
                            if (versionLabel.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.settings_version_value, versionLabel),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)   // hoisted — see Change 6
                .padding(horizontal = macacoContentGutter(), vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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

            // ── FAQ, grouped into named, collapsible sections ──
            FAQ_SECTIONS.forEach { section ->
                val isCollapsed = section.titleRes in collapsedSections
                SectionHeader(
                    text = stringResource(section.titleRes),
                    icon = section.icon,
                    collapsed = isCollapsed,
                    onToggleCollapse = { toggleSection(section.titleRes) }
                )
                if (!isCollapsed) {
                    section.items.forEach { (q, a) ->
                        FaqCard(question = stringResource(q), answer = stringResource(a))
                    }
                }
            }

            // ── Get in touch ──
            SectionHeader(stringResource(R.string.help_get_in_touch), icon = Icons.Filled.MailOutline)
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
private fun SectionHeader(
    text: String,
    icon: ImageVector? = null,
    collapsed: Boolean = false,
    onToggleCollapse: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onToggleCollapse != null) Modifier.clickable(onClick = onToggleCollapse) else Modifier)
            .padding(top = 4.dp, bottom = 2.dp, start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (onToggleCollapse != null) {
            Icon(
                imageVector = if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = if (collapsed)
                    stringResource(R.string.common_expand) else stringResource(R.string.common_collapse),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
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
