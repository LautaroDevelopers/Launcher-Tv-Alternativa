package com.televisionalternativa.launcher.update

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.televisionalternativa.launcher.R

/**
 * Panel de información del Launcher.
 * Muestra versión actual, info del dispositivo y permite buscar actualizaciones.
 */
class AboutLauncherDialogFragment : DialogFragment() {
    
    private lateinit var updateChecker: UpdateChecker
    private lateinit var updateRepository: UpdateRepository
    
    private lateinit var currentVersionText: TextView
    private lateinit var deviceModelText: TextView
    private lateinit var androidVersionText: TextView
    private lateinit var updateStatusContainer: LinearLayout
    private lateinit var updateStatusIcon: ImageView
    private lateinit var updateStatusText: TextView
    private lateinit var btnCheckUpdates: Button
    private lateinit var btnClose: TextView
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isChecking = false
    private var pendingUpdateInfo: UpdateInfo? = null
    
    companion object {
        private const val TAG = "AboutLauncherDialog"
        private const val ARG_CURRENT_VERSION = "current_version"
        private const val ARG_GITHUB_OWNER = "github_owner"
        private const val ARG_GITHUB_REPO = "github_repo"

        fun newInstance(
            currentVersion: String,
            githubOwner: String,
            githubRepo: String
        ): AboutLauncherDialogFragment {
            return AboutLauncherDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CURRENT_VERSION, currentVersion)
                    putString(ARG_GITHUB_OWNER, githubOwner)
                    putString(ARG_GITHUB_REPO, githubRepo)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Translucent_NoTitleBar)
        
        val githubOwner = arguments?.getString(ARG_GITHUB_OWNER) ?: ""
        val githubRepo = arguments?.getString(ARG_GITHUB_REPO) ?: ""
        
        updateChecker = UpdateChecker(requireContext(), githubOwner, githubRepo)
        updateRepository = UpdateRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_about_launcher, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Bind views
        currentVersionText = view.findViewById(R.id.current_version)
        deviceModelText = view.findViewById(R.id.device_model)
        androidVersionText = view.findViewById(R.id.android_version)
        updateStatusContainer = view.findViewById(R.id.update_status_container)
        updateStatusIcon = view.findViewById(R.id.update_status_icon)
        updateStatusText = view.findViewById(R.id.update_status_text)
        btnCheckUpdates = view.findViewById(R.id.btn_check_updates)
        btnClose = view.findViewById(R.id.btn_close)
        
        // Setear datos
        val currentVersion = arguments?.getString(ARG_CURRENT_VERSION) ?: "1.0.0"
        currentVersionText.text = currentVersion
        deviceModelText.text = "${Build.MANUFACTURER} ${Build.MODEL}"
        androidVersionText.text = "Android ${Build.VERSION.RELEASE}"
        
        // Estado inicial - verificar si hay update cacheado
        val cachedUpdate = updateRepository.getCachedUpdateInfo()
        if (cachedUpdate != null) {
            showUpdateAvailable(cachedUpdate)
        } else {
            showUpToDate()
        }
        
        // Click listeners
        btnCheckUpdates.setOnClickListener { checkForUpdates() }
        btnClose.setOnClickListener { dismiss() }
        
        // Focus inicial
        btnCheckUpdates.requestFocus()
    }

    private fun checkForUpdates() {
        if (isChecking) return
        isChecking = true
        
        Log.d(TAG, "Checking for updates...")
        
        btnCheckUpdates.isEnabled = false
        btnCheckUpdates.text = "BUSCANDO..."
        showChecking()
        
        updateChecker.checkForUpdate { result ->
            mainHandler.post {
                isChecking = false
                btnCheckUpdates.isEnabled = true
                btnCheckUpdates.text = "BUSCAR ACTUALIZACIONES"
                
                when (result) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        Log.d(TAG, "Update available: ${result.updateInfo.versionName}")
                        updateRepository.cacheUpdateInfo(result.updateInfo)
                        showUpdateAvailable(result.updateInfo)
                    }
                    is UpdateCheckResult.NoUpdateAvailable -> {
                        Log.d(TAG, "No update available")
                        updateRepository.clearCachedUpdateInfo()
                        showUpToDate()
                    }
                    is UpdateCheckResult.Error -> {
                        Log.e(TAG, "Update check failed: ${result.message}")
                        showError(result.message)
                    }
                }
            }
        }
    }

    private fun showChecking() {
        updateStatusContainer.setBackgroundColor(Color.parseColor("#1AFFFFFF"))
        updateStatusIcon.setImageResource(R.drawable.ic_update)
        updateStatusIcon.setColorFilter(Color.parseColor("#AAAAAA"))
        updateStatusText.text = "Buscando actualizaciones..."
        updateStatusText.setTextColor(Color.parseColor("#AAAAAA"))
    }

    private fun showUpToDate() {
        pendingUpdateInfo = null
        updateStatusContainer.setBackgroundColor(Color.parseColor("#1A4CAF50"))
        updateStatusIcon.setImageResource(R.drawable.ic_check)
        updateStatusIcon.clearColorFilter()
        updateStatusText.text = "Estás usando la última versión"
        updateStatusText.setTextColor(Color.WHITE)
        
        btnCheckUpdates.text = "BUSCAR ACTUALIZACIONES"
    }

    private fun showUpdateAvailable(updateInfo: UpdateInfo) {
        pendingUpdateInfo = updateInfo
        updateStatusContainer.setBackgroundColor(Color.parseColor("#1AFFEB3B"))
        updateStatusIcon.setImageResource(R.drawable.ic_update)
        updateStatusIcon.setColorFilter(Color.parseColor("#FFEB3B"))
        updateStatusText.text = "Nueva versión disponible: ${updateInfo.versionName}"
        updateStatusText.setTextColor(Color.WHITE)
        
        btnCheckUpdates.text = "DESCARGAR ACTUALIZACIÓN"
        btnCheckUpdates.setOnClickListener { 
            // Cerrar este dialog y abrir el de descarga
            dismiss()
            showUpdateDialog(updateInfo)
        }
    }

    private fun showError(message: String) {
        updateStatusContainer.setBackgroundColor(Color.parseColor("#1AF44336"))
        updateStatusIcon.setImageResource(R.drawable.ic_wifi_off)
        updateStatusIcon.setColorFilter(Color.parseColor("#F44336"))
        updateStatusText.text = "Error: $message"
        updateStatusText.setTextColor(Color.parseColor("#F44336"))
    }

    private fun showUpdateDialog(updateInfo: UpdateInfo) {
        val currentVersion = arguments?.getString(ARG_CURRENT_VERSION) ?: "1.0.0"
        val dialog = UpdateDialogFragment.newInstance(updateInfo, currentVersion)
        parentFragmentManager.let { fm ->
            dialog.show(fm, "update_dialog")
        }
    }
}
