package com.televisionalternativa.launcher

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.televisionalternativa.launcher.data.AppScanner
import com.televisionalternativa.launcher.update.AboutLauncherDialogFragment
import com.televisionalternativa.launcher.update.UpdateCheckResult
import com.televisionalternativa.launcher.update.UpdateChecker
import com.televisionalternativa.launcher.update.UpdateDialogFragment
import com.televisionalternativa.launcher.update.UpdateRepository

/** Loads [MainFragment]. */
class MainActivity : FragmentActivity() {

  private lateinit var wifiButton: LinearLayout
  private lateinit var wifiIcon: ImageView
  private lateinit var wifiText: TextView
  private lateinit var settingsButton: LinearLayout
  private lateinit var systemInfoChip: LinearLayout
  private lateinit var versionTextView: TextView

  private lateinit var connectivityManager: ConnectivityManager

  // Sistema de actualizaciones
  private lateinit var updateChecker: UpdateChecker
  private lateinit var updateRepository: UpdateRepository
  private var currentVersionName: String = "1.0.0"

  /**
   * NetworkCallback moderno (API 21+) - reemplaza el BroadcastReceiver deprecado. Recibe
   * notificaciones cuando cambia el estado de la red.
   */
  private val networkCallback =
          object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
              Log.d(TAG, "Network available: $network")
              runOnUiThread { updateNetworkStatus(isConnected = true) }
            }

            override fun onLost(network: Network) {
              Log.d(TAG, "Network lost: $network")
              runOnUiThread { updateNetworkStatus(isConnected = false) }
            }

            override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities
            ) {
              val hasInternet =
                      capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
              Log.d(TAG, "Network capabilities changed - hasInternet: $hasInternet")
              runOnUiThread { updateNetworkStatus(isConnected = hasInternet) }
            }
          }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate - Starting launcher")
    setContentView(R.layout.activity_main)

    connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Inicializar sistema de actualizaciones
    // TODO: Reemplazar con tu usuario/repo de GitHub
    updateChecker = UpdateChecker(this, GITHUB_OWNER, GITHUB_REPO)
    updateRepository = UpdateRepository(this)

    // Bindear views del header
    wifiButton = findViewById(R.id.wifi_status_icon)
    wifiIcon = findViewById(R.id.wifi_icon)
    wifiText = findViewById(R.id.wifi_text)
    settingsButton = findViewById(R.id.settings_button)
    systemInfoChip = findViewById(R.id.system_info_chip)
    versionTextView = findViewById(R.id.version_text)

    // Mostrar versión
    try {
      val pInfo = packageManager.getPackageInfo(packageName, 0)
      currentVersionName = pInfo.versionName ?: "1.0.0"
      versionTextView.text = "v$currentVersionName"
    } catch (e: Exception) {
      systemInfoChip.visibility = View.GONE
    }

    // Listener para el chip de información del sistema
    systemInfoChip.setOnClickListener {
      Log.d(TAG, "System Info Chip Clicked - Opening About Launcher dialog")
      showAboutLauncherDialog()
    }

    // Botón WiFi → abre configuración de redes
    wifiButton.setOnClickListener {
      try {
        val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
      } catch (e: Exception) {
        Log.e(TAG, "Could not open wifi settings", e)
        try {
          startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        } catch (e2: Exception) {
          Log.e(TAG, "Could not open any settings", e2)
        }
      }
    }

    // Botón Ajustes → abre configuración general del sistema
    settingsButton.setOnClickListener {
      try {
        val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
      } catch (e: Exception) {
        Log.e(TAG, "Could not open settings", e)
      }
    }

    if (savedInstanceState == null) {
      val apps = AppScanner.getTvApps(this)
      Log.d(TAG, "Found ${apps.size} apps to display")
      val fragment = MainFragment.newInstance(apps)
      supportFragmentManager.beginTransaction().replace(R.id.rows_container, fragment).commitNow()
    }
  }

  override fun onResume() {
    super.onResume()

    // Registrar NetworkCallback para escuchar cambios de conectividad
    val networkRequest =
            NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
    connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

    // Actualizar estado inicial
    updateNetworkStatus(isConnected = isNetworkAvailable())

    // Chequear actualizaciones
    checkForUpdates()
  }

  override fun onPause() {
    super.onPause()
    // Desregistrar para evitar leaks de memoria
    connectivityManager.unregisterNetworkCallback(networkCallback)
  }

  /** Verifica si hay conexión a internet usando la API moderna. */
  private fun isNetworkAvailable(): Boolean {
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  /** Actualiza el icono y texto de WiFi según el estado de conexión. */
  private fun updateNetworkStatus(isConnected: Boolean) {
    if (isConnected) {
      Log.d(TAG, "Network is Connected")
      wifiIcon.setImageResource(R.drawable.ic_wifi)
      wifiIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary))
      wifiText.text = "WiFi"
      wifiText.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
    } else {
      Log.d(TAG, "Network is Disconnected")
      wifiIcon.setImageResource(R.drawable.ic_wifi_off)
      wifiIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
      wifiText.text = "Sin WiFi"
      wifiText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
    }
  }

  /**
   * Chequea si hay actualizaciones disponibles. Solo chequea si:
   * - Hay conexión a internet
   * - No está en snooze (el usuario dijo "recordarme después")
   * - Pasó el cooldown desde el último chequeo
   */
  private fun checkForUpdates() {
    // Sin internet no chequeamos
    if (!isNetworkAvailable()) {
      Log.d(TAG, "No network, skipping update check")
      return
    }

    // Si el usuario pidió snooze, respetamos
    if (updateRepository.isUpdateSnoozed()) {
      Log.d(TAG, "Update is snoozed, skipping check")
      return
    }

    // Si ya hay un dialog mostrándose, no abrimos otro
    if (supportFragmentManager.findFragmentByTag(UPDATE_DIALOG_TAG) != null) {
      Log.d(TAG, "Update dialog already showing")
      return
    }

    // Chequear si tenemos update cacheado (del chequeo anterior)
    val cachedUpdate = updateRepository.getCachedUpdateInfo()
    if (cachedUpdate != null) {
      Log.d(TAG, "Showing cached update: ${cachedUpdate.versionName}")
      showUpdateDialog(cachedUpdate)
      return
    }

    // Respetar cooldown para no abusar de la API
    if (!updateRepository.shouldCheckForUpdates()) {
      Log.d(TAG, "Cooldown active, skipping API call")
      return
    }

    Log.d(TAG, "Checking for updates...")

    updateChecker.checkForUpdate { result ->
      runOnUiThread {
        updateRepository.recordCheckTime()

        when (result) {
          is UpdateCheckResult.UpdateAvailable -> {
            Log.d(TAG, "Update available: ${result.updateInfo.versionName}")
            updateRepository.cacheUpdateInfo(result.updateInfo)
            showUpdateDialog(result.updateInfo)
          }
          is UpdateCheckResult.NoUpdateAvailable -> {
            Log.d(TAG, "No update available, app is up to date")
            updateRepository.clearCachedUpdateInfo()
          }
          is UpdateCheckResult.Error -> {
            Log.e(TAG, "Update check failed: ${result.message}", result.exception)
            // No mostramos error al usuario, simplemente no chequeamos
          }
        }
      }
    }
  }

  /** Muestra el dialog de actualización. */
  private fun showUpdateDialog(updateInfo: com.televisionalternativa.launcher.update.UpdateInfo) {
    val dialog = UpdateDialogFragment.newInstance(updateInfo, currentVersionName)
    dialog.show(supportFragmentManager, UPDATE_DIALOG_TAG)
  }
  
  /** Muestra el panel "Sobre el Launcher". */
  private fun showAboutLauncherDialog() {
    // Si ya hay un dialog abierto, no abrimos otro
    if (supportFragmentManager.findFragmentByTag(ABOUT_DIALOG_TAG) != null) {
      return
    }
    
    val dialog = AboutLauncherDialogFragment.newInstance(
      currentVersion = currentVersionName,
      githubOwner = GITHUB_OWNER,
      githubRepo = GITHUB_REPO
    )
    dialog.show(supportFragmentManager, ABOUT_DIALOG_TAG)
  }

  companion object {
    private const val TAG = "MainActivity"
    private const val UPDATE_DIALOG_TAG = "update_dialog"
    private const val ABOUT_DIALOG_TAG = "about_launcher_dialog"

    // Repositorio de GitHub para actualizaciones
    private const val GITHUB_OWNER = "LautaroDevelopers"
    private const val GITHUB_REPO = "Launcher-Tv-Alternativa"
  }
}
