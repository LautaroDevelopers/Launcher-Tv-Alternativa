package com.televisionalternativa.launcher.update

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Descarga el APK de actualización con reporte de progreso.
 */
class UpdateDownloader(private val context: Context) {
    
    private val executor = Executors.newSingleThreadExecutor()
    
    companion object {
        private const val TAG = "UpdateDownloader"
        private const val APK_FILE_NAME = "launcher_update.apk"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 60_000 // 1 minuto para descargas lentas
    }

    /**
     * Descarga el APK de forma asíncrona con callbacks de progreso.
     */
    fun downloadApk(
        url: String,
        onProgress: (DownloadState.Downloading) -> Unit,
        onComplete: (DownloadState) -> Unit
    ) {
        executor.execute {
            val result = downloadApkSync(url, onProgress)
            onComplete(result)
        }
    }

    /**
     * Versión síncrona de la descarga. NO llamar desde UI thread.
     */
    fun downloadApkSync(
        url: String,
        onProgress: ((DownloadState.Downloading) -> Unit)? = null
    ): DownloadState {
        var connection: HttpURLConnection? = null
        
        return try {
            Log.d(TAG, "Starting download from: $url")
            
            val apkFile = getApkFile()
            
            // Borrar archivo anterior si existe
            if (apkFile.exists()) {
                apkFile.delete()
            }
            
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("User-Agent", "TelevisionAlternativaLauncher")
                // GitHub redirige, hay que seguir
                instanceFollowRedirects = true
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "Download response code: $responseCode")
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return DownloadState.Failed("Error del servidor: $responseCode")
            }
            
            val totalBytes = connection.contentLength.toLong()
            var downloadedBytes = 0L
            
            Log.d(TAG, "Total size: ${totalBytes / 1024} KB")
            
            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        // Reportar progreso
                        val progress = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt()
                        } else {
                            -1 // Indeterminado
                        }
                        
                        onProgress?.invoke(
                            DownloadState.Downloading(
                                progress = progress,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes
                            )
                        )
                    }
                }
            }
            
            Log.d(TAG, "Download completed: ${apkFile.absolutePath}")
            DownloadState.Completed(apkFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            DownloadState.Failed("Error al descargar: ${e.message}", e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Obtiene el archivo donde se guarda el APK descargado.
     * Usa el cache interno de la app (no requiere permisos).
     */
    fun getApkFile(): File {
        return File(context.cacheDir, APK_FILE_NAME)
    }

    /**
     * Elimina el APK descargado (después de instalar o si falló).
     */
    fun deleteDownloadedApk() {
        val file = getApkFile()
        if (file.exists()) {
            file.delete()
            Log.d(TAG, "Deleted downloaded APK")
        }
    }

    /**
     * Verifica si ya hay un APK descargado.
     */
    fun hasDownloadedApk(): Boolean {
        return getApkFile().exists()
    }
}
