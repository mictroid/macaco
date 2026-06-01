package com.example.myapplication.data.storage

import android.content.Context
import com.example.myapplication.data.model.TravelEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One-time import of entries from the legacy local store (`files/entries.json`, written by the
 * retired [EntryStorage]) into the cloud ([CloudEntrySync] / Firestore).
 *
 * Older app versions kept entries only on-device, so upgrading to the cloud version made them
 * "disappear" (and a reinstall wiped the file). This carries any leftover local entries into the
 * currently signed-in user's cloud account. After a successful import the file is renamed to
 * `entries.json.imported` so the migration never runs again. Re-saving uses each entry's original
 * id, so it is idempotent even if it somehow runs twice.
 */
object LegacyEntryMigration {
    private val json = Json { ignoreUnknownKeys = true }
    private const val LEGACY = "entries.json"
    private const val DONE = "entries.json.imported"

    /** Requires a signed-in user (so [CloudEntrySync.save] can write). Safe to call repeatedly. */
    suspend fun run(context: Context, cloudEntrySync: CloudEntrySync) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, LEGACY)
        if (!file.exists()) return@withContext

        val entries = runCatching { json.decodeFromString<List<TravelEntry>>(file.readText()) }
            .getOrNull()
        if (entries == null) {
            // Unparseable — move it aside so we don't retry every launch.
            file.renameTo(File(context.filesDir, DONE))
            return@withContext
        }

        runCatching {
            entries.forEach { entry ->
                // Legacy photo URIs were temporary Photo Picker grants that are no longer readable,
                // so drop them rather than carry dead image references into the cloud entry.
                cloudEntrySync.save(entry.copy(photoUris = emptyList()))
            }
        }.onSuccess {
            file.renameTo(File(context.filesDir, DONE))
        }
        // On failure (e.g. write rejected) the file is left in place and retried on the next launch.
    }
}
