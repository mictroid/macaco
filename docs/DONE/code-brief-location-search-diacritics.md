# Macaco — Location Autocomplete: Accent-Insensitive Matching

Fixes location search failing to surface accented place names (e.g. typing "Naxos" doesn't
match the bundled entry "Náxos", "Fira" doesn't match "Firá"). Touches `Cities.kt` and
`NewEditEntryScreen.kt`.

---

## Diacritic-insensitive city search

**Problem:** The bundled offline city list (`cities.txt.gz`, ~206k GeoNames entries) stores many
place names with their native diacritics — Naxos is `Náxos`, Santorini's actual town is `Firá`.
`Cities.search()` matches with a plain `startsWith(query, ignoreCase = true)`, which is a raw
character comparison. Accents are not folded, so a user typing the plain-ASCII spelling most
people use ("Naxos", "Fira") gets zero matches even though the place is in the dataset. Note:
"Crete" and "Santorini" as region/tourism names are separately out of scope — GeoNames only lists
actual towns (Chaniá, Irákleio, Firá), and no fix to matching logic can invent an entry that
doesn't exist. This brief only fixes the diacritic gap.

**Fix:** Add a `java.text.Normalizer`-based fold (NFD decomposition + strip combining marks) and
compare against the folded form instead of the raw string. Precompute the folded list once
alongside the existing city cache so this doesn't cost anything per keystroke. Expose a small
`matchesPrefix` helper so the same folding logic can be reused for the "past locations" filter in
`NewEditEntryScreen.kt` (same bug applies there — a user who previously logged a trip to "Náxos"
won't see it suggested back when they type "Naxos" next time).

```kotlin
// BEFORE — app/src/main/java/com/houseofmmminq/macaco/util/Cities.kt
package com.houseofmmminq.macaco.util

import android.content.Context
import android.util.Log
import java.io.InputStream
import java.util.zip.GZIPInputStream

object Cities {
    @Volatile private var cache: List<String>? = null

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

    private fun openCitiesStream(context: Context): InputStream {
        val assets = context.assets
        return runCatching { GZIPInputStream(assets.open("cities.txt.gz")) }
            .getOrElse { assets.open("cities.txt") }
    }

    fun search(cities: List<String>, query: String, limit: Int = 6): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return cities.asSequence()
            .filter { it.startsWith(q, ignoreCase = true) }
            .take(limit)
            .toList()
    }
}
```

```kotlin
// AFTER — app/src/main/java/com/houseofmmminq/macaco/util/Cities.kt
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
```

**File:** `app/src/main/java/com/houseofmmminq/macaco/util/Cities.kt`

---

## Apply the same fold to past-location suggestions

**Problem:** `LocationField` in `NewEditEntryScreen.kt` filters the user's own previously-used
locations with the same unfolded `startsWith`, so a past entry logged as "Náxos" won't resurface
when the user later types "Naxos".

**Fix:** Swap the raw `startsWith` for `Cities.matchesPrefix`.

```kotlin
// BEFORE — app/src/main/java/com/houseofmmminq/macaco/ui/screens/NewEditEntryScreen.kt (~line 1112)
val matches = remember(value, suggestions, cities) {
    val q = value.trim()
    if (q.isBlank()) emptyList()
    else {
        val past = suggestions.filter { it.startsWith(q, ignoreCase = true) }
        (past + Cities.search(cities, q, limit = 8))
            .distinctBy { it.lowercase() }
            .filterNot { it.equals(q, ignoreCase = true) }
            .take(6)
    }
}
```

```kotlin
// AFTER
val matches = remember(value, suggestions, cities) {
    val q = value.trim()
    if (q.isBlank()) emptyList()
    else {
        val past = suggestions.filter { Cities.matchesPrefix(it, q) }
        (past + Cities.search(cities, q, limit = 8))
            .distinctBy { it.lowercase() }
            .filterNot { it.equals(q, ignoreCase = true) }
            .take(6)
    }
}
```

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/NewEditEntryScreen.kt`

---

## Out of scope

"Crete" and "Santorini" as search terms will still return nothing — they're region/tourism names,
not GeoNames municipalities. The dataset has their actual towns (Chaniá, Irákleio, Rethymno,
Firá), which this fix makes reachable by plain-ASCII spelling. Adding a curated alias list
(island name → representative town) is a separate, larger feature if wanted later.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Diacritic-insensitive prefix matching in `Cities.search` + new `matchesPrefix` helper | `Cities.kt` |
| 2 | Past-location filter reuses `Cities.matchesPrefix` instead of raw `startsWith` | `NewEditEntryScreen.kt` |
