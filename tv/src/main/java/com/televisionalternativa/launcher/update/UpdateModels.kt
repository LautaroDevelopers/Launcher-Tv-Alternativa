package com.televisionalternativa.launcher.update

/**
 * Información de una actualización disponible.
 * Parseada desde GitHub Releases API.
 */
data class UpdateInfo(
    /** Nombre de la versión (ej: "1.1.0") */
    val versionName: String,
    
    /** Tag del release (ej: "v1.1.0") */
    val tagName: String,
    
    /** Notas del release (changelog) */
    val releaseNotes: String,
    
    /** URL de descarga directa del APK */
    val apkDownloadUrl: String,
    
    /** Tamaño del APK en bytes */
    val apkSizeBytes: Long
)

/**
 * Resultado del chequeo de actualizaciones.
 */
sealed class UpdateCheckResult {
    /** Hay una actualización disponible */
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateCheckResult()
    
    /** Ya tenés la última versión */
    data object NoUpdateAvailable : UpdateCheckResult()
    
    /** Error al chequear (sin conexión, API caída, etc.) */
    data class Error(val message: String, val exception: Throwable? = null) : UpdateCheckResult()
}

/**
 * Estado de la descarga del APK.
 */
sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Int, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class Completed(val apkFile: java.io.File) : DownloadState()
    data class Failed(val message: String, val exception: Throwable? = null) : DownloadState()
}
