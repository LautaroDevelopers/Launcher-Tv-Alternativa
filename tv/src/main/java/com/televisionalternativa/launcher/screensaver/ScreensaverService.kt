package com.televisionalternativa.launcher.screensaver

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.televisionalternativa.launcher.R
import com.televisionalternativa.launcher.weather.LocationHelper
import com.televisionalternativa.launcher.weather.WeatherRepository
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

/**
 * DreamService - Screensaver estilo Android TV/Google TV.
 * 
 * Features:
 * - Slideshow de fotos de fondo (Unsplash API)
 * - Reloj, fecha y clima en esquina inferior derecha
 * - Transiciones suaves entre imágenes
 * - Apps en segundo plano siguen funcionando (Spotify, etc.)
 */
class ScreensaverService : DreamService() {

    companion object {
        private const val TAG = "ScreensaverService"
        private const val IMAGE_CHANGE_INTERVAL = 30_000L // Cambiar imagen cada 30 segundos
        private const val CLOCK_UPDATE_INTERVAL = 1_000L // Actualizar reloj cada segundo
        private const val FADE_DURATION = 2_000L // 2 segundos de fade
        
        // Picsum Photos - Servicio gratuito y estable de fotos aleatorias
        // https://picsum.photos - No requiere API key
        private const val PICSUM_URL = "https://picsum.photos/1920/1080"
    }

    private lateinit var rootLayout: FrameLayout
    private lateinit var backgroundImage1: ImageView
    private lateinit var backgroundImage2: ImageView
    private lateinit var infoContainer: View
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var weatherIcon: ImageView
    private lateinit var temperatureText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val weatherRepository = WeatherRepository()
    private val random = Random()
    
    private var currentImageView = 1 // Alterna entre 1 y 2
    
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "AR"))

    private val clockUpdateRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, CLOCK_UPDATE_INTERVAL)
        }
    }

    private val imageChangeRunnable = object : Runnable {
        override fun run() {
            loadNextImage()
            handler.postDelayed(this, IMAGE_CHANGE_INTERVAL)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d(TAG, "Screensaver attached")

        // Configuración del DreamService
        isFullscreen = true
        isInteractive = false // Cualquier input lo cierra
        isScreenBright = false // Pantalla tenue para ahorrar energía (OLED friendly)

        setupUI()
        startUpdates()
        loadWeather()
        
        // Cargar primera imagen inmediatamente
        loadNextImage()
    }

    override fun onDetachedFromWindow() {
        Log.d(TAG, "Screensaver detached")
        stopUpdates()
        executor.shutdownNow()
        super.onDetachedFromWindow()
    }

    private fun setupUI() {
        // Inflar el layout
        rootLayout = LayoutInflater.from(this)
            .inflate(R.layout.screensaver_layout, null) as FrameLayout

        backgroundImage1 = rootLayout.findViewById(R.id.background_image_1)
        backgroundImage2 = rootLayout.findViewById(R.id.background_image_2)
        infoContainer = rootLayout.findViewById(R.id.info_container)
        clockText = rootLayout.findViewById(R.id.screensaver_clock)
        dateText = rootLayout.findViewById(R.id.screensaver_date)
        weatherIcon = rootLayout.findViewById(R.id.screensaver_weather_icon)
        temperatureText = rootLayout.findViewById(R.id.screensaver_temperature)

        // Iniciar con fondo negro
        backgroundImage1.setImageDrawable(ColorDrawable(Color.BLACK))
        backgroundImage2.setImageDrawable(ColorDrawable(Color.BLACK))
        backgroundImage2.alpha = 0f

        setContentView(rootLayout)
    }

    private fun startUpdates() {
        handler.post(clockUpdateRunnable)
        handler.postDelayed(imageChangeRunnable, IMAGE_CHANGE_INTERVAL)
    }

    private fun stopUpdates() {
        handler.removeCallbacks(clockUpdateRunnable)
        handler.removeCallbacks(imageChangeRunnable)
    }

    private fun updateClock() {
        val now = Date()
        clockText.text = timeFormat.format(now)
        dateText.text = dateFormat.format(now).replaceFirstChar { it.uppercase() }
    }

    private fun loadNextImage() {
        // Usar Picsum con un random para obtener imagen diferente cada vez
        // El parámetro random evita cache del servidor
        val randomSeed = random.nextInt(10000)
        val imageUrl = "$PICSUM_URL?random=$randomSeed"
        
        Log.d(TAG, "Loading image: $imageUrl")
        
        executor.execute {
            val bitmap = downloadImage(imageUrl)
            if (bitmap != null) {
                handler.post {
                    crossfadeToImage(bitmap)
                }
            }
        }
    }

    private fun downloadImage(urlString: String): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "TV-Alternativa-Launcher/1.0")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                
                // Decodificar con opciones para evitar OOM
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565 // Menos memoria
                    inSampleSize = 1 // Calidad completa para TV
                }
                
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                if (bitmap != null) {
                    Log.d(TAG, "Image downloaded successfully: ${bitmap.width}x${bitmap.height}")
                } else {
                    Log.e(TAG, "Failed to decode image bitmap")
                }
                bitmap
            } else {
                Log.e(TAG, "Image download failed: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading image", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun crossfadeToImage(bitmap: Bitmap) {
        val fadeIn: ImageView
        val fadeOut: ImageView
        
        if (currentImageView == 1) {
            fadeIn = backgroundImage2
            fadeOut = backgroundImage1
            currentImageView = 2
        } else {
            fadeIn = backgroundImage1
            fadeOut = backgroundImage2
            currentImageView = 1
        }
        
        // Configurar la nueva imagen
        fadeIn.setImageBitmap(bitmap)
        fadeIn.scaleType = ImageView.ScaleType.CENTER_CROP
        fadeIn.alpha = 0f
        
        // Animación de crossfade
        ObjectAnimator.ofFloat(fadeIn, "alpha", 0f, 1f).apply {
            duration = FADE_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        
        ObjectAnimator.ofFloat(fadeOut, "alpha", 1f, 0f).apply {
            duration = FADE_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Liberar memoria de la imagen anterior después del fade
                    val drawable = fadeOut.drawable
                    if (drawable is android.graphics.drawable.BitmapDrawable) {
                        // No reciclar el bitmap directamente, dejar que GC lo maneje
                    }
                }
            })
            start()
        }
    }

    private fun loadWeather() {
        val locationHelper = LocationHelper(applicationContext)
        
        val location = locationHelper.getLastKnownLocation()
        if (location != null) {
            fetchWeather(location.latitude, location.longitude)
        } else {
            locationHelper.requestLocationUpdate { loc ->
                if (loc != null) {
                    handler.post {
                        fetchWeather(loc.latitude, loc.longitude)
                    }
                } else {
                    Log.d(TAG, "No location available for weather")
                    hideWeather()
                }
            }
        }
    }

    private fun fetchWeather(latitude: Double, longitude: Double) {
        weatherRepository.getWeather(latitude, longitude) { weather ->
            handler.post {
                if (weather != null) {
                    val iconResId = resources.getIdentifier(
                        weather.iconRes,
                        "drawable",
                        packageName
                    )
                    
                    if (iconResId != 0) {
                        weatherIcon.setImageResource(iconResId)
                        weatherIcon.visibility = View.VISIBLE
                    } else {
                        weatherIcon.visibility = View.GONE
                    }
                    
                    temperatureText.text = weather.getTemperatureString()
                    temperatureText.visibility = View.VISIBLE
                    
                    Log.d(TAG, "Weather loaded: ${weather.getTemperatureString()}")
                } else {
                    hideWeather()
                }
            }
        }
    }

    private fun hideWeather() {
        handler.post {
            weatherIcon.visibility = View.GONE
            temperatureText.visibility = View.GONE
        }
    }
}
