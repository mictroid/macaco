package com.houseofmmminq.macaco.util

import android.content.Context
import android.util.Log
import java.io.InputStream
import java.text.Normalizer
import java.util.zip.GZIPInputStream

/**
 * Offline city lookup for the location autocomplete. Reads a bundled city list
 * ("City, Country" per line) once, caches it in memory, and matches by prefix.
 *
 * The source asset is committed gzipped (`cities.txt.gz`, ~1.6 MB vs ~4.2 MB raw) to keep the
 * repo small, but AAPT auto-extracts `.gz` assets at build time and ships them decompressed as
 * `cities.txt`. [openCitiesStream] therefore handles both layouts so the lookup works regardless
 * of how the asset ended up in the APK.
 *
 * Matching is diacritic-insensitive: many bundled entries carry native accents (Naxos is stored
 * as "Náxos", Santorini's town as "Firá"), and most users type the plain-ASCII spelling. A folded
 * (NFD, marks-stripped) copy of the list is cached alongside the display list so this costs
 * nothing per keystroke.
 *
 * Offline by design — no network or API key needed. Swap [search] for a geocoding API call if
 * full worldwide coverage is ever required.
 */
object Cities {
    @Volatile private var cache: List<String>? = null
    @Volatile private var normalizedCache: List<String>? = null

    private val DIACRITICS_REGEX = Regex("\\p{Mn}+")

    /** Loads and caches the bundled city list. Safe to call repeatedly; the file is read once. */
    fun all(context: Context): List<String> {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: runCatching {
                openCitiesStream(context.applicationContext).bufferedReader().useLines { lines ->
                    lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
                }
            }.onFailure { Log.e("Cities", "Failed to load bundled city list", it) }
                .getOrDefault(emptyList())
                .also { cache = it; normalizedCache = it.map(::normalize) }
        }
    }

    /**
     * Opens the bundled city list as a text stream. Prefers the gzipped `cities.txt.gz` when the
     * APK actually contains it; falls back to the AAPT-extracted plain `cities.txt`.
     */
    private fun openCitiesStream(context: Context): InputStream {
        val assets = context.assets
        return runCatching { GZIPInputStream(assets.open("cities.txt.gz")) }
            .getOrElse { assets.open("cities.txt") }
    }

    /** Strips diacritics so "Naxos" folds the same as "Náxos", "Fira" the same as "Firá". */
    private fun normalize(s: String): String =
        Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replace(DIACRITICS_REGEX, "")

    /**
     * True if [text] starts with [query], ignoring case and diacritics. Shared by [search] and by
     * callers matching against a smaller list (e.g. a user's own past locations).
     */
    fun matchesPrefix(text: String, query: String): Boolean =
        normalize(text).startsWith(normalize(query), ignoreCase = true)

    /**
     * Cities whose name starts with [query], ignoring case and diacritics (e.g. "Ber" → Berlin,
     * Bern, Bergen; "Naxos" → Náxos). Pass the already-loaded [cities] list so this stays cheap
     * to call on every keystroke.
     */
    fun search(cities: List<String>, query: String, limit: Int = 6): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val normalizedQuery = normalize(q)
        // Reuse the precomputed fold when `cities` is the cached list (the normal case); fall
        // back to computing it on the fly otherwise so the function still works standalone.
        val normalized = normalizedCache?.takeIf { it.size == cities.size } ?: cities.map(::normalize)
        return cities.indices.asSequence()
            .filter { normalized[it].startsWith(normalizedQuery, ignoreCase = true) }
            .take(limit)
            .map { cities[it] }
            .toList()
    }
}
