package com.houseofmmminq.macaco.ui.widget

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Resolves a readable image source for a widget row/hero. Entry photos are stored as a local
 * content:// URI (Pictures/Macaco on the device the photo was added from) AND backed up to Drive.
 * On a device that didn't add the photo (fresh reinstall, second device), the content:// URI is
 * dead — but DrivePhotoSync caches the Drive copy to cacheDir/drive_photos/<fileId>.jpg, which the
 * widget (same app process) can read. Prefer the local URI, fall back to the Drive cache.
 */
internal object WidgetPhotos {
    fun readableSource(context: Context, photoUri: String?, driveFileId: String?): Uri? {
        if (!photoUri.isNullOrBlank()) {
            val uri = Uri.parse(photoUri)
            val readable = runCatching {
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            }.getOrDefault(false)
            if (readable) return uri
        }
        if (!driveFileId.isNullOrBlank()) {
            val cached = File(File(context.cacheDir, "drive_photos"), "$driveFileId.jpg")
            if (cached.exists()) return Uri.fromFile(cached)
        }
        return null
    }

    /**
     * Open an image stream for a source returned by [readableSource]. A Drive-cache hit is a
     * `file://` URI into our own cacheDir — and ContentResolver.openInputStream returns null for
     * `file://` URIs inside a widget/AppWidgetProvider context on some ROMs (observed on Samsung
     * One UI), which is exactly why widget photos silently failed to decode. Read those directly
     * as a FileInputStream; content:// (a locally-added gallery photo) still goes via the resolver.
     */
    fun openStream(context: Context, uri: Uri): InputStream? =
        if (uri.scheme == "file") uri.path?.let { FileInputStream(it) }
        else context.contentResolver.openInputStream(uri)
}
