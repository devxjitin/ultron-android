package com.assistant.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeatherClientTest {
    @Test fun parsesFirstGeocodingMatch() {
        val place = WeatherClient.parsePlace(
            """{"results":[{"name":"Jaipur","admin1":"Rajasthan","country":"India","latitude":26.9196,"longitude":75.7878}]}"""
        )!!
        assertEquals("Jaipur, Rajasthan, India", place.label)
        assertEquals(26.9196, place.latitude, 0.0001)
    }

    @Test fun noGeocodingMatchIsNull() {
        assertNull(WeatherClient.parsePlace("""{"generationtime_ms":0.5}"""))
    }

    @Test fun describesNowAndDays() {
        val json = """{"current":{"temperature_2m":31.4,"apparent_temperature":35.2,"relative_humidity_2m":48,
            "precipitation":0.0,"weather_code":2,"wind_speed_10m":12.0},
            "daily":{"time":["2026-10-22","2026-10-23"],"weather_code":[2,61],
            "temperature_2m_max":[33.0,30.1],"temperature_2m_min":[22.0,21.5],"precipitation_probability_max":[10,70]}}"""
        val text = WeatherClient.describe("Jaipur", json, LocalDate.parse("2026-10-22"))
        assertTrue(text.startsWith("status=ok"))
        assertTrue(text, "Weather in Jaipur right now: 31 C (feels like 35 C), partly cloudy, humidity 48%, wind 12 km/h" in text)
        assertTrue(text, "Today: 22 to 33 C, partly cloudy, rain chance 10%." in text)
        assertTrue(text, "Tomorrow: 22 to 30 C, light rain, rain chance 70%." in text)
    }

    @Test fun unknownCodeIsNotInvented() {
        assertEquals("unknown conditions", WeatherClient.condition(1234))
    }
}
