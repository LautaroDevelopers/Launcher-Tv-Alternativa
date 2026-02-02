package com.televisionalternativa.launcher

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.televisionalternativa.launcher.data.AppScanner
import com.televisionalternativa.launcher.permissions.PermissionsDialogFragment
import com.televisionalternativa.launcher.update.AboutLauncherDialogFragment
import com.televisionalternativa.launcher.update.UpdateCheckResult
import com.televisionalternativa.launcher.update.UpdateChecker
import com.televisionalternativa.launcher.update.UpdateDialogFragment
import com.televisionalternativa.launcher.update.UpdateRepository

/** Loads [MainFragment]. */
class MainActivity : FragmentActivity() {

  private lateinit var wifiStatusIcon: ImageView
  private lateinit var settingsButton: ImageView
  private lateinit var systemInfoChip: LinearLayout
  private lateinit var versionTextView: TextView

  private lateinit var connectivityManager: ConnectivityManager

  // Sistema de actualizaciones
  private lateinit var updateChecker: UpdateChecker
  private lateinit var updateRepository: UpdateRepository
  private var currentVersionName: String = "1.0.0"

  /**
   * NetworkCallback moderno (API 21+) - reemplaza el BroadcastReceiver deprecado.
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
    updateChecker = UpdateChecker(this, GITHUB_OWNER, GITHUB_REPO)
    updateRepository = UpdateRepository(this)

    // Bindear views del header
    wifiStatusIcon = findViewById(R.id.wifi_status_icon)
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

    // Listener para el chip de versión → abre About Launcher
    systemInfoChip.setOnClickListener {
      Log.d(TAG, "System Info Chip Clicked - Opening About Launcher")
      showAboutLauncherDialog()
    }

    // Listener para el botón de settings → abre panel lateral
    settingsButton.setOnClickListener {
      Log.d(TAG, "Settings Button Clicked - Opening Settings Panel")
      showSettingsPanel()
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
    
    // Debug: ver si llegamos desde un intent especial
    Log.d(TAG, "onResume - intent action: ${intent?.action}, extras: ${intent?.extras}")

    // Registrar NetworkCallback
    val networkRequest =
            NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
    connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

    // Actualizar estado inicial
    updateNetworkStatus(isConnected = isNetworkAvailable())

    // Chequear si venimos del overlay con acción específica
    handleOverlayIntent()

    // Chequear permisos necesarios para el overlay global
    checkAndRequestPermissions()

    // Chequear actualizaciones
    checkForUpdates()
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    setIntent(intent)
    Log.d(TAG, "onNewIntent - action: ${intent?.action}, extras: ${intent?.extras}")
  }

  /**
   * Maneja intents que vienen del SettingsOverlayService.
   */
  private fun handleOverlayIntent() {
    val openAbout = intent?.getBooleanExtra(EXTRA_OPEN_ABOUT, false) ?: false
    if (openAbout) {
      Log.d(TAG, "Opening About Launcher from overlay intent")
      // Limpiar el extra para que no se vuelva a abrir
      intent?.removeExtra(EXTRA_OPEN_ABOUT)
      showAboutLauncherDialog()
    }
  }

  /**
   * Chequea si tiene los permisos necesarios para el overlay global.
   * Si no los tiene, muestra el modal obligatorio.
   */
  private fun checkAndRequestPermissions() {
    val hasOverlayPermission = Settings.canDrawOverlays(this)
    val hasAccessibilityPermission = isAccessibilityServiceEnabled()

    Log.d(TAG, "Permissions check - Overlay: $hasOverlayPermission, Accessibility: $hasAccessibilityPermission")

    if (!hasOverlayPermission || !hasAccessibilityPermission) {
      // Solo mostrar si no está ya visible
      if (supportFragmentManager.findFragmentByTag(PERMISSIONS_DIALOG_TAG) == null) {
        Log.d(TAG, "Showing permissions dialog")
        val dialog = PermissionsDialogFragment.newInstance()
        dialog.show(supportFragmentManager, PERMISSIONS_DIALOG_TAG)
      }
    }
  }

  /**
   * Verifica si el AccessibilityService está habilitado.
   */
  private fun isAccessibilityServiceEnabled(): Boolean {
    val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    
    val myServiceName = "${packageName}/.service.GlobalKeyService"
    val myServiceNameAlt = "${packageName}/com.televisionalternativa.launcher.service.GlobalKeyService"
    
    for (service in enabledServices) {
      val serviceId = service.id
      if (serviceId == myServiceName || serviceId == myServiceNameAlt || serviceId.contains("GlobalKeyService")) {
        Log.d(TAG, "AccessibilityService is enabled: $serviceId")
        return true
      }
    }
    
    Log.d(TAG, "AccessibilityService is NOT enabled")
    return false
  }

  override fun onPause() {
    super.onPause()
    connectivityManager.unregisterNetworkCallback(networkCallback)
  }

  /**
   * Intercepta TODAS las teclas antes de que lleguen a los views.
   */
  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.action == KeyEvent.ACTION_DOWN) {
      Log.d(TAG, "dispatchKeyEvent: keyCode=${event.keyCode}, scanCode=${event.scanCode}")
      
      // Botón TV_INPUT (178) abre el panel
      if (event.keyCode == KeyEvent.KEYCODE_TV_INPUT) {
        showSettingsPanel()
        return true
      }
    }
    return super.dispatchKeyEvent(event)
  }

  /**
   * Backup: algunos eventos llegan acá.
   */
  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    Log.d(TAG, "onKeyDown: keyCode=$keyCode")
    if (keyCode == KeyEvent.KEYCODE_TV_INPUT) {
      showSettingsPanel()
      return true
    }
    return super.onKeyDown(keyCode, event)
  }

  /** Verifica si hay conexión a internet. */
  private fun isNetworkAvailable(): Boolean {
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  /** Actualiza el icono de WiFi según el estado de conexión. */
  private fun updateNetworkStatus(isConnected: Boolean) {
    if (isConnected) {
      Log.d(TAG, "Network is Connected")
      wifiStatusIcon.setImageResource(R.drawable.ic_wifi)
      wifiStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary))
    } else {
      Log.d(TAG, "Network is Disconnected")
      wifiStatusIcon.setImageResource(R.drawable.ic_wifi_off)
      wifiStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
    }
  }

  /** Muestra el panel lateral de configuración. */
  private fun showSettingsPanel() {
    // Si ya está abierto, no abrir otro
    if (supportFragmentManager.findFragmentByTag(SETTINGS_PANEL_TAG) != null) {
      return
    }

    val panel = SettingsPanelFragment.newInstance(
      currentVersion = currentVersionName,
      githubOwner = GITHUB_OWNER,
      githubRepo = GITHUB_REPO
    )
    panel.show(supportFragmentManager, SETTINGS_PANEL_TAG)
  }

  /**
   * Chequea si hay actualizaciones disponibles.
   */
  private fun checkForUpdates() {
    if (!isNetworkAvailable()) {
      Log.d(TAG, "No network, skipping update check")
      return
    }

    if (updateRepository.isUpdateSnoozed()) {
      Log.d(TAG, "Update is snoozed, skipping check")
      return
    }

    if (supportFragmentManager.findFragmentByTag(UPDATE_DIALOG_TAG) != null) {
      Log.d(TAG, "Update dialog already showing")
      return
    }

    val cachedUpdate = updateRepository.getCachedUpdateInfo()
    if (cachedUpdate != null) {
      Log.d(TAG, "Showing cached update: ${cachedUpdate.versionName}")
      showUpdateDialog(cachedUpdate)
      return
    }

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
    private const val SETTINGS_PANEL_TAG = "settings_panel"
    private const val ABOUT_DIALOG_TAG = "about_launcher_dialog"
    private const val PERMISSIONS_DIALOG_TAG = "permissions_dialog"

    // Extra para abrir About desde el overlay
    const val EXTRA_OPEN_ABOUT = "open_about"

    // Repositorio de GitHub para actualizaciones
    private const val GITHUB_OWNER = "LautaroDevelopers"
    private const val GITHUB_REPO = "Launcher-Tv-Alternativa"
  }
}
