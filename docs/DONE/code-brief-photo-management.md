# Macaco — Entry Detail: Photo Management (Remove / Reorder / Set Cover)

Three photo management actions are missing or hidden in the entry detail screen:

- **Remove** — no way to delete an individual photo without entering edit mode
- **Reorder (move left / move right)** — no way to change photo order at all
- **Set as cover** — already works via long-press on thumbnails, but is completely hidden
  (no hint, no menu, just a silent toast)

**Solution:** Replace the silent long-press with a `ModalBottomSheet` that surfaces all three
actions clearly. Hero photo also gets long-press for the first time. Tap behaviour is unchanged
(opens the full-screen gallery).

---

## 1. Add `withRemoved` and `withSwapped` entry extensions

Add these two helpers after the existing `withCover` extension (line 642).

```kotlin
// BEFORE — EntryDetailScreen.kt (line 642, end of withCover)
}

// AFTER — insert immediately after
private fun TravelEntry.withRemoved(index: Int): TravelEntry {
    if (index < 0 || index >= photoUris.size) return this
    val photos = photoUris.toMutableList().apply { removeAt(index) }
    val driveIds = driveFileIds.toMutableList().apply { if (index < size) removeAt(index) }
    return copy(photoUris = photos, driveFileIds = driveIds)
}

private fun TravelEntry.withSwapped(a: Int, b: Int): TravelEntry {
    if (a == b || a < 0 || b < 0 || a >= photoUris.size || b >= photoUris.size) return this
    val photos = photoUris.toMutableList().also { list -> val tmp = list[a]; list[a] = list[b]; list[b] = tmp }
    val driveIds = if (a < driveFileIds.size && b < driveFileIds.size) {
        driveFileIds.toMutableList().also { list -> val tmp = list[a]; list[a] = list[b]; list[b] = tmp }
    } else driveFileIds
    return copy(photoUris = photos, driveFileIds = driveIds)
}
```

**File:** `ui/screens/EntryDetailScreen.kt`

---

## 2. Add `PhotoActionSheet` composable

Add this composable after `JournalThumb` (after line 689).

```kotlin
// AFTER JournalThumb — insert new composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoActionSheet(
    index: Int,
    total: Int,
    onSetCover: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            // "Set as cover" only for non-hero photos
            if (index > 0) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.entry_detail_photo_set_cover)) },
                    leadingContent = { Icon(Icons.Filled.Star, contentDescription = null) },
                    modifier = Modifier.clickable { onSetCover(); onDismiss() }
                )
            }
            if (index > 0) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.entry_detail_photo_move_left)) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null) },
                    modifier = Modifier.clickable { onMoveLeft(); onDismiss() }
                )
            }
            if (index < total - 1) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.entry_detail_photo_move_right)) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable { onMoveRight(); onDismiss() }
                )
            }
            HorizontalDivider()
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.entry_detail_photo_remove),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clickable { onRemove(); onDismiss() }
            )
        }
    }
}
```

**File:** `ui/screens/EntryDetailScreen.kt`

---

## 3. Add `photoActionIndex` state variable

Add alongside the other state variables inside the pager page block (near line 262, after
`listState`).

```kotlin
// BEFORE — EntryDetailScreen.kt (line 262, after listState)
        val listState = rememberLazyListState()
        LaunchedEffect(entriesPagerState.currentPage) {

// AFTER — insert one line
        val listState = rememberLazyListState()
        var photoActionIndex by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(entriesPagerState.currentPage) {
```

**File:** `ui/screens/EntryDetailScreen.kt`

---

## 4. Show `PhotoActionSheet` when `photoActionIndex` is set

Add inside the per-page `Box`, just before the `LazyColumn` (line 283).

```kotlin
// BEFORE — EntryDetailScreen.kt (line 283)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {

// AFTER — insert sheet trigger above LazyColumn
            val photoCount = maxOf(entry.photoUris.size, entry.driveFileIds.size)
            photoActionIndex?.let { idx ->
                PhotoActionSheet(
                    index = idx,
                    total = photoCount,
                    onSetCover = { onSaveEntry(entry.withCover(idx)) },
                    onMoveLeft = { onSaveEntry(entry.withSwapped(idx, idx - 1)) },
                    onMoveRight = { onSaveEntry(entry.withSwapped(idx, idx + 1)) },
                    onRemove = { onSaveEntry(entry.withRemoved(idx)) },
                    onDismiss = { photoActionIndex = null }
                )
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
```

Note: `val photoCount = ...` is currently declared inside `item { }` at line 293. Move it above
the `LazyColumn` so the sheet can reference it. Remove the duplicate declaration inside `item { }`.

**File:** `ui/screens/EntryDetailScreen.kt`

---

## 5. Update `JournalPhoto` to accept an optional `onLongClick`

```kotlin
// BEFORE — EntryDetailScreen.kt (line 646)
private fun JournalPhoto(data: String?, onClick: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(data).crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    )
}

// AFTER
@OptIn(ExperimentalFoundationApi::class)
private fun JournalPhoto(data: String?, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, modifier: Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(data).crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick ?: {})
    )
}
```

**File:** `ui/screens/EntryDetailScreen.kt`

---

## 6. Update all `JournalPhoto` call sites to pass `onLongClick`

There are two `JournalPhoto` calls — single-photo hero (line 322) and multi-photo hero (line 341).
Both get `onLongClick = { photoActionIndex = 0 }`.

```kotlin
// BEFORE — single-photo hero (line 322)
                    photoCount == 1 -> JournalPhoto(
                        data = entry.displayPhotoUri(0, cachedDrivePhotos),
                        onClick = { galleryStartIndex = 0 },
                        modifier = Modifier.fillMaxWidth().height(heroHeight)
                    )

// AFTER
                    photoCount == 1 -> JournalPhoto(
                        data = entry.displayPhotoUri(0, cachedDrivePhotos),
                        onClick = { galleryStartIndex = 0 },
                        onLongClick = { photoActionIndex = 0 },
                        modifier = Modifier.fillMaxWidth().height(heroHeight)
                    )
```

```kotlin
// BEFORE — multi-photo hero (line 341)
                                JournalPhoto(
                                    data = entry.displayPhotoUri(0, cachedDrivePhotos),
                                    onClick = { galleryStartIndex = 0 },
                                    modifier = Modifier.weight(0.65f).fillMaxHeight()
                                )

// AFTER
                                JournalPhoto(
                                    data = entry.displayPhotoUri(0, cachedDrivePhotos),
                                    onClick = { galleryStartIndex = 0 },
                                    onLongClick = { photoActionIndex = 0 },
                                    modifier = Modifier.weight(0.65f).fillMaxHeight()
                                )
```

**File:** `ui/screens/EntryDetailScreen.kt`

---

## 7. Update `JournalThumb` call sites to open the sheet instead of immediate set-cover

There are two `JournalThumb` call sites — the right column (line 350) and the overflow LazyRow
(line 374). Change `onLongClick = { setCover(index) }` to `onLongClick = { photoActionIndex = index }`.

```kotlin
// BEFORE — right column thumbs (line 354)
                                        onLongClick = { setCover(index) },

// AFTER
                                        onLongClick = { photoActionIndex = index },
```

```kotlin
// BEFORE — overflow row thumbs (line ~376)
                                        onLongClick = { setCover(index) },

// AFTER
                                        onLongClick = { photoActionIndex = index },
```

The `setCover` lambda can remain in the file for now (it's still used conceptually via the sheet's
`onSetCover` callback), but the direct call sites no longer reference it. Code may remove the
standalone lambda and inline the call in the sheet callback if preferred.

**File:** `ui/screens/EntryDetailScreen.kt`

---

## 8. New string resources (×11 languages)

| Key | EN value |
|-----|----------|
| `entry_detail_photo_set_cover` | Set as cover |
| `entry_detail_photo_move_left` | Move left |
| `entry_detail_photo_move_right` | Move right |
| `entry_detail_photo_remove` | Remove photo |

**File:** `res/values/strings.xml` + all 10 translation files

---

## 9. New imports

```kotlin
// Add to EntryDetailScreen.kt imports (these may already be present — add only if missing):
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
```

**File:** `ui/screens/EntryDetailScreen.kt`

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `withRemoved` + `withSwapped` entry extensions | `EntryDetailScreen.kt` |
| 2 | Add `PhotoActionSheet` composable | `EntryDetailScreen.kt` |
| 3 | Add `photoActionIndex` state var in pager page block | `EntryDetailScreen.kt` |
| 4 | Show sheet; move `photoCount` above `LazyColumn` | `EntryDetailScreen.kt` |
| 5 | Add `onLongClick` param to `JournalPhoto` | `EntryDetailScreen.kt` |
| 6 | Wire `onLongClick = { photoActionIndex = 0 }` on hero photos | `EntryDetailScreen.kt` |
| 7 | Change thumbnail `onLongClick` to open sheet | `EntryDetailScreen.kt` |
| 8 | 4 new string keys × 11 languages | `res/values/strings.xml` + translations |
| 9 | New imports (Star, KeyboardArrowLeft/Right, ListItem, ModalBottomSheet) | `EntryDetailScreen.kt` |
