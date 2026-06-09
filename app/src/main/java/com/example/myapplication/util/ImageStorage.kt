package com.example.myapplication.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Persists images picked via the Android Photo Picker.
 *
 * The Photo Picker (and SAF) only grant *temporary* read access to a `content://` URI — the grant
 * is revoked when the process restarts, so a persisted picker URI becomes unreadable on the next
 * launch. We copy the bytes somewhere we can reliably reload:
 *
 *  - [persist] copies into the app's `filesDir` and returns a `file://` URI. Used for local-only
 *    personalization (profile photo, theme background) that doesn't need to outlive the app.
 *  - [persistToGallery] copies into the shared **Pictures/Wanderlog** collection via MediaStore and
 *    returns a `content://` URI. Used for entry photos: shared media is NOT deleted on uninstall, so
 *    after a reinstall (and re-login) the cloud-synced entries can show their photos again — as long
 *    as the app has been granted media-read permission.
 */
object ImageStorage {

    /**
     * Copies [source] into `filesDir/[subDir]` and returns a `file://` URI string, or null if the
     * copy fails. When [replaceExisting] is true, any previously stored files in [subDir] are
     * deleted first (used for single-image slots like the profile photo or theme background, where
     * a fresh filename also busts Coil's cache so the new image shows immediately).
     */
    fun persist(
        context: Context,
        source: Uri,
        subDir: String,
        replaceExisting: Boolean = false
    ): String? = runCatching {
        val dir = File(context.filesDir, subDir).apply { mkdirs() }
        if (replaceExisting) dir.listFiles()?.forEach { it.delete() }
        val dest = File(dir, "img_${System.currentTimeMillis()}_${source.hashCode().toUInt()}.jpg")
        context.contentResolver.openInputStream(source)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        Uri.fromFile(dest).toString()
    }.getOrNull()

    /**
     * Copies [source] into the device's shared Pictures/Wanderlog collection and returns the
     * resulting `content://` URI string, or null on failure. The file lives outside app storage, so
     * it survives an uninstall (reading it again later requires media-read permission).
     */
    fun persistToGallery(context: Context, source: Uri): String? = runCatching {
        val resolver = context.contentResolver
        val name = "wanderlog_${System.currentTimeMillis()}_${source.hashCode().toUInt()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Macaco")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: return null
        val ok = resolver.openOutputStream(uri)?.use { output ->
            resolver.openInputStream(source)?.use { input -> input.copyTo(output); true } ?: false
        } ?: false
        if (!ok) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri.toString()
    }.getOrNull()

    /**
     * Like [persistToGallery] but writes raw [bytes] (e.g. from a backup zip) instead of copying
     * from a source URI. Returns the resulting `content://` URI string, or null on failure.
     */
    fun persistBytesToGallery(context: Context, bytes: ByteArray): String? = runCatching {
        val resolver = context.contentResolver
        val name = "macaco_${System.currentTimeMillis()}_${bytes.size}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Macaco")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: return null
        val ok = resolver.openOutputStream(uri)?.use { output -> output.write(bytes); true } ?: false
        if (!ok) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri.toString()
    }.getOrNull()

    /** Deletes everything stored under `filesDir/[subDir]`. */
    fun clear(context: Context, subDir: String) {
        runCatching { File(context.filesDir, subDir).listFiles()?.forEach { it.delete() } }
    }

    /**
     * Deletes the backing storage for each URI we own: `file://` URIs (copied into internal storage)
     * are removed from disk; `content://` URIs (our MediaStore entry photos) are removed via the
     * resolver. Other schemes are ignored. Best-effort — failures (e.g. media we no longer own after
     * a reinstall, which raises a RecoverableSecurityException) are swallowed.
     */
    fun delete(context: Context, uris: Collection<String>) {
        uris.forEach { uriString ->
            runCatching {
                val uri = Uri.parse(uriString)
                when (uri.scheme) {
                    "file" -> uri.path?.let { File(it).delete() }
                    "content" -> context.contentResolver.delete(uri, null, null)
                }
            }
        }
    }

    /**
     * Creates an empty temp file under `filesDir/[CAMERA_TEMP]` and returns a FileProvider
     * `content://` URI the camera app can write the captured photo into, or null on failure.
     * After capture, copy the result into the gallery via [persistToGallery] and call
     * `clear(context, CAMERA_TEMP)` to remove the temp file.
     */
    fun newCameraTempUri(context: Context): Uri? = runCatching {
        val dir = File(context.filesDir, CAMERA_TEMP).apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        file.createNewFile()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    const val BACKGROUNDS = "backgrounds"
    const val PROFILE = "profile"
    const val ENTRY_PHOTOS = "entry_photos"
    const val CAMERA_TEMP = "camera_temp"
}
