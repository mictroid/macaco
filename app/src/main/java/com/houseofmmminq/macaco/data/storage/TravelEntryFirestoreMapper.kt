package com.houseofmmminq.macaco.data.storage

import com.google.firebase.firestore.DocumentSnapshot
import com.houseofmmminq.macaco.data.model.TravelEntry

/**
 * The single source of truth for the Firestore document shape of a TravelEntry.
 * Every write (CloudEntrySync.save) and every read (CloudEntrySync's snapshot listener,
 * OnThisDayWidgetProvider's out-of-process query) MUST go through this pair — a field
 * added to TravelEntry that should sync must be added in BOTH functions here, nowhere else.
 * (The vc72 weatherIsFahrenheit no-op happened because these mappings lived in three places.)
 */
fun TravelEntry.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "title" to title,
    "location" to location,
    "dateMillis" to dateMillis,
    "description" to description,
    "mood" to mood,
    "photoUris" to photoUris,
    "tags" to tags,
    "createdAt" to createdAt,
    "driveFileIds" to driveFileIds,
    "tripName" to tripName,
    "videoUris" to videoUris,
    "videoFileIds" to videoFileIds,
    "mediaOrder" to mediaOrder,
    "weatherCode" to weatherCode,
    "weatherTempMaxC" to weatherTempMaxC,
    "weatherIsFahrenheit" to weatherIsFahrenheit
)

/** Returns null for a document missing the required title field (mirrors the old mapNotNull). */
fun DocumentSnapshot.toTravelEntry(): TravelEntry? = runCatching {
    TravelEntry(
        id = getString("id") ?: id,
        title = getString("title") ?: return@runCatching null,
        location = getString("location") ?: "",
        dateMillis = getLong("dateMillis") ?: 0L,
        description = getString("description") ?: "",
        mood = getString("mood") ?: "",
        // Photos stored as local URIs — visible on the device they were added from
        photoUris = (get("photoUris") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        tags = (get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        createdAt = getLong("createdAt") ?: 0L,
        driveFileIds = (get("driveFileIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        tripName = getString("tripName"),
        videoUris = (get("videoUris") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        videoFileIds = (get("videoFileIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        mediaOrder = (get("mediaOrder") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        weatherCode = getLong("weatherCode")?.toInt(),
        weatherTempMaxC = getDouble("weatherTempMaxC"),
        weatherIsFahrenheit = getBoolean("weatherIsFahrenheit")
    )
}.getOrNull()
