package com.houseofmmminq.macaco.data.model

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
    // Decided once at fetch time from the entry's own location's country (WeatherLookup.fetch),
    // not the device locale. Null on entries fetched before this field existed or with no weather
    // — EntryDetailScreen falls back to metric (false) for those, since most locales are metric.
    val weatherIsFahrenheit: Boolean? = null,
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

/** The single entry the home-screen widget shows: the most recent "on this day" match if one
 *  exists, otherwise the most recently created entry overall, or null for an empty journal. */
fun List<TravelEntry>.widgetHighlight(): TravelEntry? =
    onThisDayEntries().firstOrNull() ?: maxByOrNull { it.dateMillis }

/** Distinct trip names from these entries, alphabetical. Shared by the entry-editor autocomplete
 *  and the Print Book selection screen. */
fun List<TravelEntry>.tripNames(): List<String> =
    mapNotNull { it.tripName?.trim()?.ifBlank { null } }
        .distinct()
        .sorted()

/** Distinct, non-blank locations from these entries, alphabetical. */
fun List<TravelEntry>.locations(): List<String> =
    mapNotNull { it.location.trim().ifBlank { null } }
        .distinct()
        .sorted()

/** This entry's calendar year (device default time zone, matching how dates are entered/shown
 *  everywhere else in the app). */
private fun TravelEntry.year(): Int =
    Calendar.getInstance().apply { timeInMillis = dateMillis }.get(Calendar.YEAR)

/** Entries whose date falls in [year]. */
fun List<TravelEntry>.inYear(year: Int): List<TravelEntry> = filter { it.year() == year }

/** Distinct years that have at least one entry, most recent first — populates the year picker. */
fun List<TravelEntry>.entryYears(): List<Int> = map { it.year() }.distinct().sortedDescending()

data class YearRecap(
    val year: Int,
    val entryCount: Int,
    val tripCount: Int,
    val locationCount: Int,
    val mediaCount: Int,
    val topMood: String?,
    val topTag: String?,
    val busiestMonth: String?
)

/** Aggregates [this] (already the whole journal — filters internally) into a [YearRecap] for
 *  [year]. Returns a recap with all-zero counts if the year has no entries, rather than null —
 *  the screen always has something to render, even if empty. */
fun List<TravelEntry>.toYearRecap(year: Int): YearRecap {
    val yearEntries = inYear(year)
    val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
    return YearRecap(
        year = year,
        entryCount = yearEntries.size,
        tripCount = yearEntries.mapNotNull { it.tripName?.trim()?.ifBlank { null } }.distinct().size,
        locationCount = yearEntries.mapNotNull { it.location.trim().ifBlank { null } }.distinct().size,
        mediaCount = yearEntries.sumOf { it.photoUris.size + it.videoUris.size },
        topMood = yearEntries.map { it.mood }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
        topTag = yearEntries.tagsByFrequency().firstOrNull(),
        busiestMonth = yearEntries
            .groupingBy { monthFormat.format(Date(it.dateMillis)) }
            .eachCount().maxByOrNull { it.value }?.key
    )
}
