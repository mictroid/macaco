package com.example.myapplication.util

import android.content.Context
import java.util.zip.GZIPInputStream

/**
 * Offline city lookup for the location autocomplete. Reads a bundled, gzipped
 * `assets/cities.txt.gz` ("City, Country" per line) once, caches it in memory, and matches by
 * prefix. The list is gzipped (~1.6 MB vs ~4.2 MB raw) to keep the APK smaller and decompressed
 * on first load.
 *
 * Offline by design — no network or API key needed. Swap [search] for a geocoding API call if
 * full worldwide coverage is ever required.
 */
object Cities {
    @Volatile private var cache: List<String>? = null

    /** Loads and caches the bundled city list. Safe to call repeatedly; the file is read once. */
    fun all(context: Context): List<String> {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: runCatching {
                GZIPInputStream(context.applicationContext.assets.open("cities.txt.gz"))
                    .bufferedReader().useLines { lines ->
                        lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
                    }
            }.getOrDefault(emptyList()).also { cache = it }
        }
    }

    /**
     * Cities whose name starts with [query] (case-insensitive), e.g. "Ber" → Berlin, Bern, Bergen.
     * Pass the already-loaded [cities] list so this stays cheap to call on every keystroke.
     */
    fun search(cities: List<String>, query: String, limit: Int = 6): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return cities.asSequence()
            .filter { it.startsWith(q, ignoreCase = true) }
            .take(limit)
            .toList()
    }
}
