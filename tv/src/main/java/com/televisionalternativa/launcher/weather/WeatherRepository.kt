package com.televisionalternativa.launcher.weather

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Repositorio para obtener datos del clima desde Open-Meteo API.
 * Open-Meteo es gratis y no requiere API key.
 */
class WeatherRepository {

    companion object {
        private const val TAG = "WeatherRepository"
        private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"
        private const val CONNECT_TIMEOUT = 10_000
        private const val READ_TIMEOUT = 10_000
    }

    private val executor = Executors.newSingleThreadExecutor()

    // Cache
    private var cachedWeather: WeatherData? = null
    private var lastFetchTime: Long = 0
    private val CACHE_DURATION = 15 * 60 * 1000L // 15 minutos

    /**
     * Obtiene el clima actual para las coordenadas dadas.
     */
    fun getWeather(
        latitude: Double,
        longitude: Double,
        callback: (WeatherData?) -> Unit
    ) {
        // Devolver cache si es reciente
        if (cachedWeather != null && (System.currentTimeMillis() - lastFetchTime) < CACHE_DURATION) {
            Log.d(TAG, "Returning cached weather")
            callback(cachedWeather)
            return
        }

        executor.execute {
            val weather = fetchWeatherSync(latitude, longitude)
            if (weather != null) {
                cachedWeather = weather
                lastFetchTime = System.currentTimeMillis()
            }
            callback(weather)
        }
    }

    /**
     * Fetch síncrono del clima. NO llamar desde UI thread.
     */
    private fun fetchWeatherSync(latitude: Double, longitude: Double): WeatherData? {
        var connection: HttpURLConnection? = null

        return try {
            val url = URL(
                "$BASE_URL?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,weather_code,is_day" +
                "&timezone=auto"
            )

            Log.d(TAG, "Fetching weather from: $url")

            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Weather API error: $responseCode")
                return null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parseWeatherResponse(response)

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching weather", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Parsea la respuesta JSON de Open-Meteo.
     */
    private fun parseWeatherResponse(jsonString: String): WeatherData? {
        return try {
            val json = JSONObject(jsonString)
            val current = json.getJSONObject("current")

            val temperature = current.getDouble("temperature_2m")
            val weatherCode = current.getInt("weather_code")
            val isDay = current.getInt("is_day") == 1

            WeatherData(
                temperature = temperature,
                weatherCode = weatherCode,
                isDay = isDay,
                description = getWeatherDescription(weatherCode),
                iconRes = getWeatherIcon(weatherCode, isDay)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing weather response", e)
            null
        }
    }

    /**
     * Convierte el código de clima de WMO a descripción.
     * https://open-meteo.com/en/docs#weathervariables
     */
    private fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "Despejado"
            1, 2, 3 -> "Parcialmente nublado"
            45, 48 -> "Niebla"
            51, 53, 55 -> "Llovizna"
            56, 57 -> "Llovizna helada"
            61, 63, 65 -> "Lluvia"
            66, 67 -> "Lluvia helada"
            71, 73, 75 -> "Nieve"
            77 -> "Granizo"
            80, 81, 82 -> "Chubascos"
            85, 86 -> "Nieve"
            95 -> "Tormenta"
            96, 99 -> "Tormenta con granizo"
            else -> "Desconocido"
        }
    }

    /**
     * Obtiene el recurso de icono según el código de clima.
     */
    private fun getWeatherIcon(code: Int, isDay: Boolean): String {
        return when (code) {
            0 -> if (isDay) "ic_weather_sunny" else "ic_weather_clear_night"
            1, 2, 3 -> if (isDay) "ic_weather_partly_cloudy" else "ic_weather_cloudy_night"
            45, 48 -> "ic_weather_fog"
            51, 53, 55, 56, 57 -> "ic_weather_drizzle"
            61, 63, 65, 66, 67 -> "ic_weather_rain"
            71, 73, 75, 77, 85, 86 -> "ic_weather_snow"
            80, 81, 82 -> "ic_weather_showers"
            95, 96, 99 -> "ic_weather_storm"
            else -> "ic_weather_cloudy"
        }
    }

    /**
     * Limpia el cache.
     */
    fun clearCache() {
        cachedWeather = null
        lastFetchTime = 0
    }
}

/**
 * Datos del clima.
 */
data class WeatherData(
    val temperature: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val description: String,
    val iconRes: String
) {
    /**
     * Temperatura formateada como string.
     */
    fun getTemperatureString(): String {
        return "${temperature.toInt()}°"
    }
}
