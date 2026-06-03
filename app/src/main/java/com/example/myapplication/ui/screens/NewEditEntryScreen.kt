package com.example.myapplication.ui.screens

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.data.model.TravelEntry
import com.example.myapplication.util.Cities
import com.example.myapplication.util.ImageStorage
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
    tagSuggestions: List<String> = emptyList()
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf(existingEntry?.title ?: "") }
    var location by remember { mutableStateOf(existingEntry?.location ?: "") }
    var dateMillis by remember { mutableLongStateOf(existingEntry?.dateMillis ?: System.currentTimeMillis()) }
    var mood by remember { mutableStateOf(existingEntry?.mood ?: "") }
    var description by remember { mutableStateOf(existingEntry?.description ?: "") }
    var photoUris by remember { mutableStateOf(existingEntry?.photoUris ?: emptyList()) }
    var tags by remember { mutableStateOf(existingEntry?.tags ?: emptyList()) }
    var titleError by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    // Files we copied into storage this session. Any that aren't committed via Save (removed again,
    // or the screen is dismissed) must be deleted so picker copies don't leak. Committed-photo
    // cleanup on edit/delete happens in the ViewModel instead.
    var sessionAdded by remember { mutableStateOf(emptySet<String>()) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        // Copy each picked image into the shared Pictures collection so it survives both relaunches
        // (the Photo Picker grant is temporary) and uninstalls. See ImageStorage.persistToGallery.
        val stored = uris.mapNotNull { ImageStorage.persistToGallery(context, it) }
        sessionAdded = sessionAdded + stored
        photoUris = (photoUris + stored).distinct()
    }

    // Dismissing without Save: drop any files added this session, since they were never committed.
    val cancel = {
        ImageStorage.delete(context, sessionAdded)
        onBack()
    }
    BackHandler(onBack = cancel)

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
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
                        if (existingEntry == null) "New Entry" else "Edit Entry",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = cancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
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
                                        createdAt = existingEntry?.createdAt ?: System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Photos row
            item {
                SectionLabel("Photos")
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
                            .clickable {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Add photo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Add",
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
                                    contentDescription = "Remove photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Title
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; titleError = false },
                    label = { Text("Title *") },
                    placeholder = { Text("My Paris Adventure") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = titleError,
                    supportingText = if (titleError) {
                        { Text("Title is required") }
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
                                "Date",
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
                SectionLabel("Mood")
                Spacer(Modifier.height(8.dp))
                MoodSelector(selectedMood = mood, onMoodSelected = { mood = it })
            }

            // Description
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Your story") },
                    placeholder = { Text("Write about your experience, the sights, sounds, and feelings...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    maxLines = Int.MAX_VALUE,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // Tags
            item {
                SectionLabel("Tags")
                Spacer(Modifier.height(8.dp))
                TagsField(
                    tags = tags,
                    onTagsChange = { tags = it },
                    suggestions = tagSuggestions
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
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

    fun commit(raw: String) {
        val tag = normalizeTag(raw)
        if (tag.isNotEmpty() && tag !in tags) onTagsChange(tags + tag)
        input = ""
    }

    // When the field is empty, surface previously used tags; once typing, filter them by prefix.
    // Either way, hide tags already on this entry.
    val matches = remember(input, tags, suggestions) {
        val q = normalizeTag(input)
        val unused = suggestions.filterNot { it in tags }
        if (q.isEmpty()) unused.take(8)
        else unused.filter { it.startsWith(q) }.take(8)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                // A space or comma ends a tag, mirroring how hashtags are typed.
                if (it.endsWith(" ") || it.endsWith(",")) commit(it) else input = it
            },
            label = { Text("Add a tag") },
            placeholder = { Text("museum, architecture, vacation") },
            leadingIcon = { Text("#", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                if (input.isNotBlank()) {
                    IconButton(onClick = { commit(input) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add tag")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit(input) }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )

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
                                contentDescription = "Remove tag",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }

        // Suggestions drawn from tags used on other entries; tap to add.
        if (matches.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                matches.forEach { suggestion ->
                    SuggestionChip(
                        onClick = { commit(suggestion) },
                        label = { Text("#$suggestion") }
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
            label = { Text("Location") },
            placeholder = { Text("Paris, France") },
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
    val chunked = MOODS.chunked(6)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chunked.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { m ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
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
    }
}
