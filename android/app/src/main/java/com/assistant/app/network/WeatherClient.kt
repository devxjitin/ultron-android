package com.assistant.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/** A place the weather can be asked for, with the name to read out. */
data class WeatherPlace(val label: String, val latitude: Double, val longitude: Double)

/** Free weather from Open-Meteo: no account and no key. Place names are looked up with its geocoding service. */
object WeatherClient {
    private val http = HttpClients.shared.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun findPlace(name: String): Result<WeatherPlace> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
                .addQueryParameter("name", name.take(100))
                .addQueryParameter("count", "1")
                .addQueryParameter("language", "en")
                .addQueryParameter("format", "json")
                .build()
            parsePlace(get(url.toString())) ?: throw IllegalStateException("No place called \"$name\" was found.")
        }
    }

    suspend fun forecast(place: WeatherPlace, days: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
                .addQueryParameter("latitude", "%.4f".format(Locale.ENGLISH, place.latitude))
                .addQueryParameter("longitude", "%.4f".format(Locale.ENGLISH, place.longitude))
                .addQueryParameter("current", "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m")
                .addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                .addQueryParameter("timezone", "auto")
                .addQueryParameter("forecast_days", days.coerceIn(1, 7).toString())
                .build()
            describe(place.label, get(url.toString()), LocalDate.now())
        }
    }

    private fun get(url: String): String =
        http.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("The weather service answered ${response.code}.")
            response.body?.string().orEmpty()
        }

    /** The first match of a geocoding answer, labelled "Name, Region, Country", or null when there is none. */
    internal fun parsePlace(json: String): WeatherPlace? {
        val first = JSONObject(json).optJSONArray("results")?.optJSONObject(0) ?: return null
        val label = listOf("name", "admin1", "country").mapNotNull { first.optString(it).takeIf { v -> v.isNotBlank() } }
            .distinct().joinToString(", ")
        if (!first.has("latitude") || !first.has("longitude")) return null
        return WeatherPlace(label, first.getDouble("latitude"), first.getDouble("longitude"))
    }

    /** Turns a forecast answer into the plain text the model reads. [today] only names the days. */
    internal fun describe(label: String, json: String, today: LocalDate): String {
        val root = JSONObject(json)
        val lines = mutableListOf("status=ok")
        root.optJSONObject("current")?.let { now ->
            lines += "Weather in $label right now: %.0f C (feels like %.0f C), %s, humidity %d%%, wind %.0f km/h, rain %.1f mm.".format(
                Locale.ENGLISH, now.optDouble("temperature_2m"), now.optDouble("apparent_temperature"),
                condition(now.optInt("weather_code", -1)), now.optInt("relative_humidity_2m"),
                now.optDouble("wind_speed_10m"), now.optDouble("precipitation")
            )
        }
        val daily = root.optJSONObject("daily")
        val dates = daily?.optJSONArray("time")
        if (daily != null && dates != null) {
            for (i in 0 until dates.length()) {
                val date = runCatching { LocalDate.parse(dates.getString(i)) }.getOrNull()
                val name = when (date) {
                    today -> "Today"
                    today.plusDays(1) -> "Tomorrow"
                    null -> dates.getString(i)
                    else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + date.dayOfMonth
                }
                val rain = daily.optJSONArray("precipitation_probability_max")?.takeIf { !it.isNull(i) }?.optInt(i)
                lines += "$name: %.0f to %.0f C, %s%s.".format(
                    Locale.ENGLISH,
                    daily.optJSONArray("temperature_2m_min")?.optDouble(i) ?: Double.NaN,
                    daily.optJSONArray("temperature_2m_max")?.optDouble(i) ?: Double.NaN,
                    condition(daily.optJSONArray("weather_code")?.optInt(i, -1) ?: -1),
                    rain?.let { ", rain chance $it%" }.orEmpty()
                )
            }
        }
        return lines.joinToString("\n")
    }

    /** WMO weather interpretation codes, as Open-Meteo reports them. */
    internal fun condition(code: Int): String = when (code) {
        0 -> "clear sky"
        1 -> "mostly clear"
        2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        51, 53, 55 -> "drizzle"
        56, 57 -> "freezing drizzle"
        61 -> "light rain"
        63 -> "rain"
        65 -> "heavy rain"
        66, 67 -> "freezing rain"
        71, 73, 75, 77 -> "snow"
        80 -> "light rain showers"
        81 -> "rain showers"
        82 -> "violent rain showers"
        85, 86 -> "snow showers"
        95 -> "thunderstorm"
        96, 99 -> "thunderstorm with hail"
        else -> "unknown conditions"
    }
}
