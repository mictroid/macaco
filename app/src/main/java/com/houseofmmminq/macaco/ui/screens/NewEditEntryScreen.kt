package com.houseofmmminq.macaco.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.data.model.TravelEntry
import com.houseofmmminq.macaco.ui.components.MacacoWatermarkBackground
import com.houseofmmminq.macaco.util.Cities
import com.houseofmmminq.macaco.util.ImageStorage
import com.houseofmmminq.macaco.util.SUGGESTED_TAGS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private val MOODS = listOf("😊", "🌟", "😎", "🏔️", "🌊", "🌺", "✨", "🎭", "🍜", "🏛️", "🌅", "❤️")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEditEntryScreen(
    existingEntry: TravelEntry?,
    onSave: (TravelEntry) -> Unit,
    onBack: () -> Unit,
    locationSuggestions: List<String> = emptyList(),
    tagSuggestions: List<String> = emptyList(),
    tripSuggestions: List<String> = emptyList()
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf(existingEntry?.title ?: "") }
    var location by remember { mutableStateOf(existingEntry?.location ?: "") }
    var dateMillis by remember { mutableLongStateOf(existingEntry?.dateMillis ?: System.currentTimeMillis()) }
    var mood by remember { mutableStateOf(existingEntry?.mood ?: "") }
    var description by remember { mutableStateOf(existingEntry?.description ?: "") }
    var photoUris by remember { mutableStateOf(existingEntry?.photoUris ?: emptyList()) }
    var tags by remember { mutableStateOf(existingEntry?.tags ?: emptyList()) }
    var tripName by remember { mutableStateOf(existingEntry?.tripName ?: "") }
    var titleError by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    // Files we copied into storage this session. Any that aren't committed via Save (removed again,
    // or the screen is dismissed) must be deleted so picker copies don't leak. Committed-photo
    // cleanup on edit/delete happens in the ViewModel instead.
    var sessionAdded by remember { mutableStateOf(emptySet<String>()) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)

    var showPhotoSourceDialog by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        // Copy each picked image into the shared Pictures collection so it survives both relaunches
        // (the Photo Picker grant is temporary) and uninstalls. See ImageStorage.persistToGallery.
        val stored = uris.mapNotNull { ImageStorage.persistToGallery(context, it) }
        sessionAdded = sessionAdded + stored
        photoUris = (photoUris + stored).distinct()
    }

    // Camera capture: the camera app writes into a FileProvider temp file, which we then copy into
    // the shared gallery (same destination as picked photos) so it's Drive-syncable and persists.
    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val captured = pendingCameraUri
        if (success && captured != null) {
            ImageStorage.persistToGallery(context, captured)?.let { stored ->
                sessionAdded = sessionAdded + stored
                photoUris = (photoUris + stored).distinct()
            }
        }
        ImageStorage.clear(context, ImageStorage.CAMERA_TEMP)
        pendingCameraUri = null
    }
    val launchCamera = {
        ImageStorage.newCameraTempUri(context)?.let { uri ->
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    // Speech-to-text: result is appended to the description with a space separator.
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!text.isNullOrEmpty()) {
                description = if (description.isBlank()) text else "$description $text"
            }
        }
    }

    // Dismissing without Save: drop any files added this session, since they were never committed.
    val cancel = {
        ImageStorage.delete(context, sessionAdded)
        onBack()
    }
    BackHandler(onBack = cancel)

    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = { Text(stringResource(R.string.new_entry_add_photo)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoSourceDialog = false
                                launchCamera()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.new_entry_photo_take), style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoSourceDialog = false
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.new_entry_photo_choose), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoSourceDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMillis = it }
                    showDatePicker = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (existingEntry == null) stringResource(R.string.common_new_entry) else stringResource(R.string.common_edit_entry),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = cancel) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (title.isBlank()) {
                                titleError = true
                            } else {
                                onSave(
                                    TravelEntry(
                                        id = existingEntry?.id ?: UUID.randomUUID().toString(),
                                        title = title.trim(),
                                        location = location.trim(),
                                        dateMillis = dateMillis,
                                        description = description.trim(),
                                        mood = mood,
                                        photoUris = photoUris,
                                        tags = tags,
                                        createdAt = existingEntry?.createdAt ?: System.currentTimeMillis(),
                                        driveFileIds = existingEntry?.driveFileIds ?: emptyList(),
                                        tripName = tripName.trim().ifBlank { null }
                                    )
                                )
                            }
                        }
                    ) {
                        Text(stringResource(R.string.common_save), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        MacacoWatermarkBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Photos row
            item {
                SectionLabel(stringResource(R.string.new_entry_photos_label))
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Add button
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showPhotoSourceDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.new_entry_add_photo_cd),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.new_entry_add_photo),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    photoUris.forEachIndexed { index, uri ->
                        Box {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .clickable {
                                        photoUris = photoUris.toMutableList().also { it.removeAt(index) }
                                        // If this was added this session it was never committed, so
                                        // delete its file now. Pre-existing photos are left to the
                                        // ViewModel to clean up only once the removal is saved.
                                        if (uri in sessionAdded) {
                                            ImageStorage.delete(context, listOf(uri))
                                            sessionAdded = sessionAdded - uri
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.new_entry_remove_photo_cd),
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
                if (photoUris.isEmpty()) {
                    HintRow(Icons.Filled.PhotoCamera, stringResource(R.string.new_entry_hint_photos))
                }
            }

            // Title
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; titleError = false },
                    label = { Text(stringResource(R.string.new_entry_title_label)) },
                    placeholder = { Text(stringResource(R.string.new_entry_title_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = titleError,
                    supportingText = if (titleError) {
                        { Text(stringResource(R.string.new_entry_title_required)) }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // Location (with autocomplete from previously used locations)
            item {
                LocationField(
                    value = location,
                    onValueChange = { location = it },
                    suggestions = locationSuggestions
                )
            }

            // Trip (optional — groups this entry with others from the same named trip)
            item {
                TripField(
                    value = tripName,
                    onValueChange = { tripName = it },
                    suggestions = tripSuggestions
                )
            }

            // Date picker
            item {
                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.new_entry_date_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatDate(dateMillis),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Mood selector
            item {
                SectionLabel(stringResource(R.string.new_entry_mood_label))
                Spacer(Modifier.height(8.dp))
                MoodSelector(selectedMood = mood, onMoodSelected = { mood = it })
            }

            // Description
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.new_entry_description_label)) },
                    placeholder = { Text(stringResource(R.string.new_entry_description_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    maxLines = Int.MAX_VALUE,
                    trailingIcon = {
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.new_entry_dictate_prompt))
                            }
                            speechLauncher.launch(intent)
                        }) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = stringResource(R.string.new_entry_dictate_cd),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
                if (description.isEmpty()) {
                    HintRow(Icons.Filled.Mic, stringResource(R.string.new_entry_hint_story))
                }
            }

            // Tags
            item {
                SectionLabel(stringResource(R.string.new_entry_tags_label))
                Spacer(Modifier.height(8.dp))
                TagsField(
                    tags = tags,
                    onTagsChange = { tags = it },
                    suggestions = tagSuggestions
                )
                if (tags.isEmpty()) {
                    HintRow(null, stringResource(R.string.new_entry_hint_tags))
                }
            }

            // Suggested tag chips — tap to add; already-added chips are de-emphasised
            item {
                SuggestedTagsRow(
                    currentTags = tags,
                    onAdd = { raw ->
                        val tag = normalizeTag(raw)
                        if (tag.isNotEmpty() && tag !in tags) tags = tags + tag
                    }
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
        }
    }
}

/**
 * Normalize raw tag text into an Instagram-style hashtag body: drop a leading '#', lowercase, and
 * keep only letters/digits/underscore. Returns "" if nothing usable remains.
 */
internal fun normalizeTag(raw: String): String =
    raw.trim().removePrefix("#").lowercase()
        .filter { it.isLetterOrDigit() || it == '_' }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TagsField(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    suggestions: List<String>
) {
    var input by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    fun commit(raw: String) {
        val tag = normalizeTag(raw)
        if (tag.isNotEmpty() && tag !in tags) onTagsChange(tags + tag)
        input = ""
        expanded = false
    }

    // Previously used tags come first (most-used order); preset suggestions fill the rest.
    // Tags already on this entry are hidden. When the field is empty the dropdown opens with
    // the most relevant tags immediately visible.
    val allSuggestions = remember(suggestions) {
        (suggestions + SUGGESTED_TAGS.map { normalizeTag(it) }).distinctBy { it }
    }
    val matches = remember(input, tags, allSuggestions) {
        val q = normalizeTag(input)
        val unused = allSuggestions.filterNot { it in tags }
        if (q.isEmpty()) unused.take(8)
        else unused.filter { it.startsWith(q) }.take(8)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Autocomplete dropdown of existing tags, mirroring the location field.
        ExposedDropdownMenuBox(
            expanded = expanded && matches.isNotEmpty(),
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    // A space or comma ends a tag, mirroring how hashtags are typed.
                    if (it.endsWith(" ") || it.endsWith(",")) commit(it)
                    else {
                        input = it
                        expanded = true
                    }
                },
                label = { Text(stringResource(R.string.new_entry_add_tag_label)) },
                placeholder = { Text(stringResource(R.string.new_entry_add_tag_placeholder)) },
                leadingIcon = { Text("#", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (input.isNotBlank()) {
                        IconButton(onClick = { commit(input) }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_entry_add_tag_cd))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit(input) }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
            )
            ExposedDropdownMenu(
                expanded = expanded && matches.isNotEmpty(),
                onDismissRequest = { expanded = false }
            ) {
                matches.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text("#$suggestion") },
                        onClick = { commit(suggestion) }
                    )
                }
            }
        }

        // Chosen tags, each removable by tapping its ✕.
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { onTagsChange(tags - tag) },
                        label = { Text("#$tag") },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.new_entry_remove_tag_cd),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    // Bundled city list, loaded once off the main thread (see Cities).
    var cities by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        cities = withContext(Dispatchers.IO) { Cities.all(context) }
    }
    // As the user types, suggest their previously used locations plus matching world cities,
    // both by prefix ("Ber" → Berlin, Bern, Bergen). Past locations come first; an exact match
    // is dropped so the menu doesn't just echo what's already typed.
    val matches = remember(value, suggestions, cities) {
        val q = value.trim()
        if (q.isBlank()) emptyList()
        else {
            val past = suggestions.filter { it.startsWith(q, ignoreCase = true) }
            (past + Cities.search(cities, q, limit = 8))
                .distinctBy { it.lowercase() }
                .filterNot { it.equals(q, ignoreCase = true) }
                .take(6)
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && matches.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(stringResource(R.string.new_entry_location_label)) },
            placeholder = { Text(stringResource(R.string.new_entry_location_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded && matches.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            matches.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>
) {
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(value, suggestions) {
        val q = value.trim()
        if (q.isBlank()) suggestions.take(5)
        else suggestions
            .filter { it.contains(q, ignoreCase = true) }
            .filterNot { it.equals(q, ignoreCase = true) }
            .take(5)
    }

    ExposedDropdownMenuBox(
        expanded = expanded && matches.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text("Trip") },
            placeholder = { Text("e.g. Thailand 2026") },
            leadingIcon = {
                Text("✈️", fontSize = 16.sp, modifier = androidx.compose.ui.Modifier.padding(start = 4.dp))
            },
            trailingIcon = {
                if (value.isNotBlank()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear trip")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded && matches.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            matches.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    leadingIcon = { Text("✈️") },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SuggestedTagsRow(
    currentTags: List<String>,
    onAdd: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SUGGESTED_TAGS.forEach { label ->
            val normalized = normalizeTag(label)
            val alreadyAdded = normalized in currentTags
            Text(
                "#$label",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (alreadyAdded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (alreadyAdded) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                    .clickable(enabled = !alreadyAdded) { onAdd(label) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

/**
 * A "whispered guidance" empty-state hint: small primary-tinted text (optionally with a leading
 * icon) shown below a field only while it's empty. Theme-adaptive (primary at low alpha) rather than
 * a hardcoded brand colour, so it reads correctly under every app theme.
 */
@Composable
private fun HintRow(icon: ImageVector?, text: String) {
    val tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun MoodSelector(selectedMood: String, onMoodSelected: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MOODS) { m ->
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selectedMood == m) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onMoodSelected(if (selectedMood == m) "" else m) },
                contentAlignment = Alignment.Center
            ) {
                Text(m, fontSize = 22.sp)
            }
        }
    }
}
