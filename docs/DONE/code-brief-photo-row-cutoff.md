# Macaco — NewEditEntryScreen: Photo Row Trailing Edge Clipped

The photo scroll row uses `Row + horizontalScroll` with no trailing padding, so the last item
is flush with the container edge and appears clipped. Fix: switch to `LazyRow` with end content
padding and move the + button to the end of the list.

---

## Replace photo row with LazyRow

**Problem:** Current code in `NewEditEntryScreen.kt`:

```kotlin
Row(
    modifier = Modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    // Add button — currently FIRST
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { showPhotoSourceDialog = true },
        contentAlignment = Alignment.Center
    ) { ... }

    photoUris.forEachIndexed { index, uri ->
        Box { ... }  // photo thumbnails
    }
}
if (photoUris.isEmpty()) {
    HintRow(Icons.Filled.PhotoCamera, stringResource(R.string.new_entry_hint_photos))
}
```

`Row + horizontalScroll` has no `contentPadding` mechanism, so the last item is flush with the
right edge of its parent container. Also, the + button belongs at the END so the user always
scrolls right to add more photos (natural UX: existing photos left → add button right).

**Fix:** Replace the `Row` block with a `LazyRow`. Move the + button to the end. Add
`contentPadding = PaddingValues(end = 16.dp)` so the last item never clips.

```
Before:  [+]  [photo 1]  [photo 2]  [photo 3|  ← clipped
After:   [photo 1]  [photo 2]  [photo 3]  [+]  16dp →
```

**File:** `ui/screens/NewEditEntryScreen.kt`

Replace the entire `Row(...) { ... }` + `if (photoUris.isEmpty())` block with:

```kotlin
LazyRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = PaddingValues(end = 16.dp)
) {
    // Photos first
    itemsIndexed(photoUris) { index, uri ->
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

    // Add button always last
    item {
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
    }
}
// HintRow is no longer needed — the + button is always visible as the last item.
// If you want to keep the camera hint for the empty state, you can show it below the
// LazyRow only when photoUris.isEmpty(), but the + button itself is sufficient.
```

Add import (others already present):
```kotlin
import androidx.compose.foundation.lazy.itemsIndexed
```

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Replace `Row + horizontalScroll` with `LazyRow(contentPadding = PaddingValues(end = 16.dp))`; move + to end; use `itemsIndexed` for photos | `ui/screens/NewEditEntryScreen.kt` |
