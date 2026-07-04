# Macaco — Entry Detail: Add Video Support to the + Tile

The "+" add-media tile in `EntryDetailScreen` currently launches the system photo picker
(`ImageOnly`). When tapped, it should instead show a small dialog letting the user choose
**Add Photo** or **Add Video** — no permanently visible extra button. This mirrors the
`showPhotoSourceDialog` / `showVideoSourceDialog` pattern already used in `NewEditEntryScreen`.

**Files:**
- `app/src/main/java/com/houseofmmminq/macaco/ui/screens/EntryDetailScreen.kt`

---

## Overview of changes

1. Add `showAddMediaDialog` state variable.
2. Change both "+" tile `onClick` sites (landscape ~line 343, portrait ~line 623) from
   `addPhotoLauncher.launch(…)` to `showAddMediaDialog = true`.
3. Add the `AlertDialog` that shows "Add Photo" / "Add Video" options.
4. Add a video picker launcher (`addVideoLauncher`) + transcode flow mirroring the one in
   `NewEditEntryScreen` (`videoPicker`, `transcodingProgress`, `trimQueue`, `storeTranscoded`,
   trim dialog, transcoding overlay).

---

## Change 1 — New state variables (~after existing `addPhotoLauncher` declaration, ~line 185)

Add alongside the existing launcher declarations:

```kotlin
var showAddMediaDialog by remember { mutableStateOf(false) }

// Video-add state (mirrors NewEditEntryScreen)
var transcodingProgress by remember { mutableStateOf<Float?>(null) }
var trimQueue by remember { mutableStateOf<List<Pair<Uri, Long>>>(emptyList()) }
val coroutineScope = rememberCoroutineScope()

val storeTranscoded: (java.io.File?) -> Unit = { file ->
    if (file == null) {
        android.widget.Toast.makeText(
            context, context.getString(R.string.video_add_failed), android.widget.Toast.LENGTH_LONG
        ).show()
    } else {
        val persisted = ImageStorage.persistVideoToGallery(context, file)
        if (persisted != null) {
            val updated = entry.copy(
                videoUris = entry.videoUris + persisted,
                videoFileIds = entry.videoFileIds + ""
            )
            onSave(updated)
        }
    }
}

val addVideoLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3)
) { uris ->
    val canAdd = 3 - entry.videoUris.size
    if (canAdd <= 0) return@rememberLauncherForActivityResult
    val (short, long) = uris.take(canAdd).partition { uri ->
        VideoTranscoder.getDurationMs(context, uri) <= VideoTranscoder.MAX_DURATION_MS
    }
    trimQueue = trimQueue + long.map { it to VideoTranscoder.getDurationMs(context, it) }
    if (short.isNotEmpty()) {
        transcodingProgress = 0f
        coroutineScope.launch {
            short.forEach { uri ->
                val outFile = VideoTranscoder.transcode(
                    context, uri, onProgress = { transcodingProgress = it }
                )
                storeTranscoded(outFile)
            }
            transcodingProgress = null
        }
    }
}
```

---

## Change 2 — "+" tile onClick: both landscape and portrait sites

Find the `+` AddMediaTile (the small tiled Box that launches `addPhotoLauncher`) in both the
landscape and portrait branches. Replace the direct launcher call with `showAddMediaDialog = true`.

### BEFORE (each site, two occurrences)
```kotlin
.clickable {
    addPhotoLauncher.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )
}
```

### AFTER (both sites)
```kotlin
.clickable { showAddMediaDialog = true }
```

---

## Change 3 — Add the media-type dialog

Place this just before the `Scaffold` (or at the same level as the other dialog composables):

```kotlin
if (showAddMediaDialog) {
    AlertDialog(
        onDismissRequest = { showAddMediaDialog = false },
        title = { Text(stringResource(R.string.new_entry_add_photo)) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAddMediaDialog = false
                            addPhotoLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.new_entry_photo_choose),
                        style = MaterialTheme.typography.bodyLarge)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAddMediaDialog = false
                            addVideoLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.VideoOnly
                                )
                            )
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Videocam, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.new_entry_video_pick),
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { showAddMediaDialog = false }) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
```

---

## Change 4 — Trim dialog + transcoding overlay

After the add-media dialog, add the trim dialog and the transcoding scrim (copy from
`NewEditEntryScreen` and adapt):

```kotlin
// Trim dialog — advances the queue one entry at a time
trimQueue.firstOrNull()?.let { (trimUri, trimDuration) ->
    VideoTrimDialog(
        sourceUri = trimUri,
        durationMs = trimDuration,
        onTrimConfirmed = { startMs ->
            trimQueue = trimQueue.drop(1)
            transcodingProgress = 0f
            coroutineScope.launch {
                val outFile = VideoTranscoder.transcode(
                    context, trimUri, trimStartMs = startMs,
                    onProgress = { transcodingProgress = it }
                )
                transcodingProgress = null
                storeTranscoded(outFile)
            }
        },
        onDismiss = { trimQueue = trimQueue.drop(1) }
    )
}

// Transcoding overlay — blocks input while processing
val progress = transcodingProgress
if (progress != null) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(56.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.video_processing),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
```

`VideoTrimDialog` is a private composable in `NewEditEntryScreen.kt` — move it to a shared
location (e.g. a new file `ui/components/VideoTrimDialog.kt`) or copy it into `EntryDetailScreen.kt`.

---

## Imports to add

```kotlin
import com.houseofmmminq.macaco.util.VideoTranscoder
import com.houseofmmminq.macaco.util.ImageStorage
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
```

---

## Notes

- `onSave` is already called from the entry detail context when photos are added — the same
  pattern applies for videos (`entry.copy(videoUris = ..., videoFileIds = ...)`).
- The `VideoTrimDialog` in `NewEditEntryScreen` uses `R.string.video_trim_*` strings and
  `R.string.video_add_failed` — no new strings needed.
- If `R.string.new_entry_video_pick` doesn't exist, add it (`"Choose from library"`) alongside
  the existing `new_entry_video_record`.
