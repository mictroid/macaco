package com.houseofmmminq.macaco.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import coil.compose.AsyncImage
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.data.model.TravelEntry
import com.houseofmmminq.macaco.data.model.locations
import com.houseofmmminq.macaco.data.model.tripNames
import com.houseofmmminq.macaco.data.sync.PrintBookExporter
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SelectionMode { TRIP, LOCATION, CUSTOM }

/** Which of the two special-photo slots a picker tap assigns to. */
private enum class PhotoSlot { COVER, FIRST_PAGE }

/** Resolve a photo for display, preferring the Drive-cached copy when the local URI is unreadable
 *  — same logic as EntryDetailScreen's private displayPhotoUri. */
private fun TravelEntry.printDisplayUri(index: Int, cached: Map<String, String>): String? =
    driveFileIds.getOrNull(index)?.takeIf { it.isNotEmpty() }?.let { cached[it] }
        ?: photoUris.getOrNull(index)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintExportScreen(
    viewModel: JournalViewModel,
    onBack: () -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val cachedDrivePhotos by viewModel.cachedDrivePhotos.collectAsState()
    val context = LocalContext.current

    var mode by remember { mutableStateOf(SelectionMode.CUSTOM) }
    var scopeValue by remember { mutableStateOf<String?>(null) }   // chosen trip name / location
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var title by remember { mutableStateOf("") }
    var coverPhotoUri by remember { mutableStateOf<String?>(null) }
    var firstPagePhotoUri by remember { mutableStateOf<String?>(null) }
    var firstPageCaption by remember { mutableStateOf("") }
    var photoSlot by remember { mutableStateOf(PhotoSlot.COVER) }

    val selectedEntries = remember(entries, selectedIds) {
        entries.filter { it.id in selectedIds }.sortedBy { it.dateMillis }
    }
    // Each pickable photo as (display-uri) — resolved through the Drive cache so Drive-only photos
    // still show as thumbnails and can be assigned to the cover / first page.
    val availablePhotos = remember(selectedEntries, cachedDrivePhotos) {
        selectedEntries.flatMap { entry ->
            entry.photoUris.indices.mapNotNull { i -> entry.printDisplayUri(i, cachedDrivePhotos) }
        }
    }

    // Sensible defaults for cover / first page the first time photos become available.
    LaunchedEffect(availablePhotos) {
        if (coverPhotoUri == null) coverPhotoUri = availablePhotos.firstOrNull()
        if (firstPagePhotoUri == null) {
            firstPagePhotoUri = availablePhotos.getOrNull(1) ?: availablePhotos.firstOrNull()
        }
    }

    fun applyScope(newMode: SelectionMode, value: String?) {
        mode = newMode
        scopeValue = value
        selectedIds = when (newMode) {
            SelectionMode.TRIP -> entries.filter { it.tripName == value }.map { it.id }.toSet()
            SelectionMode.LOCATION -> entries.filter { it.location == value }.map { it.id }.toSet()
            SelectionMode.CUSTOM -> selectedIds
        }
        if (title.isBlank()) title = value ?: context.getString(R.string.print_default_title)
    }

    val exportState by viewModel.printExportState.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }

    val createDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { dest ->
        if (dest != null) {
            viewModel.exportPrintBook(
                dest = dest,
                config = PrintBookExporter.BookConfig(
                    title = title,
                    coverPhotoUri = coverPhotoUri,
                    firstPagePhotoUri = firstPagePhotoUri,
                    firstPageCaption = firstPageCaption,
                    entries = selectedEntries
                )
            )
        }
    }

    // On a finished export, surface a Share action; consume the state afterward so it doesn't fire
    // again on recomposition.
    LaunchedEffect(exportState) {
        val ready = exportState as? JournalViewModel.PrintExportState.Ready ?: return@LaunchedEffect
        val msg = if (ready.photosSkipped > 0) {
            context.getString(R.string.print_export_done_warn, ready.photosSkipped)
        } else {
            context.getString(R.string.print_export_done, ready.pagesWritten)
        }
        val result = snackbarHost.showSnackbar(
            message = msg,
            actionLabel = context.getString(R.string.print_export_share)
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, ready.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(share, null))
        }
        viewModel.printExportConsumed()
    }

    val generating = exportState is JournalViewModel.PrintExportState.Generating

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.print_book_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.print_book_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // 1. Mode chips.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == SelectionMode.TRIP,
                        onClick = { applyScope(SelectionMode.TRIP, null) },
                        label = { Text(stringResource(R.string.print_select_by_trip)) }
                    )
                    FilterChip(
                        selected = mode == SelectionMode.LOCATION,
                        onClick = { applyScope(SelectionMode.LOCATION, null) },
                        label = { Text(stringResource(R.string.print_select_by_location)) }
                    )
                    FilterChip(
                        selected = mode == SelectionMode.CUSTOM,
                        onClick = { applyScope(SelectionMode.CUSTOM, null) },
                        label = { Text(stringResource(R.string.print_select_custom)) }
                    )
                }
            }

            // 2. Trip / location group picker.
            if (mode == SelectionMode.TRIP || mode == SelectionMode.LOCATION) {
                val groups = if (mode == SelectionMode.TRIP) entries.tripNames() else entries.locations()
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(groups) { value ->
                            FilterChip(
                                selected = value == scopeValue,
                                onClick = { applyScope(mode, value) },
                                label = { Text(value) }
                            )
                        }
                    }
                }
            }

            // 3. Entry checklist.
            item {
                Text(
                    stringResource(R.string.print_book_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(entries) { entry ->
                EntryChecklistRow(
                    entry = entry,
                    checked = entry.id in selectedIds,
                    thumbnailUri = entry.printDisplayUri(0, cachedDrivePhotos),
                    onToggle = {
                        selectedIds = if (entry.id in selectedIds) selectedIds - entry.id
                        else selectedIds + entry.id
                        if (mode != SelectionMode.CUSTOM) mode = SelectionMode.CUSTOM
                    }
                )
            }

            // 4. Cover & first-page picker (only once something is selected).
            if (selectedEntries.isNotEmpty() && availablePhotos.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = photoSlot == PhotoSlot.COVER,
                            onClick = { photoSlot = PhotoSlot.COVER },
                            label = { Text(stringResource(R.string.print_cover_photo)) }
                        )
                        FilterChip(
                            selected = photoSlot == PhotoSlot.FIRST_PAGE,
                            onClick = { photoSlot = PhotoSlot.FIRST_PAGE },
                            label = { Text(stringResource(R.string.print_first_page_photo)) }
                        )
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(availablePhotos) { uri ->
                            val isSelected = when (photoSlot) {
                                PhotoSlot.COVER -> uri == coverPhotoUri
                                PhotoSlot.FIRST_PAGE -> uri == firstPagePhotoUri
                            }
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (isSelected) Modifier.border(
                                            2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)
                                        ) else Modifier
                                    )
                                    .clickable {
                                        when (photoSlot) {
                                            PhotoSlot.COVER -> coverPhotoUri = uri
                                            PhotoSlot.FIRST_PAGE -> firstPagePhotoUri = uri
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            // 5. Title + caption fields.
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.print_book_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = firstPageCaption,
                    onValueChange = { firstPageCaption = it },
                    label = { Text(stringResource(R.string.print_first_page_caption_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 6. Create button.
            item {
                val canCreate = selectedEntries.isNotEmpty() && title.isNotBlank() && !generating
                Button(
                    onClick = { createDocLauncher.launch("${title.ifBlank { "macaco-book" }}.pdf") },
                    enabled = canCreate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (generating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.print_export_generating))
                    } else {
                        Text(stringResource(R.string.print_create_pdf))
                    }
                }
                if (selectedEntries.isEmpty()) {
                    Text(
                        stringResource(R.string.print_no_entries_selected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EntryChecklistRow(
    entry: TravelEntry,
    checked: Boolean,
    thumbnailUri: String?,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (thumbnailUri != null) {
                AsyncImage(
                    model = thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title.ifBlank { stringResource(R.string.print_default_title) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                val dateStr = remember(entry.dateMillis) {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(entry.dateMillis))
                }
                val sub = if (entry.location.isNotBlank()) "${entry.location} · $dateStr" else dateStr
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}
