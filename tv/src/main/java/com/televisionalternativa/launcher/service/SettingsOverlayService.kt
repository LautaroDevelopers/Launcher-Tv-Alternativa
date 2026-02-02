package com.televisionalternativa.launcher.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.televisionalternativa.launcher.R

/**
 * Service que muestra el panel de configuración como overlay
 * sobre cualquier aplicación usando SYSTEM_ALERT_WINDOW.
 */
class SettingsOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isShowing = false

    companion object {
        private const val TAG = "SettingsOverlayService"
        const val ACTION_SHOW = "com.televisionalternativa.launcher.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE = "com.televisionalternativa.launcher.ACTION_HIDE_OVERLAY"
        const val ACTION_TOGGLE = "com.televisionalternativa.launcher.ACTION_TOGGLE_OVERLAY"
        
        // Estado global para que el AccessibilityService sepa si cerrar con BACK
        @Volatile
        var isOverlayVisible = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
            ACTION_TOGGLE -> {
                if (isShowing) hideOverlay() else showOverlay()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
    }

    private fun showOverlay() {
        if (isShowing || overlayView != null) {
            Log.d(TAG, "Overlay already showing")
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "No permission to draw overlays")
            return
        }

        Log.d(TAG, "Showing settings overlay")

        // Inflar el layout
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_settings_panel, null)

        // Configurar WindowManager.LayoutParams
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
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        // Setup views
        setupViews()

        // Agregar al WindowManager
        try {
            windowManager?.addView(overlayView, params)
            isShowing = true
            isOverlayVisible = true  // Estado global
            
            // Focus en WiFi
            overlayView?.findViewById<LinearLayout>(R.id.option_wifi)?.requestFocus()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view", e)
            overlayView = null
        }
    }

    private fun setupViews() {
        val view = overlayView ?: return

        // Background click cierra
        view.setOnClickListener {
            hideOverlay()
        }

        // Panel no cierra al click
        view.findViewById<LinearLayout>(R.id.settings_panel)?.setOnClickListener {
            // No propagar
        }

        // Actualizar estado WiFi
        val wifiIcon = view.findViewById<ImageView>(R.id.wifi_icon)
        val wifiStatusText = view.findViewById<TextView>(R.id.wifi_status_text)
        updateWifiStatus(wifiIcon, wifiStatusText)

        // Versión
        val versionText = view.findViewById<TextView>(R.id.version_text)
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            versionText?.text = "v${pInfo.versionName ?: "1.0.0"}"
        } catch (e: Exception) {
            versionText?.text = "v1.0.0"
        }

        // Click listeners
        view.findViewById<LinearLayout>(R.id.option_wifi)?.setOnClickListener {
            openSettings(Settings.ACTION_WIFI_SETTINGS)
        }

        view.findViewById<LinearLayout>(R.id.option_about)?.setOnClickListener {
            // Abrir el launcher con el about dialog
            val intent = Intent(this, com.televisionalternativa.launcher.MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("open_about", true)
            startActivity(intent)
            hideOverlay()
        }

        // Manejar tecla BACK
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                hideOverlay()
                true
            } else {
                false
            }
        }
    }

    private fun updateWifiStatus(wifiIcon: ImageView?, wifiStatusText: TextView?) {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(network)
        val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (isConnected) {
            wifiIcon?.setColorFilter(ContextCompat.getColor(this, R.color.primary))
            wifiStatusText?.text = "Conectado"
            wifiStatusText?.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
        } else {
            wifiIcon?.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
            wifiStatusText?.text = "Sin conexión"
            wifiStatusText?.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun openSettings(action: String) {
        try {
            val intent = Intent(action)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            hideOverlay()
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                hideOverlay()
            } catch (e2: Exception) {
                Log.e(TAG, "Could not open settings", e2)
            }
        }
    }

    private fun hideOverlay() {
        Log.d(TAG, "Hiding settings overlay")
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view", e)
            }
        }
        overlayView = null
        isShowing = false
        isOverlayVisible = false  // Estado global
    }
}
