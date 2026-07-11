package com.houseofmmminq.macaco.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.data.model.entryYears
import com.houseofmmminq.macaco.data.model.toYearRecap
import com.houseofmmminq.macaco.data.sync.YearRecapRenderer
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel
import com.houseofmmminq.macaco.util.AppActions
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.year_recap_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp)
        ) {
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
                // Stat grid mirroring ProfileScreen's card (reuses its StatItem composable).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(value = recap.entryCount.toString(), label = stringResource(R.string.profile_memories))
                    StatItem(value = recap.tripCount.toString(), label = stringResource(R.string.profile_trips))
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(value = recap.locationCount.toString(), label = stringResource(R.string.profile_locations))
                    StatItem(value = recap.mediaCount.toString(), label = stringResource(R.string.profile_media))
                }
                Spacer(Modifier.height(24.dp))
                recap.topMood?.let {
                    Text(
                        stringResource(R.string.year_recap_top_mood, it),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                recap.topTag?.let {
                    Text(
                        stringResource(R.string.year_recap_top_tag, it),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                recap.busiestMonth?.let {
                    Text(
                        stringResource(R.string.year_recap_busiest_month, it),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))
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
