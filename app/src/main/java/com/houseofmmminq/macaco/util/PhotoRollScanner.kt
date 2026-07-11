package com.houseofmmminq.macaco.util

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.houseofmmminq.macaco.data.model.TravelEntry
import kotlin.math.abs

/**
 * Scans the last [LOOKBACK_DAYS] of the camera roll for geotagged photos this app didn't create,
 * clusters them by time + location, reverse-geocodes each cluster, and drops anything that already
 * roughly matches an existing entry — surfacing "new trip you haven't journaled yet" suggestions.
 *
 * Platform crux: reading unredacted GPS EXIF from a photo this app didn't write requires the
 * ACCESS_MEDIA_LOCATION permission AND opening the file via [MediaStore.setRequireOriginal]
 * (API 29+). Without both, GPS tags read null and no cluster is ever found.
 */
object PhotoRollScanner {

    data class PhotoCluster(
        val photoUris: List<Uri>,
        val startMillis: Long,
        val endMillis: Long,
        val placeName: String?
    )

    private const val LOOKBACK_DAYS = 14
    private const val CLUSTER_GAP_MILLIS = 6L * 60 * 60 * 1000   // 6 hours
    private const val CLUSTER_RADIUS_DEGREES = 0.02              // ~2 km at most inhabited latitudes

    /** Candidate clusters from the last [LOOKBACK_DAYS], excluding anything overlapping
     *  [existingEntries]'s own location+date coverage. Must run on a background dispatcher —
     *  does MediaStore queries, per-photo EXIF reads, and geocoding. */
    fun scan(context: Context, existingEntries: List<TravelEntry>): List<PhotoCluster> {
        val cutoff = System.currentTimeMillis() - LOOKBACK_DAYS * 24L * 60 * 60 * 1000
        val photos = queryGeotaggedPhotos(context, cutoff)
        if (photos.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<GeoPhoto>>()
        photos.sortedBy { it.dateTakenMillis }.forEach { photo ->
            val last = clusters.lastOrNull()?.lastOrNull()
            val sameCluster = last != null &&
                photo.dateTakenMillis - last.dateTakenMillis <= CLUSTER_GAP_MILLIS &&
                abs(photo.lat - last.lat) <= CLUSTER_RADIUS_DEGREES &&
                abs(photo.lon - last.lon) <= CLUSTER_RADIUS_DEGREES
            if (sameCluster) clusters.last().add(photo) else clusters.add(mutableListOf(photo))
        }

        val geocoder = Geocoder(context)
        return clusters.mapNotNull { group ->
            val startMillis = group.minOf { it.dateTakenMillis }
            val endMillis = group.maxOf { it.dateTakenMillis }
            val centroidLat = group.map { it.lat }.average()
            val centroidLon = group.map { it.lon }.average()
            val placeName = runCatching {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(centroidLat, centroidLon, 1)?.firstOrNull()
                    ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
            }.getOrNull()

            // Skip if an existing entry already covers roughly this place + time — coarse,
            // string/date-range based (exact photo-level de-dup isn't attempted since an
            // already-journaled photo is a *copy* in Pictures/Macaco with a different MediaStore
            // id than this camera-roll original, not a comparable URI).
            val alreadyJournaled = existingEntries.any { entry ->
                entry.location.isNotBlank() && placeName != null &&
                    entry.location.contains(placeName, ignoreCase = true) &&
                    entry.dateMillis in (startMillis - CLUSTER_GAP_MILLIS)..(endMillis + CLUSTER_GAP_MILLIS)
            }
            if (alreadyJournaled) null
            else PhotoCluster(
                photoUris = group.map { it.uri },
                startMillis = startMillis,
                endMillis = endMillis,
                placeName = placeName
            )
        }
    }

    private data class GeoPhoto(val uri: Uri, val dateTakenMillis: Long, val lat: Double, val lon: Double)

    private fun queryGeotaggedPhotos(context: Context, sinceMillis: Long): List<GeoPhoto> {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN)
        val selection = "${MediaStore.Images.Media.DATE_TAKEN} >= ?"
        val results = mutableListOf<GeoPhoto>()
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection,
            arrayOf(sinceMillis.toString()), "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val dateTaken = cursor.getLong(dateCol)
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                // The load-bearing bit: setRequireOriginal forces MediaStore to hand back the real
                // file (GPS tags intact) instead of a location-redacted copy — what you'd otherwise
                // get for any photo this app didn't itself write. Requires ACCESS_MEDIA_LOCATION;
                // without it this throws or the tags read null (both handled by runCatching below).
                val originalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
                } else uri
                val latLong = runCatching {
                    resolver.openInputStream(originalUri)?.use { ExifInterface(it).latLong }
                }.getOrNull()
                if (latLong != null) {
                    results += GeoPhoto(uri, dateTaken, latLong[0], latLong[1])
                }
            }
        }
        return results
    }
}
