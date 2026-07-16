package com.houseofmmminq.macaco.util

import android.content.Context
import android.location.Geocoder
import com.houseofmmminq.macaco.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Historical weather for an entry's location + date, via Open-Meteo's free archive API
 * (https://open-meteo.com — no API key, no attribution required for non-commercial rate limits).
 * Deliberately narrow: one location, one date, no caching beyond what's persisted on the entry
 * itself (see TravelEntry.weatherCode / weatherTempMaxC).
 */
object WeatherLookup {

    private const val ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive"

    data class Result(val weatherCode: Int, val tempMaxC: Double, val isFahrenheit: Boolean)

    /** Returns null for a blank location, a future date (nothing to look up yet — Open-Meteo's
     *  archive API is historical only), a failed geocode, or any network/parse failure. Always
     *  call from a background dispatcher — this does blocking I/O. */
    suspend fun fetch(context: Context, location: String, dateMillis: Long): Result? =
        withContext(Dispatchers.IO) {
            if (location.isBlank() || dateMillis > System.currentTimeMillis()) return@withContext null
            val geo = geocode(context, location) ?: return@withContext null
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dateMillis))
            val json = runCatching {
                val url = URL(
                    "$ARCHIVE_URL?latitude=${geo.lat}&longitude=${geo.lon}" +
                        "&start_date=$dateStr&end_date=$dateStr" +
                        "&daily=weathercode,temperature_2m_max&timezone=auto"
                )
                (url.openConnection() as HttpURLConnection).run {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    inputStream.bufferedReader().use { it.readText() }
                }
            }.getOrNull() ?: return@withContext null
            runCatching {
                val daily = JSONObject(json).getJSONObject("daily")
                Result(
                    weatherCode = daily.getJSONArray("weathercode").getInt(0),
                    tempMaxC = daily.getJSONArray("temperature_2m_max").getDouble(0),
                    // Imperial only for the US — decided from where the *entry* is, not the phone's
                    // display-language setting (that was the bug: a device set to English (US) but
                    // physically in Berlin was always showing °F).
                    isFahrenheit = geo.countryCode == "US"
                )
            }.getOrNull()
        }

    private data class Geo(val lat: Double, val lon: Double, val countryCode: String?)

    private fun geocode(context: Context, location: String): Geo? = runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context).getFromLocationName(location, 1)?.firstOrNull()
            ?.let { Geo(it.latitude, it.longitude, it.countryCode) }
    }.getOrNull()

    /** Maps Open-Meteo's WMO weather code to an emoji + localized short label for the chip. */
    fun describe(context: Context, code: Int): Pair<String, String> = when (code) {
        0 -> "☀️" to context.getString(R.string.weather_clear)
        1, 2, 3 -> "⛅" to context.getString(R.string.weather_partly_cloudy)
        45, 48 -> "🌫️" to context.getString(R.string.weather_fog)
        51, 53, 55, 56, 57 -> "🌦️" to context.getString(R.string.weather_drizzle)
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️" to context.getString(R.string.weather_rain)
        71, 73, 75, 77, 85, 86 -> "❄️" to context.getString(R.string.weather_snow)
        95, 96, 99 -> "⛈️" to context.getString(R.string.weather_storm)
        else -> "🌡️" to context.getString(R.string.weather_unknown)
    }

    /** °C → the display string for the unit this entry was fetched with — [isFahrenheit] is decided
     *  once at fetch time from the entry's own location (see [fetch]), not the device's locale, so a
     *  Berlin entry reads in °C even on a phone whose display language is English (US). */
    fun formatTemp(context: Context, tempC: Double, isFahrenheit: Boolean): String {
        val value = if (isFahrenheit) (tempC * 9 / 5 + 32) else tempC
        return context.getString(
            if (isFahrenheit) R.string.weather_temp_f else R.string.weather_temp_c,
            value.toInt()
        )
    }
}
