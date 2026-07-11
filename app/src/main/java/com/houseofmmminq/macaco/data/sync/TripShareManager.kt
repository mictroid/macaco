package com.houseofmmminq.macaco.data.sync

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.houseofmmminq.macaco.data.model.TravelEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Read-only, unauthenticated web links for a trip. See docs/code-brief-shared-trip-links.md for
 * why this is architecturally different from everything else in the app (public data, a new
 * Storage path, security rules this repo doesn't own) before changing this file.
 *
 * NOTE: createShareLink() fails with a permission-denied error until the Firestore + Storage
 * security rules for /shared_trips are applied in the Firebase console (not in this repo), and the
 * viewer page at .../trip/ is a separate static-web task. Both are out of scope here.
 */
class TripShareManager {

    data class ShareResult(val shareId: String, val url: String)

    private companion object { private const val MAX_PHOTOS = 12 }

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val sharesCollection get() = firestore.collection("shared_trips")

    suspend fun createShareLink(
        ownerUid: String,
        tripName: String,
        entries: List<TravelEntry>,
        expiryDays: Int?   // null = never expires — the UI should not default to this, see Change 4
    ): Result<ShareResult> = withContext(Dispatchers.IO) {
        runCatching {
            val shareId = UUID.randomUUID().toString()
            val photoUrls = entries.flatMap { it.photoUris }.take(MAX_PHOTOS)
                .mapIndexedNotNull { i, uriString ->
                    runCatching {
                        val ref = storage.reference.child("shared_trips/$shareId/photo_$i.jpg")
                        ref.putFile(Uri.parse(uriString)).await()
                        ref.downloadUrl.await().toString()
                    }.getOrNull()
                }
            val publicEntries = entries.map { entry ->
                mapOf(
                    "title" to entry.title,
                    "location" to entry.location,
                    "dateMillis" to entry.dateMillis,
                    // Truncated — a public page, not the full private journal entry.
                    "description" to entry.description.take(400)
                )
            }
            val expiresAt = expiryDays?.let { System.currentTimeMillis() + TimeUnit.DAYS.toMillis(it.toLong()) }
            sharesCollection.document(shareId).set(
                mapOf(
                    "ownerUid" to ownerUid,
                    "tripName" to tripName,
                    "createdAt" to System.currentTimeMillis(),
                    "expiresAt" to expiresAt,
                    "photoUrls" to photoUrls,
                    "entries" to publicEntries
                )
            ).await()
            // trip/index.html (the actual viewer) is a separate web task — see this brief's Scope.
            ShareResult(shareId, "https://mictroid.github.io/macaco/trip/?id=$shareId")
        }
    }

    suspend fun revokeShareLink(shareId: String, photoCount: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            repeat(photoCount) { i ->
                runCatching { storage.reference.child("shared_trips/$shareId/photo_$i.jpg").delete().await() }
            }
            sharesCollection.document(shareId).delete().await()
            Unit
        }
    }

    /** This user's own active shares (shareId to tripName), so the UI can show "already shared"
     *  per trip and offer revoke. Works under the Change 5 read rule, which doesn't restrict
     *  reads by owner — public link access requires that — so an authenticated owner querying
     *  their own docs by ownerUid is just an ordinary allowed read, no separate index needed. */
    suspend fun listMyShares(ownerUid: String): Result<List<Pair<String, String>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                sharesCollection.whereEqualTo("ownerUid", ownerUid).get().await()
                    .documents.map { it.id to (it.getString("tripName") ?: "") }
            }
        }
}
