# Macaco — ImageStorage: Fix MediaStore Writes on API ≤ 28 (Samsung S8)

On API ≤ 28, `resolver.openOutputStream()` on a freshly inserted MediaStore entry silently
returns `null` on Samsung Android 8.0, causing `persistBytesToGallery` and `persistToGallery`
to drop every photo. Fix: write the file to disk first, then insert into MediaStore with the
`DATA` column pointing to the existing file. Touches only `util/ImageStorage.kt`.

---

## Background: why the current code silently fails on Samsung API 26

On API ≥ 29, the flow is: insert (with `IS_PENDING=1`) → open output stream → write → commit
(`IS_PENDING=0`). The pending flag serialises the operation and MediaStore is happy.

On API ≤ 28 there is no `IS_PENDING`. The current code does:
```
resolver.insert(EXTERNAL_CONTENT_URI, values)   // creates an empty MediaStore entry
resolver.openOutputStream(uri)                   // Samsung API 26: returns null ← CRASH POINT
```
Samsung's MediaStore implementation on Android 8.0 refuses to open an output stream for an
entry whose backing file does not yet exist on disk. `openOutputStream` returns `null`, `ok`
becomes `false`, the entry is deleted, and `null` is returned. Because the whole method is
wrapped in `runCatching`, this is completely silent.

---

## Fix: file-first approach for API ≤ 28

For `Build.VERSION.SDK_INT < Build.VERSION_CODES.Q`:
1. Resolve `Environment.getExternalStoragePublicDirectory(DIRECTORY_PICTURES)/Macaco/`.
2. Write the bytes (or copy the source URI) directly to a `File` there — no MediaStore yet.
3. Insert into MediaStore with `MediaStore.Images.Media.DATA` set to the file's absolute path.
   This tells MediaStore where the file already lives; no output stream needed.
4. Return the resulting `content://` URI (or fall back to `Uri.fromFile()` if the insert fails).

---

## Change 1: fix `persistBytesToGallery` for API ≤ 28

Replace the existing `persistBytesToGallery` function with:

```kotlin
fun persistBytesToGallery(context: Context, bytes: ByteArray): String? = runCatching {
    val resolver = context.contentResolver
    val name = "macaco_${System.currentTimeMillis()}_${bytes.size}.jpg"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // API 29+: insert with IS_PENDING, stream bytes, then commit.
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Macaco")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
        ) ?: return null
        val ok = resolver.openOutputStream(uri)?.use { it.write(bytes); true } ?: false
        if (!ok) { runCatching { resolver.delete(uri, null, null) }; return null }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri.toString()
    } else {
        // API ≤ 28 (Samsung / Android 8.x): write file first, THEN insert into MediaStore.
        // openOutputStream on a freshly inserted entry silently returns null on Samsung API 26.
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Macaco"
        ).also { it.mkdirs() }
        val file = File(dir, name)
        file.writeBytes(bytes)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            @Suppress("DEPRECATION")
            put(MediaStore.Images.Media.DATA, file.absolutePath)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        (uri ?: Uri.fromFile(file)).toString()
    }
}.getOrNull()
```

Required new import:
```kotlin
import android.os.Environment
```

---

## Change 2: fix `persistToGallery` for API ≤ 28

Same root cause. Replace the existing `persistToGallery` function with:

```kotlin
fun persistToGallery(context: Context, source: Uri): String? = runCatching {
    val resolver = context.contentResolver
    val name = "macaco_${System.currentTimeMillis()}_${source.hashCode().toUInt()}.jpg"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Macaco")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
        ) ?: return null
        val ok = resolver.openOutputStream(uri)?.use { output ->
            resolver.openInputStream(source)?.use { input -> input.copyTo(output); true } ?: false
        } ?: false
        if (!ok) { runCatching { resolver.delete(uri, null, null) }; return null }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri.toString()
    } else {
        // API ≤ 28: write file to disk first to avoid Samsung openOutputStream null bug.
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Macaco"
        ).also { it.mkdirs() }
        val file = File(dir, name)
        resolver.openInputStream(source)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            @Suppress("DEPRECATION")
            put(MediaStore.Images.Media.DATA, file.absolutePath)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        (uri ?: Uri.fromFile(file)).toString()
    }
}.getOrNull()
```

---

## Scope notes

- The old `persistToGallery` used the folder name `"Pictures/Wanderlog"` (note stale name in the
  existing comment on line 51). The new code uses `"Macaco"` for both paths, consistent with the
  API 29+ path. **Check the existing function before replacing** — if `RELATIVE_PATH` already says
  `"Pictures/Macaco"` leave it, only the API ≤ 28 branch is new.
- The `@Suppress("DEPRECATION")` on `MediaStore.Images.Media.DATA` is intentional: this column
  is deprecated on API 29+ but is the correct way to register an existing file on API ≤ 28.
- `Uri.fromFile(file)` fallback: if MediaStore insert fails despite the file existing, a `file://`
  URI is returned. Coil will display it correctly. Drive sync will upload it on the next sync pass.
- No permission changes needed. `WRITE_EXTERNAL_STORAGE` (maxSdkVersion 28) is already declared
  in the manifest and requested at launch in `MainActivity.kt`.
- `persistBytesToGallery` is called from `JournalBackup.importFrom()` — no changes there.
- `persistToGallery` is called from `NewEditEntryScreen` and `ProfileScreen` — no changes there.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Replace `persistBytesToGallery` with file-first API ≤ 28 path | `util/ImageStorage.kt` |
| 2 | Replace `persistToGallery` with file-first API ≤ 28 path | `util/ImageStorage.kt` |
