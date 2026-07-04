# Macaco — Edit Entry: Move Add Photo/Video Buttons to Front of LazyRow

When the user has added photos or videos, the Add Photo and Add Video buttons are pushed to the
**end** of the LazyRow, out of sight. Move them to be the **first two items** so they're always
visible without scrolling.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/NewEditEntryScreen.kt`

---

## Change — Reorder LazyRow items (~line 621)

Move both `item { AddMediaButton(...) }` blocks from AFTER `itemsIndexed(displayMedia)` to BEFORE it.

The drag-and-drop logic (`draggingUri`, `mediaOrder`, `liveDisplay`) works on URI strings that only
exist in `displayMedia` — the AddMediaButton items are not URIs and are never in `mediaOrder`, so
their visual position in the LazyRow has no effect on drag behaviour.

### BEFORE
```kotlin
LazyRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = PaddingValues(end = 16.dp)
) {
    itemsIndexed(displayMedia, key = { _, pair -> pair.first }) { index, (uri, type) ->
        // ... draggable media tiles
    }

    // + Photo button always present.
    item {
        AddMediaButton(
            icon = Icons.Filled.PhotoCamera,
            label = stringResource(R.string.new_entry_add_photo_short),
            onClick = { showPhotoSourceDialog = true }
        )
    }

    // + Video button while under the per-entry video cap.
    if (videoUris.size < MAX_VIDEOS) {
        item {
            AddMediaButton(
                icon = Icons.Filled.Videocam,
                label = stringResource(R.string.new_entry_add_video_short),
                onClick = { showVideoSourceDialog = true }
            )
        }
    }
}
```

### AFTER
```kotlin
LazyRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = PaddingValues(end = 16.dp)
) {
    // + Photo and + Video buttons always first so they're visible without scrolling.
    item {
        AddMediaButton(
            icon = Icons.Filled.PhotoCamera,
            label = stringResource(R.string.new_entry_add_photo_short),
            onClick = { showPhotoSourceDialog = true }
        )
    }

    if (videoUris.size < MAX_VIDEOS) {
        item {
            AddMediaButton(
                icon = Icons.Filled.Videocam,
                label = stringResource(R.string.new_entry_add_video_short),
                onClick = { showVideoSourceDialog = true }
            )
        }
    }

    itemsIndexed(displayMedia, key = { _, pair -> pair.first }) { index, (uri, type) ->
        // ... draggable media tiles (unchanged)
    }
}
```

No other changes. Drag-and-drop, remove (×), and save logic are unaffected.
