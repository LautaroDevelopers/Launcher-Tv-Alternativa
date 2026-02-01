package com.televisionalternativa.launcher.update

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.televisionalternativa.launcher.R

/**
 * Dialog modal para mostrar actualización disponible.
 * 
 * Características:
 * - No se puede cerrar con BACK (el usuario DEBE elegir una opción)
 * - Botón principal: Actualizar ahora
 * - Botón secundario: Recordarme en 1 hora (snooze)
 * - Muestra progreso durante la descarga
 */
class UpdateDialogFragment : DialogFragment() {
    
    private lateinit var updateInfo: UpdateInfo
    private lateinit var downloader: UpdateDownloader
    private lateinit var repository: UpdateRepository
    
    private lateinit var btnUpdate: Button
    private lateinit var btnSnooze: TextView
    private lateinit var currentVersionText: TextView
    private lateinit var newVersionText: TextView
    private lateinit var releaseNotesText: TextView
    private lateinit var progressContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var downloadStatus: TextView
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isDownloading = false
    
    companion object {
        private const val TAG = "UpdateDialogFragment"
        private const val ARG_VERSION_NAME = "version_name"
        private const val ARG_TAG_NAME = "tag_name"
        private const val ARG_RELEASE_NOTES = "release_notes"
        private const val ARG_APK_URL = "apk_url"
        private const val ARG_APK_SIZE = "apk_size"
        private const val ARG_CURRENT_VERSION = "current_version"

        fun newInstance(updateInfo: UpdateInfo, currentVersion: String): UpdateDialogFragment {
            return UpdateDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_VERSION_NAME, updateInfo.versionName)
                    putString(ARG_TAG_NAME, updateInfo.tagName)
                    putString(ARG_RELEASE_NOTES, updateInfo.releaseNotes)
                    putString(ARG_APK_URL, updateInfo.apkDownloadUrl)
                    putLong(ARG_APK_SIZE, updateInfo.apkSizeBytes)
                    putString(ARG_CURRENT_VERSION, currentVersion)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Estilo fullscreen sin bordes
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Translucent_NoTitleBar)
        
        // Parsear argumentos
        arguments?.let { args ->
            updateInfo = UpdateInfo(
                versionName = args.getString(ARG_VERSION_NAME, ""),
                tagName = args.getString(ARG_TAG_NAME, ""),
                releaseNotes = args.getString(ARG_RELEASE_NOTES, ""),
                apkDownloadUrl = args.getString(ARG_APK_URL, ""),
                apkSizeBytes = args.getLong(ARG_APK_SIZE, 0)
            )
        }
        
        downloader = UpdateDownloader(requireContext())
        repository = UpdateRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_update, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Bind views
        btnUpdate = view.findViewById(R.id.btn_update)
        btnSnooze = view.findViewById(R.id.btn_snooze)
        currentVersionText = view.findViewById(R.id.current_version)
        newVersionText = view.findViewById(R.id.new_version)
        releaseNotesText = view.findViewById(R.id.release_notes)
        progressContainer = view.findViewById(R.id.download_progress_container)
        progressBar = view.findViewById(R.id.download_progress)
        downloadStatus = view.findViewById(R.id.download_status)
        
        // Setear datos
        val currentVersion = arguments?.getString(ARG_CURRENT_VERSION, "1.0.0") ?: "1.0.0"
        currentVersionText.text = currentVersion
        newVersionText.text = updateInfo.versionName
        
        if (updateInfo.releaseNotes.isNotBlank()) {
            releaseNotesText.text = updateInfo.releaseNotes
            releaseNotesText.visibility = View.VISIBLE
        }
        
        // Click listeners
        btnUpdate.setOnClickListener { startDownload() }
        btnSnooze.setOnClickListener { snoozeAndDismiss() }
        
        // Focus inicial en el botón de actualizar
        btnUpdate.requestFocus()
        
        // Interceptar BACK para que no cierre el dialog
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                // No hacemos nada - el usuario DEBE elegir una opción
                Log.d(TAG, "BACK pressed - ignoring (user must choose an option)")
                true
            } else {
                false
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setCancelable(false)
            setCanceledOnTouchOutside(false)
        }
    }

    private fun startDownload() {
        if (isDownloading) return
        isDownloading = true
        
        Log.d(TAG, "Starting download: ${updateInfo.apkDownloadUrl}")
        
        // Mostrar progreso, ocultar botones
        btnUpdate.isEnabled = false
        btnUpdate.text = "DESCARGANDO..."
        btnSnooze.visibility = View.GONE
        progressContainer.visibility = View.VISIBLE
        progressBar.progress = 0
        downloadStatus.text = "Iniciando descarga..."
        
        downloader.downloadApk(
            url = updateInfo.apkDownloadUrl,
            onProgress = { state ->
                mainHandler.post {
                    progressBar.progress = state.progress
                    val downloadedMB = state.downloadedBytes / 1024 / 1024
                    val totalMB = state.totalBytes / 1024 / 1024
                    downloadStatus.text = "Descargando... ${state.progress}% ($downloadedMB MB / $totalMB MB)"
                }
            },
            onComplete = { state ->
                mainHandler.post {
                    handleDownloadComplete(state)
                }
            }
        )
    }

    private fun handleDownloadComplete(state: DownloadState) {
        isDownloading = false
        
        when (state) {
            is DownloadState.Completed -> {
                Log.d(TAG, "Download completed, installing...")
                downloadStatus.text = "Descarga completada. Instalando..."
                
                // Verificar permisos de instalación
                if (!UpdateInstaller.canInstallApks(requireContext())) {
                    Toast.makeText(
                        requireContext(),
                        "Necesitás permitir la instalación de aplicaciones",
                        Toast.LENGTH_LONG
                    ).show()
                    UpdateInstaller.openInstallPermissionSettings(requireContext())
                    resetToInitialState()
                    return
                }
                
                // Instalar
                val success = UpdateInstaller.installApk(requireContext(), state.apkFile)
                if (success) {
                    // El sistema muestra el instalador, el dialog se cierra
                    dismiss()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Error al instalar. Intentá de nuevo.",
                        Toast.LENGTH_LONG
                    ).show()
                    resetToInitialState()
                }
            }
            is DownloadState.Failed -> {
                Log.e(TAG, "Download failed: ${state.message}", state.exception)
                Toast.makeText(
                    requireContext(),
                    "Error al descargar: ${state.message}",
                    Toast.LENGTH_LONG
                ).show()
                resetToInitialState()
            }
            else -> {
                // No debería llegar acá
                resetToInitialState()
            }
        }
    }

    private fun resetToInitialState() {
        btnUpdate.isEnabled = true
        btnUpdate.text = "ACTUALIZAR AHORA"
        btnSnooze.visibility = View.VISIBLE
        progressContainer.visibility = View.GONE
        btnUpdate.requestFocus()
    }

    private fun snoozeAndDismiss() {
        Log.d(TAG, "User chose to snooze update")
        repository.snoozeUpdate()
        dismiss()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // Limpiar APK descargado si no se instaló
        if (!isDownloading) {
            downloader.deleteDownloadedApk()
        }
    }
}
