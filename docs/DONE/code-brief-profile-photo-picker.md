# Macaco — ProfileScreen: Expanded Profile Photo Picker

Replaces the single-tap gallery launch on the profile photo with a `ModalBottomSheet` offering
three sources: Gallery, Browse Files (includes Google Drive), and Camera. Touches only
`ui/screens/ProfileScreen.kt`.

---

## Change: photo source bottom sheet

**Problem:** Tapping the camera icon on the profile photo directly launches `PickVisualMedia`,
which only shows the Android system photo picker (device gallery + Google Photos). Users cannot
pick a photo from Google Drive or take a new photo with the camera.

**Fix:** Replace the direct `photoPicker.launch()` call with `showPhotoSourceSheet = true`.
When the sheet is open, show three `ListItem` rows — Gallery, Browse Files, Take Photo — each
triggering its own launcher. All three paths end with the same `ImageStorage.persist()` call
that already handles profile photos correctly.

```
┌─────────────────────────────────┐
│  Change profile photo           │  ← ModalBottomSheet title (bodyMedium, center)
├─────────────────────────────────┤
│  🖼   Gallery                   │  ← PickVisualMedia (existing)
├─────────────────────────────────┤
│  📁   Browse files              │  ← OpenDocument image/* (includes Drive)
├─────────────────────────────────┤
│  📷   Take photo                │  ← TakePicture via ImageStorage.newCameraTempUri
└─────────────────────────────────┘
```

### 1. Add state variables (top of the composable, alongside existing state)

```kotlin
var showPhotoSourceSheet by remember { mutableStateOf(false) }
var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
```

### 2. Add two new launchers (alongside the existing `photoPicker` launcher)

```kotlin
// Browse Files — ACTION_OPEN_DOCUMENT with image/* opens system file picker including Drive.
val documentPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    ImageStorage.persist(context, uri, ImageStorage.PROFILE, replaceExisting = true)
    // Follow the same post-persist pattern as the existing photoPicker result handler.
}

// Camera — uses the FileProvider temp URI already supported by ImageStorage.
val cameraPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.TakePicture()
) { success ->
    if (success) {
        pendingCameraUri?.let { uri ->
            ImageStorage.persist(context, uri, ImageStorage.PROFILE, replaceExisting = true)
            // Follow the same post-persist pattern as the existing photoPicker result handler.
        }
    }
    pendingCameraUri = null
}
```

The comment "Follow the same post-persist pattern" means: copy whatever the existing
`photoPicker` result lambda does after `ImageStorage.persist(...)` (e.g. a ViewModel call to
refresh the profile photo URI) and repeat it identically in both new launchers.

### 3. Change the camera icon tap to open the sheet

Find the `IconButton` (or `Box` with `clickable`) that currently calls:
```kotlin
photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
```

Replace that call with:
```kotlin
showPhotoSourceSheet = true
```

### 4. Add the ModalBottomSheet

Place this at the bottom of the composable body, before the closing brace:

```kotlin
if (showPhotoSourceSheet) {
    ModalBottomSheet(onDismissRequest = { showPhotoSourceSheet = false }) {
        Text(
            text = stringResource(R.string.profile_change_photo_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            textAlign = TextAlign.Center
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Gallery
        ListItem(
            headlineContent = { Text(stringResource(R.string.profile_photo_source_gallery)) },
            leadingContent = {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
            },
            modifier = Modifier.clickable {
                showPhotoSourceSheet = false
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )

        // Browse Files (includes Google Drive)
        ListItem(
            headlineContent = { Text(stringResource(R.string.profile_photo_source_files)) },
            leadingContent = {
                Icon(Icons.Outlined.Folder, contentDescription = null)
            },
            modifier = Modifier.clickable {
                showPhotoSourceSheet = false
                documentPicker.launch(arrayOf("image/*"))
            }
        )

        // Camera
        ListItem(
            headlineContent = { Text(stringResource(R.string.profile_photo_source_camera)) },
            leadingContent = {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null)
            },
            modifier = Modifier.clickable {
                showPhotoSourceSheet = false
                val uri = ImageStorage.newCameraTempUri(context)
                pendingCameraUri = uri
                cameraPicker.launch(uri)
            }
        )

        Spacer(Modifier.height(24.dp))
    }
}
```

### Imports to add

```kotlin
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.text.style.TextAlign
```

Check which of these are already imported before adding.

---

## New string resources

| Key | EN value |
|-----|----------|
| `profile_change_photo_title` | Change profile photo |
| `profile_photo_source_gallery` | Gallery |
| `profile_photo_source_files` | Browse files |
| `profile_photo_source_camera` | Take photo |

Add to `strings.xml` ×11 languages.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `showPhotoSourceSheet` + `pendingCameraUri` state | `ui/screens/ProfileScreen.kt` |
| 2 | Add `documentPicker` (`OpenDocument`) + `cameraPicker` (`TakePicture`) launchers | `ui/screens/ProfileScreen.kt` |
| 3 | Change camera icon tap to `showPhotoSourceSheet = true` | `ui/screens/ProfileScreen.kt` |
| 4 | Add `ModalBottomSheet` with Gallery / Browse files / Take photo rows | `ui/screens/ProfileScreen.kt` |
| 5 | Add 4 new strings | `strings.xml` ×11 |
