package com.houseofmmminq.macaco.data.sync

import android.content.Context
import android.net.Uri
import com.houseofmmminq.macaco.data.model.TravelEntry
import com.houseofmmminq.macaco.util.ImageStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
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

    private companion object {
        // Longest edge of decoded bitmap for compact export. At 2048 px and JPEG 80% the output
        // is indistinguishable from the original at phone-screen sizes, and fits comfortably in
        // heap even on low-RAM devices (2048 × 1536 × RGB_565 = ~6 MB).
        private const val MAX_COMPACT_DIMENSION = 2048
    }

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
     * Decodes the photo at [uriString] at a memory-safe resolution, re-encodes at JPEG 80%
     * directly into [out], and recycles the bitmap. Returns false only if the photo is
     * genuinely unreadable (revoked URI, deleted media) — OOM is prevented by subsampling.
     */
    private fun writeCompressed(uriString: String, out: java.io.OutputStream): Boolean {
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver

        // Pass 1: read dimensions only — no bitmap allocation.
        val boundsOpts = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        resolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, boundsOpts)
        } ?: return false  // URI not readable

        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        if (rawW <= 0 || rawH <= 0) return false  // unrecognised format

        // Compute smallest power-of-2 sample that brings longest edge within MAX_COMPACT_DIMENSION.
        var sampleSize = 1
        var longestEdge = maxOf(rawW, rawH)
        while (longestEdge > MAX_COMPACT_DIMENSION) {
            sampleSize *= 2
            longestEdge /= 2
        }

        // Pass 2: decode at subsampled size with RGB_565 (2 bytes/px instead of 4).
        val decodeOpts = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        val bitmap = runCatching {
            resolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, decodeOpts)
            }
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
    suspend fun importFrom(
        src: Uri,
        onEntry: suspend (TravelEntry) -> Unit,
        // Non-suspend on purpose: it's invoked from the synchronous CountingInputStream read
        // callback as well as the suspend body. Callers just push the values into a StateFlow.
        onProgress: (phase: ImportPhase, current: Int, total: Int) -> Unit = { _, _, _ -> }
    ): Result<Int> = runCatching {
        val resolver = context.contentResolver

        // Stream photo bytes to temp files rather than holding the whole zip in memory.
        // A large backup (hundreds of MB) would otherwise exhaust the heap and crash silently.
        val tempDir = File(context.cacheDir, "backup_import_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        var backupJson: String? = null

        // Total download size in MB for pass-1 progress; -1 if the provider doesn't report a length
        // (e.g. some Drive streams), in which case the UI shows an indeterminate bar with a label.
        val totalMb: Int = runCatching {
            resolver.openAssetFileDescriptor(src, "r")?.use { it.length }
        }.getOrNull().let { if (it == null || it <= 0L) -1 else (it / 1024 / 1024).toInt() }

        try {
            // ── Phase 1: download the entire SAF source to a local temp file ──────────────────────
            // Opening ZipInputStream directly over a Drive-backed SAF stream causes ZLIB errors:
            // closeEntry() tries to drain remaining compressed bytes, but Drive delivers data in
            // network chunks and can stall mid-drain, desyncing the ZLIB decompressor. Copying to a
            // local file first avoids this entirely — local file reads are always contiguous and
            // never stall. A CountingInputStream reports bytes read so the UI can show download
            // progress for a large (hundreds-of-MB) Drive-backed zip that streams in slowly.
            onProgress(ImportPhase.DOWNLOADING, 0, totalMb)

            val srcZip = File(tempDir, "source.zip")
            resolver.openInputStream(src)?.use { rawIns ->
                var lastReportedMb = -1
                val counting = CountingInputStream(rawIns) { totalBytes ->
                    // Report only on each new whole-MB boundary to avoid flooding the UI.
                    val mb = (totalBytes / 1024 / 1024).toInt()
                    if (mb != lastReportedMb) {
                        lastReportedMb = mb
                        onProgress(ImportPhase.DOWNLOADING, mb, totalMb)
                    }
                }
                srcZip.outputStream().buffered(65_536).use { out ->
                    counting.copyTo(out, bufferSize = 65_536)
                }
            } ?: error("Couldn't open the selected file.")

            // ── Phase 2: extract from the local zip ───────────────────────────────────────────────
            // ZipFile reads the central directory from the end of the file on open. A truncated
            // download (Drive SAF returning premature EOF during the copy above) has no central
            // directory → an immediate, clear ZipException rather than a confusing ZLIB error
            // discovered mid-stream by ZipInputStream.
            try {
                java.util.zip.ZipFile(srcZip).use { zipFile ->
                    val entries = zipFile.entries().toList()

                    // backup.json is not wrapped — if this fails the import cannot proceed.
                    entries.find { it.name == "backup.json" }?.let { entry ->
                        backupJson = zipFile.getInputStream(entry).use { it.readBytes().decodeToString() }
                    }

                    // Each photo entry is extracted independently. A corrupt entry is skipped rather
                    // than aborting the whole import. The RESTORING phase already handles a missing
                    // temp file with: if (!tempFile.exists()) return@mapNotNull null.
                    // Flatten "photos/foo.jpg" → "photos_foo.jpg" so it's a valid filename.
                    entries.filter { it.name.startsWith("photos/") }.forEach { entry ->
                        runCatching {
                            File(tempDir, entry.name.replace("/", "_"))
                                .outputStream()
                                .buffered(65_536)
                                .use { out -> zipFile.getInputStream(entry).copyTo(out, bufferSize = 65_536) }
                        }
                        // Silently skip unreadable entries — the photo will be absent from the
                        // restored entry rather than blocking restoration of every other entry.
                    }
                }
            } catch (e: java.util.zip.ZipException) {
                // Central directory missing → file was truncated during download.
                error(
                    "The backup file is incomplete — it may not have finished downloading from Drive. " +
                    "Open the file in the Files app to force a full download, then try importing again."
                )
            }

            srcZip.delete() // no longer needed; photos are extracted into tempDir

            val backup = json.decodeFromString<BackupFile>(
                backupJson ?: error("This doesn't look like a Macaco backup.")
            )

            // Pass 2 (restore): one photo at a time — read its temp file, write to gallery, delete it.
            onProgress(ImportPhase.RESTORING, 0, backup.entries.size)
            backup.entries.forEachIndexed { index, entry ->
                onProgress(ImportPhase.RESTORING, index + 1, backup.entries.size)
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

    /** Phase of a backup import, surfaced to the UI for a phase-aware progress label. */
    enum class ImportPhase { DOWNLOADING, RESTORING }

    /** Wraps an [InputStream] and reports the cumulative byte count read, so a slow SAF/Drive
     *  stream can drive a determinate download progress bar. */
    private class CountingInputStream(
        private val wrapped: InputStream,
        private val onRead: (totalBytes: Long) -> Unit
    ) : InputStream() {
        private var total = 0L
        override fun read(): Int = wrapped.read().also { if (it >= 0) { total++; onRead(total) } }
        override fun read(b: ByteArray, off: Int, len: Int): Int =
            wrapped.read(b, off, len).also { if (it > 0) { total += it; onRead(total) } }
        override fun close() = wrapped.close()
    }

    /** Reads the bytes behind a `file://` or `content://` URI, or null if unreadable. */
    private fun readBytes(uriString: String): ByteArray? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { it.readBytes() }
    }.getOrNull()
}
