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

    data class Result(val weatherCode: Int, val tempMaxC: Double)

    /** Returns null for a blank location, a future date (nothing to look up yet — Open-Meteo's
     *  archive API is historical only), a failed geocode, or any network/parse failure. Always
     *  call from a background dispatcher — this does blocking I/O. */
    suspend fun fetch(context: Context, location: String, dateMillis: Long): Result? =
        withContext(Dispatchers.IO) {
            if (location.isBlank() || dateMillis > System.currentTimeMillis()) return@withContext null
            val (lat, lon) = geocode(context, location) ?: return@withContext null
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dateMillis))
            val json = runCatching {
                val url = URL(
                    "$ARCHIVE_URL?latitude=$lat&longitude=$lon" +
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
                    tempMaxC = daily.getJSONArray("temperature_2m_max").getDouble(0)
                )
            }.getOrNull()
        }

    private fun geocode(context: Context, location: String): Pair<Double, Double>? = runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context).getFromLocationName(location, 1)?.firstOrNull()
            ?.let { it.latitude to it.longitude }
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

    /** °C → the display string appropriate to the device locale (°F for a US locale, °C for
     *  everyone else) — matches how most weather apps localize without a settings toggle. */
    fun formatTemp(context: Context, tempC: Double): String {
        val useF = Locale.getDefault().country == "US"
        val value = if (useF) (tempC * 9 / 5 + 32) else tempC
        return context.getString(
            if (useF) R.string.weather_temp_f else R.string.weather_temp_c,
            value.toInt()
        )
    }
}
