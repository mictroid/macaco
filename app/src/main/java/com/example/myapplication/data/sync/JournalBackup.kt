package com.example.myapplication.data.sync

import android.content.Context
import android.net.Uri
import com.example.myapplication.data.model.TravelEntry
import com.example.myapplication.util.ImageStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
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
    fun exportTo(dest: Uri, entries: List<TravelEntry>): Result<Int> = runCatching {
        val resolver = context.contentResolver
        resolver.openOutputStream(dest)?.use { os ->
            ZipOutputStream(BufferedOutputStream(os)).use { zip ->
                val exported = entries.map { entry ->
                    // Replace each photo URI with a zip-relative path, writing its bytes as we go.
                    // Photos we can't read (revoked grants, deleted media) are dropped, not fatal.
                    val paths = entry.photoUris.mapIndexedNotNull { i, uriString ->
                        val bytes = readBytes(uriString) ?: return@mapIndexedNotNull null
                        val path = "photos/${entry.id}_$i.jpg"
                        zip.putNextEntry(ZipEntry(path))
                        zip.write(bytes)
                        zip.closeEntry()
                        path
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
     * Reads a backup zip at [src] and hands each restored entry to [onEntry] (which should upsert
     * it into the user's store). Photos are re-materialized into the shared gallery and the URIs
     * re-pointed. Returns the number of entries imported.
     */
    suspend fun importFrom(src: Uri, onEntry: suspend (TravelEntry) -> Unit): Result<Int> = runCatching {
        val resolver = context.contentResolver
        val photoBytes = mutableMapOf<String, ByteArray>()
        var backupJson: String? = null

        resolver.openInputStream(src)?.use { ins ->
            ZipInputStream(BufferedInputStream(ins)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val bytes = zip.readBytes() // ZipInputStream caps reads at the current entry
                    when {
                        name == "backup.json" -> backupJson = bytes.decodeToString()
                        name.startsWith("photos/") -> photoBytes[name] = bytes
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("Couldn't open the selected file.")

        val backup = json.decodeFromString<BackupFile>(
            backupJson ?: error("This doesn't look like a Macaco backup.")
        )

        backup.entries.forEach { entry ->
            val newUris = entry.photoUris.mapNotNull { path ->
                photoBytes[path]?.let { ImageStorage.persistBytesToGallery(context, it) }
            }
            onEntry(entry.copy(photoUris = newUris, driveFileIds = emptyList()))
        }
        backup.entries.size
    }

    /** Reads the bytes behind a `file://` or `content://` URI, or null if unreadable. */
    private fun readBytes(uriString: String): ByteArray? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { it.readBytes() }
    }.getOrNull()
}
