package com.televisionalternativa.launcher

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.televisionalternativa.launcher.update.AboutLauncherDialogFragment

/**
 * Panel lateral de configuración rápida.
 * Se abre con el botón Settings del control remoto.
 */
class SettingsPanelFragment : DialogFragment() {

    private lateinit var optionWifi: LinearLayout
    private lateinit var optionAbout: LinearLayout
    private lateinit var wifiIcon: ImageView
    private lateinit var wifiStatusText: TextView
    private lateinit var versionText: TextView

    companion object {
        const val TAG = "SettingsPanelFragment"
        
        private const val ARG_VERSION = "version"
        private const val ARG_GITHUB_OWNER = "github_owner"
        private const val ARG_GITHUB_REPO = "github_repo"

        fun newInstance(
            currentVersion: String,
            githubOwner: String,
            githubRepo: String
        ): SettingsPanelFragment {
            return SettingsPanelFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_VERSION, currentVersion)
                    putString(ARG_GITHUB_OWNER, githubOwner)
                    putString(ARG_GITHUB_REPO, githubRepo)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.Theme_Launcher_SettingsPanel)
        isCancelable = true // BACK cierra el dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings_panel, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind views
        optionWifi = view.findViewById(R.id.option_wifi)
        optionAbout = view.findViewById(R.id.option_about)
        wifiIcon = view.findViewById(R.id.wifi_icon)
        wifiStatusText = view.findViewById(R.id.wifi_status_text)
        versionText = view.findViewById(R.id.version_text)

        // Mostrar versión
        versionText.text = arguments?.getString(ARG_VERSION) ?: "v1.0.0"

        // Actualizar estado WiFi
        updateWifiStatus()

        // Click listeners
        optionWifi.setOnClickListener {
            openSettings(Settings.ACTION_WIFI_SETTINGS)
        }

        optionAbout.setOnClickListener {
            showAboutDialog()
        }

        // Click fuera del panel cierra
        view.setOnClickListener {
            dismiss()
        }

        // El panel en sí no cierra al clickear
        view.findViewById<LinearLayout>(R.id.settings_panel).setOnClickListener { 
            // No hacer nada, evita que se propague al parent
        }

        // Focus inicial en WiFi después de un pequeño delay para que el layout esté listo
        optionWifi.post {
            optionWifi.requestFocus()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Asegurar focus en WiFi cuando el dialog esté visible
        optionWifi.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        // Hacer el dialog fullscreen
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    private fun updateWifiStatus() {
        val connectivityManager = requireContext().getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(network)
        
        val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        
        if (isConnected) {
            wifiIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary))
            wifiStatusText.text = "Conectado"
            wifiStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_green))
        } else {
            wifiIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            wifiStatusText.text = "Sin conexión"
            wifiStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        }
    }

    private fun openSettings(action: String) {
        try {
            val intent = Intent(action)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            dismiss()
        } catch (e: Exception) {
            // Fallback a settings general
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                dismiss()
            } catch (e2: Exception) {
                // No hay settings disponibles
            }
        }
    }

    private fun showAboutDialog() {
        dismiss()
        
        val aboutDialog = AboutLauncherDialogFragment.newInstance(
            currentVersion = arguments?.getString(ARG_VERSION) ?: "1.0.0",
            githubOwner = arguments?.getString(ARG_GITHUB_OWNER) ?: "",
            githubRepo = arguments?.getString(ARG_GITHUB_REPO) ?: ""
        )
        aboutDialog.show(parentFragmentManager, "about_launcher_dialog")
    }
}
