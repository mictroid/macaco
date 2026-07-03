package com.houseofmmminq.macaco.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
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
 *  - [persistToGallery] copies into the shared **Pictures/Macaco** collection via MediaStore and
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
     * Copies [source] into the device's shared Pictures/Macaco collection and returns the
     * resulting `content://` URI string, or null on failure. The file lives outside app storage, so
     * it survives an uninstall (reading it again later requires media-read permission).
     *
     * On API ≤ 28 we write the file to disk *first* and then register it with MediaStore via the
     * `DATA` column: Samsung's Android 8.x MediaStore silently returns `null` from
     * `openOutputStream()` for an entry whose backing file doesn't exist yet, which would drop the
     * photo. On API 29+ the IS_PENDING insert → stream → commit flow is used.
     */
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
            val sourceBytes = resolver.openInputStream(source)?.use { it.readBytes() } ?: run {
                runCatching { resolver.delete(uri, null, null) }
                return null
            }
            val outputBytes = compressForStorage(sourceBytes) ?: sourceBytes // fallback: write original
            val ok = resolver.openOutputStream(uri)?.use { output ->
                output.write(outputBytes); true
            } ?: false
            if (!ok) {
                runCatching { resolver.delete(uri, null, null) }
                return null
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri.toString()
        } else {
            // API ≤ 28: write to disk first to avoid Samsung's openOutputStream-null bug.
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Macaco"
            ).also { it.mkdirs() }
            val file = File(dir, name)
            val sourceBytes = resolver.openInputStream(source)?.use { it.readBytes() } ?: return null
            val outputBytes = compressForStorage(sourceBytes) ?: sourceBytes // fallback: write original
            file.writeBytes(outputBytes)
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

    /**
     * Like [persistToGallery] but writes raw [bytes] (e.g. from a backup zip) instead of copying
     * from a source URI. Returns the resulting `content://` URI string, or null on failure.
     */
    fun persistBytesToGallery(context: Context, bytes: ByteArray): String? = runCatching {
        val resolver = context.contentResolver
        val name = "macaco_${System.currentTimeMillis()}_${bytes.size}.jpg"

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
            val ok = resolver.openOutputStream(uri)?.use { output -> output.write(bytes); true } ?: false
            if (!ok) {
                runCatching { resolver.delete(uri, null, null) }
                return null
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri.toString()
        } else {
            // API ≤ 28: write to disk first to avoid Samsung's openOutputStream-null bug.
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

    /**
     * Creates an empty `.mp4` temp file the system camera can record into, returned as a
     * FileProvider `content://` URI, or null on failure. After recording, transcode then call
     * [persistVideoToGallery]. Temp files live in `filesDir/[VIDEO_TEMP]` — call
     * `clear(context, VIDEO_TEMP)` when done.
     */
    fun newVideoTempUri(context: Context): Uri? = runCatching {
        val dir = File(context.filesDir, VIDEO_TEMP).apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.mp4")
        file.createNewFile()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    /**
     * Copies [transcodedFile] (the `.mp4` output from [VideoTranscoder]) into the device's shared
     * Movies/Macaco collection and returns the `content://` URI string, or null on failure. Uses the
     * same IS_PENDING flow as [persistToGallery] on API 29+, and the write-to-disk-first approach on
     * API ≤ 28.
     */
    fun persistVideoToGallery(context: Context, transcodedFile: File): String? = runCatching {
        val resolver = context.contentResolver
        val name = "macaco_${System.currentTimeMillis()}.mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Macaco")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
            ) ?: return null
            val ok = resolver.openOutputStream(uri)?.use { out ->
                transcodedFile.inputStream().use { it.copyTo(out) }; true
            } ?: false
            if (!ok) {
                runCatching { resolver.delete(uri, null, null) }
                return null
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri.toString()
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Macaco"
            ).also { it.mkdirs() }
            val file = File(dir, name)
            transcodedFile.copyTo(file, overwrite = true)
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                @Suppress("DEPRECATION")
                put(MediaStore.Video.Media.DATA, file.absolutePath)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            (uri ?: Uri.fromFile(file)).toString()
        }
    }.getOrNull()

    /**
     * Decodes [bytes] as a bitmap, scales it down so neither dimension exceeds [maxDim], and
     * re-encodes at [quality]% JPEG. Returns null if the input is not a recognised bitmap format
     * (the caller falls back to the original bytes in that case, so no photo is ever lost).
     *
     * Uses a two-pass decode (bounds only → inSampleSize) so the full-resolution pixel data is
     * never loaded into RAM: a 12 MP photo decoded with inSampleSize=4 uses ~2 MB of heap instead
     * of ~36 MB. CPU work — run it off the main thread.
     */
    private fun compressForStorage(
        bytes: ByteArray,
        maxDim: Int = 1920,
        quality: Int = 85
    ): ByteArray? = runCatching {
        // Pass 1: read dimensions without decoding pixels.
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        check(opts.outWidth > 0 && opts.outHeight > 0)

        // Largest power-of-2 subsample that keeps the image above maxDim.
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) > maxDim) sample *= 2

        // Pass 2: decode at reduced resolution using 16-bit colour (half the RAM of ARGB_8888).
        opts.inJustDecodeBounds = false
        opts.inSampleSize = sample
        opts.inPreferredConfig = Bitmap.Config.RGB_565
        val sampled = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts))

        // Fine-scale to exactly maxDim if the subsampled bitmap is still too large.
        val largest = maxOf(sampled.width, sampled.height)
        val final = if (largest > maxDim) {
            val s = maxDim.toFloat() / largest
            Bitmap.createScaledBitmap(
                sampled,
                (sampled.width * s).toInt(),
                (sampled.height * s).toInt(),
                true
            ).also { if (it !== sampled) sampled.recycle() }
        } else sampled

        val oriented = applyExifOrientation(final, exifOrientation(bytes))
        ByteArrayOutputStream().use { out ->
            oriented.compress(Bitmap.CompressFormat.JPEG, quality, out)
            oriented.recycle()
            out.toByteArray()
        }
    }.getOrNull()

    /** EXIF orientation of [bytes], or ORIENTATION_NORMAL if unreadable. */
    private fun exifOrientation(bytes: ByteArray): Int = runCatching {
        androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
            .getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
            )
    }.getOrDefault(androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)

    /** Returns [bitmap] transformed per the EXIF [orientation] (recycling the input if replaced). */
    internal fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = android.graphics.Matrix()
        when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (out !== bitmap) bitmap.recycle()
        return out
    }

    const val BACKGROUNDS = "backgrounds"
    const val PROFILE = "profile"
    const val ENTRY_PHOTOS = "entry_photos"
    const val CAMERA_TEMP = "camera_temp"
    const val VIDEO_TEMP = "video_temp"
    const val DRIVE_VIDEOS = "drive_videos"   // used in DrivePhotoSync
}
