package com.televisionalternativa.launcher.screensaver

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
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
 * Screensaver basado en overlay (SYSTEM_ALERT_WINDOW).
 * A diferencia de DreamService:
 * - Podemos iniciarlo sin permisos de sistema
 * - Las apps en background NO reciben onStop()
 * - SmartTube sigue reproduciendo
 */
class ScreensaverOverlayService : Service() {

    companion object {
        private const val TAG = "ScreensaverOverlay"
        const val ACTION_START = "com.televisionalternativa.launcher.SCREENSAVER_START"
        const val ACTION_STOP = "com.televisionalternativa.launcher.SCREENSAVER_STOP"
        
        private const val IMAGE_CHANGE_INTERVAL = 30_000L
        private const val CLOCK_UPDATE_INTERVAL = 1_000L
        private const val FADE_DURATION = 2000L
        private const val PICSUM_URL = "https://picsum.photos/1920/1080"
        
        @Volatile
        var isRunning = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    
    private lateinit var backgroundImage1: ImageView
    private lateinit var backgroundImage2: ImageView
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var weatherIcon: ImageView
    private lateinit var temperatureText: TextView
    
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val weatherRepository = WeatherRepository()
    private val random = Random()
    
    private var currentImageView = 1
    
    // Preloading: guardamos la siguiente imagen mientras mostramos la actual
    @Volatile
    private var nextImageBitmap: Bitmap? = null
    
    @Volatile
    private var isPreloading = false
    
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
            showNextImageAndPreload()
            handler.postDelayed(this, IMAGE_CHANGE_INTERVAL)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> showScreensaver()
            ACTION_STOP -> hideScreensaver()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        hideScreensaver()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun showScreensaver() {
        if (isRunning || overlayView != null) {
            Log.d(TAG, "Screensaver already showing")
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "No overlay permission")
            return
        }

        Log.d(TAG, "Showing screensaver overlay")

        // Inflar layout
        overlayView = LayoutInflater.from(this).inflate(R.layout.screensaver_layout, null)

        // Configurar WindowManager params
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            // Sin FLAG_NOT_FOCUSABLE para poder recibir key events
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        )
        params.gravity = Gravity.TOP or Gravity.START

        // Bind views
        backgroundImage1 = overlayView!!.findViewById(R.id.background_image_1)
        backgroundImage2 = overlayView!!.findViewById(R.id.background_image_2)
        clockText = overlayView!!.findViewById(R.id.screensaver_clock)
        dateText = overlayView!!.findViewById(R.id.screensaver_date)
        weatherIcon = overlayView!!.findViewById(R.id.screensaver_weather_icon)
        temperatureText = overlayView!!.findViewById(R.id.screensaver_temperature)

        // Inicializar
        backgroundImage1.setImageDrawable(ColorDrawable(Color.BLACK))
        backgroundImage2.setImageDrawable(ColorDrawable(Color.BLACK))
        backgroundImage2.alpha = 0f

        // Hacer que cualquier tecla cierre el screensaver
        overlayView!!.isFocusableInTouchMode = true
        overlayView!!.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                Log.d(TAG, "Key pressed, closing screensaver: $keyCode")
                hideScreensaver()
                true
            } else {
                false
            }
        }
        
        // Click también cierra
        overlayView!!.setOnClickListener {
            Log.d(TAG, "Clicked, closing screensaver")
            hideScreensaver()
        }

        try {
            windowManager?.addView(overlayView, params)
            isRunning = true
            
            // Pedir focus para recibir key events
            overlayView!!.requestFocus()
            
            // Iniciar updates
            handler.post(clockUpdateRunnable)
            loadWeather()
            
            // Cargar primera imagen inmediatamente Y precargar la siguiente
            loadFirstImageAndStartCycle()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing screensaver", e)
            overlayView = null
        }
    }

    private fun hideScreensaver() {
        Log.d(TAG, "Hiding screensaver overlay")
        
        handler.removeCallbacks(clockUpdateRunnable)
        handler.removeCallbacks(imageChangeRunnable)
        
        // Limpiar bitmap precargado
        nextImageBitmap?.recycle()
        nextImageBitmap = null
        
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay", e)
            }
        }
        overlayView = null
        isRunning = false
        
        stopSelf()
    }

    private fun updateClock() {
        val now = Date()
        clockText.text = timeFormat.format(now)
        dateText.text = dateFormat.format(now).replaceFirstChar { it.uppercase() }
    }

    /**
     * Carga la primera imagen al iniciar y arranca el ciclo.
     */
    private fun loadFirstImageAndStartCycle() {
        Log.d(TAG, "Loading first image...")
        
        executor.execute {
            val bitmap = downloadImage(getRandomImageUrl())
            if (bitmap != null) {
                handler.post {
                    // Mostrar primera imagen directamente (sin crossfade)
                    backgroundImage1.setImageBitmap(bitmap)
                    backgroundImage1.scaleType = ImageView.ScaleType.CENTER_CROP
                    backgroundImage1.alpha = 1f
                    currentImageView = 1
                    
                    // Ahora precargar la siguiente
                    preloadNextImage()
                    
                    // Iniciar el ciclo de cambio de imágenes
                    handler.postDelayed(imageChangeRunnable, IMAGE_CHANGE_INTERVAL)
                }
            } else {
                // Reintentar después de un rato
                handler.postDelayed({ loadFirstImageAndStartCycle() }, 5000)
            }
        }
    }

    /**
     * Precarga la siguiente imagen en background.
     */
    private fun preloadNextImage() {
        if (isPreloading) return
        
        isPreloading = true
        Log.d(TAG, "Preloading next image...")
        
        executor.execute {
            val bitmap = downloadImage(getRandomImageUrl())
            nextImageBitmap = bitmap
            isPreloading = false
            
            if (bitmap != null) {
                Log.d(TAG, "Next image preloaded and ready")
            } else {
                Log.w(TAG, "Failed to preload next image, will retry on show")
            }
        }
    }

    /**
     * Muestra la imagen precargada y empieza a precargar la siguiente.
     */
    private fun showNextImageAndPreload() {
        val bitmapToShow = nextImageBitmap
        
        if (bitmapToShow != null) {
            // Tenemos imagen precargada, mostrarla con crossfade
            Log.d(TAG, "Showing preloaded image")
            nextImageBitmap = null
            crossfadeToImage(bitmapToShow)
            
            // Precargar la siguiente mientras se muestra esta
            preloadNextImage()
        } else {
            // No tenemos imagen precargada, cargar una ahora
            Log.w(TAG, "No preloaded image, loading now (will cause delay)")
            executor.execute {
                val bitmap = downloadImage(getRandomImageUrl())
                if (bitmap != null) {
                    handler.post {
                        crossfadeToImage(bitmap)
                        preloadNextImage()
                    }
                }
            }
        }
    }

    private fun getRandomImageUrl(): String {
        val randomSeed = random.nextInt(10000)
        return "$PICSUM_URL?random=$randomSeed"
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
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                if (bitmap != null) {
                    Log.d(TAG, "Image downloaded: ${bitmap.width}x${bitmap.height}")
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
        val newImageView: ImageView
        val oldImageView: ImageView
        
        if (currentImageView == 1) {
            newImageView = backgroundImage2
            oldImageView = backgroundImage1
            currentImageView = 2
        } else {
            newImageView = backgroundImage1
            oldImageView = backgroundImage2
            currentImageView = 1
        }
        
        // 1. Preparar la nueva imagen (visible, debajo)
        newImageView.visibility = View.VISIBLE
        newImageView.alpha = 1f
        newImageView.setImageBitmap(bitmap)
        newImageView.scaleType = ImageView.ScaleType.CENTER_CROP
        
        // 2. Asegurar que la vieja está arriba
        oldImageView.bringToFront()
        oldImageView.alpha = 1f
        
        // 3. FadeOut de la vieja (revela la nueva debajo)
        oldImageView.animate()
            .alpha(0f)
            .setDuration(FADE_DURATION)
            .withEndAction {
                oldImageView.visibility = View.GONE
                oldImageView.clearAnimation()
            }
            .start()
        
        Log.d(TAG, "Crossfade to ImageView$currentImageView")
    }

    private fun loadWeather() {
        val locationHelper = LocationHelper(applicationContext)
        val location = locationHelper.getLastKnownLocation()
        
        if (location != null) {
            fetchWeather(location.latitude, location.longitude)
        } else {
            locationHelper.requestLocationUpdate { loc ->
                if (loc != null) {
                    handler.post { fetchWeather(loc.latitude, loc.longitude) }
                }
            }
        }
    }

    private fun fetchWeather(latitude: Double, longitude: Double) {
        weatherRepository.getWeather(latitude, longitude) { weather ->
            handler.post {
                if (weather != null) {
                    val iconResId = resources.getIdentifier(
                        weather.iconRes, "drawable", packageName
                    )
                    if (iconResId != 0) {
                        weatherIcon.setImageResource(iconResId)
                        weatherIcon.visibility = View.VISIBLE
                    }
                    temperatureText.text = weather.getTemperatureString()
                    temperatureText.visibility = View.VISIBLE
                }
            }
        }
    }
}
