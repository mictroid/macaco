package com.example.myapplication.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.data.model.TravelEntry
import com.example.myapplication.ui.theme.heroGradientColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entry: TravelEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Entry") },
            text = {
                Text("Delete \"${entry.title}\"? This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { shareEntry(context, entry) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                if (entry.photoUris.isNotEmpty()) {
                    val pagerState = rememberPagerState(pageCount = { entry.photoUris.size })
                    Box {
                        HorizontalPager(state = pagerState) { page ->
                            AsyncImage(
                                model = entry.photoUris[page],
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                        if (entry.photoUris.size > 1) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                repeat(entry.photoUris.size) { index ->
                                    Box(
                                        modifier = Modifier
                                            .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (pagerState.currentPage == index) Color.White
                                                else Color.White.copy(alpha = 0.5f)
                                            )
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Brush.verticalGradient(heroGradientColors())),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(entry.mood.ifBlank { "🗺️" }, fontSize = 72.sp)
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (entry.mood.isNotBlank()) {
                            AssistChip(
                                onClick = {},
                                label = { Text(entry.mood) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                        if (entry.location.isNotBlank()) {
                            AssistChip(
                                onClick = {},
                                label = { Text(entry.location) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }

                    AssistChip(
                        onClick = {},
                        label = { Text(formatDate(entry.dateMillis)) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    if (entry.description.isNotBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            entry.description,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 28.sp
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

private fun shareEntry(context: Context, entry: TravelEntry) {
    val dateStr = formatDate(entry.dateMillis)
    val shareText = buildString {
        appendLine("✈️ ${entry.title}")
        if (entry.location.isNotBlank()) appendLine("📍 ${entry.location}")
        appendLine("📅 $dateStr")
        if (entry.mood.isNotBlank()) appendLine(entry.mood)
        if (entry.description.isNotBlank()) {
            appendLine()
            appendLine(entry.description)
        }
        appendLine()
        append("— Shared from Wanderlog")
    }

    // Photos live in app-internal storage (file:// URIs), which other apps can't read directly.
    // Expose each through our FileProvider so the share target gets a readable content:// URI.
    val authority = "${context.packageName}.fileprovider"
    val shareUris = ArrayList<Uri>(
        entry.photoUris.mapNotNull { uriString ->
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "file" -> uri.path?.let { path ->
                    runCatching { FileProvider.getUriForFile(context, authority, File(path)) }.getOrNull()
                }
                else -> uri // already a content:// URI — pass through
            }
        }
    )

    val intent = if (shareUris.isNotEmpty()) {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris)
            putExtra(Intent.EXTRA_TEXT, shareText)
            // A ClipData listing every URI is what propagates the read grant to all of them,
            // so the whole entry is shared as one multi-photo post (not one per image).
            clipData = ClipData.newUri(context.contentResolver, "Wanderlog photos", shareUris[0]).apply {
                for (i in 1 until shareUris.size) addItem(ClipData.Item(shareUris[i]))
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
    }
    context.startActivity(Intent.createChooser(intent, "Share your memory"))
}
