package com.houseofmmminq.macaco.data.sync

import android.content.Context
import android.net.Uri
import com.houseofmmminq.macaco.data.model.TravelEntry
import com.houseofmmminq.macaco.util.ImageStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Local file backup/restore for the whole journal, as a single `.zip` the user picks via the
 * Storage Access Framework. Complements [DrivePhotoSync] (cloud) with a portable, offline copy.
 *
 * The zip layout is:
 * ```
 * backup.json          ← BackupFile: every entry, with photoUris rewritten to the paths below
 * photos/<id>_<i>.jpg  ← the actual bytes of each entry photo
 * ```
 *
 * Photos matter here: entry photos are stored as *local* content URIs, so exporting only the URIs
 * would produce broken images on any other device. Bundling the bytes makes the backup truly
 * portable — import re-materializes each photo into the shared gallery and re-points the URI.
 */
class JournalBackup(private val context: Context) {

    @Serializable
    data class BackupFile(
        val version: Int = 1,
        val exportedAt: Long,
        val entries: List<TravelEntry>
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Writes all [entries] (and their photo bytes) into a zip at [dest].
     * Returns the number of entries written, or a failure the caller can surface.
     */
    fun exportTo(dest: Uri, entries: List<TravelEntry>, compact: Boolean = false): Result<Int> = runCatching {
        val resolver = context.contentResolver
        resolver.openOutputStream(dest)?.use { os ->
            ZipOutputStream(BufferedOutputStream(os)).use { zip ->
                val exported = entries.map { entry ->
                    // Replace each photo URI with a zip-relative path, writing its bytes as we go.
                    // Photos we can't read (revoked grants, deleted media) are dropped, not fatal.
                    val paths = entry.photoUris.mapIndexedNotNull { i, uriString ->
                        val path = "photos/${entry.id}_$i.jpg"
                        zip.putNextEntry(ZipEntry(path))
                        // Compact re-encodes at JPEG 80% (lossy, strips EXIF) for a much smaller zip;
                        // full quality writes the original bytes untouched.
                        val wrote = if (compact) {
                            writeCompressed(uriString, zip)
                        } else {
                            val bytes = readBytes(uriString) ?: run {
                                zip.closeEntry()
                                return@mapIndexedNotNull null
                            }
                            zip.write(bytes)
                            true
                        }
                        zip.closeEntry()
                        if (wrote) path else null
                    }
                    // driveFileIds are device/account-specific; clear them so import re-uploads fresh.
                    entry.copy(photoUris = paths, driveFileIds = emptyList())
                }
                val backup = BackupFile(exportedAt = System.currentTimeMillis(), entries = exported)
                zip.putNextEntry(ZipEntry("backup.json"))
                zip.write(json.encodeToString(backup).toByteArray())
                zip.closeEntry()
            }
        } ?: error("Couldn't open the destination file.")
        entries.size
    }

    /**
     * Decodes the photo at [uriString] into a Bitmap, re-encodes at JPEG 80% quality directly
     * into [out] (no intermediate ByteArray), then recycles the Bitmap. Returns false if the
     * photo can't be read. One photo is decoded at a time, so peak memory stays bounded.
     */
    private fun writeCompressed(uriString: String, out: java.io.OutputStream): Boolean {
        val bitmap = runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))
                ?.use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return false
        return runCatching {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
        }.also {
            bitmap.recycle()
        }.isSuccess
    }

    /**
     * Reads a backup zip at [src] and hands each restored entry to [onEntry] (which should upsert
     * it into the user's store). Photos are re-materialized into the shared gallery and the URIs
     * re-pointed. Returns the number of entries imported.
     */
    suspend fun importFrom(src: Uri, onEntry: suspend (TravelEntry) -> Unit): Result<Int> = runCatching {
        val resolver = context.contentResolver

        // Stream photo bytes to temp files rather than holding the whole zip in memory.
        // A large backup (hundreds of MB) would otherwise exhaust the heap and crash silently.
        val tempDir = File(context.cacheDir, "backup_import_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        var backupJson: String? = null

        try {
            // Pass 1: stream each zip entry to disk (backup.json is small enough to keep in memory).
            resolver.openInputStream(src)?.use { ins ->
                ZipInputStream(BufferedInputStream(ins)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        when {
                            name == "backup.json" -> backupJson = zip.readBytes().decodeToString()
                            name.startsWith("photos/") -> {
                                // Flatten "photos/foo.jpg" → "photos_foo.jpg" so it's a valid filename.
                                File(tempDir, name.replace("/", "_"))
                                    .outputStream()
                                    .buffered()
                                    .use { out -> zip.copyTo(out) }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("Couldn't open the selected file.")

            val backup = json.decodeFromString<BackupFile>(
                backupJson ?: error("This doesn't look like a Macaco backup.")
            )

            // Pass 2: one photo at a time — read its temp file, write to gallery, delete it.
            backup.entries.forEach { entry ->
                val newUris = entry.photoUris.mapNotNull { path ->
                    val tempFile = File(tempDir, path.replace("/", "_"))
                    if (!tempFile.exists()) return@mapNotNull null
                    val uri = ImageStorage.persistBytesToGallery(context, tempFile.readBytes())
                    tempFile.delete() // free disk immediately after writing to the gallery
                    uri
                }
                onEntry(entry.copy(photoUris = newUris, driveFileIds = emptyList()))
            }
            backup.entries.size
        } finally {
            // Always clean up temp files, even on failure.
            tempDir.deleteRecursively()
        }
    }

    /** Reads the bytes behind a `file://` or `content://` URI, or null if unreadable. */
    private fun readBytes(uriString: String): ByteArray? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { it.readBytes() }
    }.getOrNull()
}
