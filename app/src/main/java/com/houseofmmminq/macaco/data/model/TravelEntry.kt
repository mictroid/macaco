package com.houseofmmminq.macaco.data.model

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.UUID

@Serializable
data class TravelEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val location: String,
    val dateMillis: Long,
    val description: String,
    val mood: String,
    val photoUris: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    // Drive file IDs parallel to photoUris — "" means not yet uploaded.
    val driveFileIds: List<String> = emptyList(),
    val tripName: String? = null,
    // ── Video support (added post-vc53) ─────────────────────────────────────
    // videoUris: content:// URIs in Movies/Macaco (parallel to videoFileIds).
    val videoUris: List<String> = emptyList(),
    // videoFileIds: Drive file IDs parallel to videoUris; "" = not uploaded yet.
    val videoFileIds: List<String> = emptyList(),
    // mediaOrder: all media URIs (photos + videos) in user-defined display order.
    // Empty on old entries = backward-compatible: photos displayed first, then videos.
    val mediaOrder: List<String> = emptyList(),
    // ── Weather (added post-vc-print-book) ───────────────────────────────────
    // Fetched once, lazily, after an entry is first saved (see JournalViewModel.saveEntry).
    // null = not yet fetched, fetch failed, or the entry predates this feature. Never fetched for
    // future-dated entries (Open-Meteo's archive API is historical only).
    val weatherCode: Int? = null,
    val weatherTempMaxC: Double? = null,
)

/**
 * Entries whose trip date (dateMillis) falls on today's month and day but in a prior year,
 * sorted most-recent first. Used for the "On This Day" banner.
 */
fun List<TravelEntry>.onThisDayEntries(): List<TravelEntry> {
    val today = Calendar.getInstance()
    val todayMonth = today.get(Calendar.MONTH)
    val todayDay = today.get(Calendar.DAY_OF_MONTH)
    val todayYear = today.get(Calendar.YEAR)
    return filter { entry ->
        val cal = Calendar.getInstance().apply { timeInMillis = entry.dateMillis }
        cal.get(Calendar.MONTH) == todayMonth &&
            cal.get(Calendar.DAY_OF_MONTH) == todayDay &&
            cal.get(Calendar.YEAR) != todayYear
    }.sortedByDescending { it.dateMillis }
}

/** Distinct tags across the given entries, most-used first then alphabetical. */
fun List<TravelEntry>.tagsByFrequency(): List<String> =
    flatMap { it.tags }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }

/**
 * Entries whose title, description, location, tags, or trip name contain [query]
 * (case-insensitive, substring match). Blank query returns an empty list — the search screen
 * shows a placeholder rather than the whole journal, since "empty query, show everything" isn't
 * useful for a search screen the same way it is for the main list.
 */
fun List<TravelEntry>.matchingSearch(query: String): List<TravelEntry> {
    val q = query.trim()
    if (q.isBlank()) return emptyList()
    return filter { entry ->
        entry.title.contains(q, ignoreCase = true) ||
            entry.description.contains(q, ignoreCase = true) ||
            entry.location.contains(q, ignoreCase = true) ||
            entry.tags.any { it.contains(q, ignoreCase = true) } ||
            entry.tripName?.contains(q, ignoreCase = true) == true
    }.sortedByDescending { it.dateMillis }
}
