package com.example.myapplication.util

import android.content.Context
import android.util.Log
import java.io.InputStream
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
                openCitiesStream(context.applicationContext).bufferedReader().useLines { lines ->
                    lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
                }
            }.onFailure { Log.e("Cities", "Failed to load bundled city list", it) }
                .getOrDefault(emptyList()).also { cache = it }
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
