package com.example.myapplication.util

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Copies images picked via the Android Photo Picker into app-internal storage.
 *
 * The Photo Picker (and SAF) only grant *temporary* read access to a `content://` URI — the
 * grant is revoked when the process restarts, so a persisted picker URI becomes unreadable on
 * the next launch. To keep an image around we copy its bytes into our own `filesDir` and store a
 * `file://` URI that we own permanently and Coil can always load.
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

    /** Deletes everything stored under `filesDir/[subDir]`. */
    fun clear(context: Context, subDir: String) {
        runCatching { File(context.filesDir, subDir).listFiles()?.forEach { it.delete() } }
    }

    /**
     * Deletes the backing file for each `file://` URI in [uris] (those we copied into internal
     * storage). Legacy `content://` picker URIs and any other schemes are ignored, since we don't
     * own those files. Safe to call with URIs that are already gone.
     */
    fun delete(uris: Collection<String>) {
        uris.forEach { uriString ->
            runCatching {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "file") uri.path?.let { File(it).delete() }
            }
        }
    }

    const val BACKGROUNDS = "backgrounds"
    const val PROFILE = "profile"
    const val ENTRY_PHOTOS = "entry_photos"
}
