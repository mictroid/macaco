package com.houseofmmminq.macaco.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.ui.components.MacacoBrandBlock
import com.houseofmmminq.macaco.data.model.entryYears
import com.houseofmmminq.macaco.data.model.toYearRecap
import com.houseofmmminq.macaco.data.sync.YearRecapRenderer
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel
import com.houseofmmminq.macaco.util.AppActions
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun YearInTravelScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val context = LocalContext.current
    val years = remember(entries) { entries.entryYears() }
    var selectedYear by remember(years) {
        mutableStateOf(years.firstOrNull() ?: Calendar.getInstance().get(Calendar.YEAR))
    }
    val recap = remember(entries, selectedYear) { entries.toYearRecap(selectedYear) }

    Scaffold(
        // Branded header runs edge-to-edge under the status bar, same pattern as SettingsScreen —
        // opt out of the default top inset and re-apply it inside the band via statusBarsPadding().
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(macacoBrandBackground())
                    .statusBarsPadding()
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart).padding(4.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = Color.White
                    )
                }
                MacacoBrandBlock(
                    isLandscape = false,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 2.dp, bottom = 10.dp)
                ) {
                    Text(
                        stringResource(R.string.year_recap_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            // Big gold year numeral — mirrors the export PNG's anchor. SplashGold (muted for
            // light backgrounds), not SplashGoldBright (reserved for the dark-teal screens).
            Text(
                selectedYear.toString(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            // Year selector — a handful of years, so a plain chip row keeps every year one tap away.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(years) { y ->
                    FilterChip(
                        selected = y == selectedYear,
                        onClick = { selectedYear = y },
                        label = { Text(y.toString()) }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            if (recap.entryCount == 0) {
                Text(
                    stringResource(R.string.year_recap_no_entries, selectedYear),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Stat grid in an elevated card (matches ProfileScreen's stats card), values in
                // gold to echo the exported PNG's stat treatment.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatItem(value = recap.entryCount.toString(), label = stringResource(R.string.profile_memories), valueColor = SplashGold)
                            StatItem(value = recap.tripCount.toString(), label = stringResource(R.string.profile_trips), valueColor = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatItem(value = recap.locationCount.toString(), label = stringResource(R.string.profile_locations), valueColor = MaterialTheme.colorScheme.primary)
                            StatItem(value = recap.mediaCount.toString(), label = stringResource(R.string.profile_media), valueColor = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recap.topMood?.let {
                        YearRecapHighlightChip(
                            icon = Icons.Filled.Mood,
                            text = stringResource(R.string.year_recap_top_mood, it),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    recap.topTag?.let {
                        YearRecapHighlightChip(
                            icon = Icons.Filled.Sell,
                            text = stringResource(R.string.year_recap_top_tag, it),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    recap.busiestMonth?.let {
                        YearRecapHighlightChip(
                            icon = Icons.Filled.CalendarMonth,
                            text = stringResource(R.string.year_recap_busiest_month, it),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            MacacoBrandBlock(isLandscape = false, collapsed = true)
            Text(
                "macaco",
                color = SplashGold,
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                textAlign = TextAlign.Center
            )
            Button(
                onClick = {
                    val uri = YearRecapRenderer(context).render(recap) ?: return@Button
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            context.getString(R.string.reel_share_caption, AppActions.REEL_SHARE_URL)
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(shareIntent, context.getString(R.string.year_recap_share_chooser))
                    )
                },
                enabled = recap.entryCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.year_recap_share))
            }
        }
        }
    }
}

@Composable
private fun YearRecapHighlightChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
