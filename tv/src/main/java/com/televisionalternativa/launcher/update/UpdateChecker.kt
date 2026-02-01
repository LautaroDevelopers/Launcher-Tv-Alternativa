package com.televisionalternativa.launcher.update

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Chequea actualizaciones desde GitHub Releases.
 * 
 * Usa la API pública de GitHub para obtener el último release.
 * No requiere autenticación para repos públicos.
 */
class UpdateChecker(
    private val context: Context,
    private val githubOwner: String,
    private val githubRepo: String
) {
    private val executor = Executors.newSingleThreadExecutor()
    
    companion object {
        private const val TAG = "UpdateChecker"
        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val CONNECT_TIMEOUT = 10_000 // 10 segundos
        private const val READ_TIMEOUT = 15_000 // 15 segundos
    }

    /**
     * Chequea si hay una actualización disponible.
     * DEBE llamarse desde un background thread o usar el callback.
     */
    fun checkForUpdate(callback: (UpdateCheckResult) -> Unit) {
        executor.execute {
            val result = checkForUpdateSync()
            callback(result)
        }
    }

    /**
     * Versión síncrona del chequeo. NO llamar desde UI thread.
     */
    fun checkForUpdateSync(): UpdateCheckResult {
        return try {
            Log.d(TAG, "Checking for updates from $githubOwner/$githubRepo")
            
            val latestRelease = fetchLatestRelease()
                ?: return UpdateCheckResult.Error("No se pudo obtener información del release")
            
            val remoteVersion = parseVersionName(latestRelease.getString("tag_name"))
            val localVersion = getLocalVersionName()
            
            Log.d(TAG, "Local version: $localVersion, Remote version: $remoteVersion")
            
            if (isNewerVersion(remoteVersion, localVersion)) {
                val updateInfo = parseUpdateInfo(latestRelease)
                if (updateInfo != null) {
                    Log.d(TAG, "Update available: ${updateInfo.versionName}")
                    UpdateCheckResult.UpdateAvailable(updateInfo)
                } else {
                    UpdateCheckResult.Error("No se encontró APK en el release")
                }
            } else {
                Log.d(TAG, "No update available")
                UpdateCheckResult.NoUpdateAvailable
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            UpdateCheckResult.Error("Error al buscar actualizaciones: ${e.message}", e)
        }
    }

    /**
     * Obtiene el último release desde GitHub API.
     */
    private fun fetchLatestRelease(): JSONObject? {
        val url = URL("$GITHUB_API_BASE/repos/$githubOwner/$githubRepo/releases/latest")
        val connection = url.openConnection() as HttpURLConnection
        
        return try {
            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "TelevisionAlternativaLauncher")
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "GitHub API response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                JSONObject(response)
            } else {
                Log.e(TAG, "GitHub API error: $responseCode")
                null
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Parsea la respuesta de GitHub a UpdateInfo.
     * Busca un asset que sea un APK.
     */
    private fun parseUpdateInfo(releaseJson: JSONObject): UpdateInfo? {
        val tagName = releaseJson.getString("tag_name")
        val versionName = parseVersionName(tagName)
        val releaseNotes = releaseJson.optString("body", "").trim()
        
        // Buscar el APK en los assets
        val assets = releaseJson.optJSONArray("assets") ?: return null
        
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val assetName = asset.getString("name")
            
            if (assetName.endsWith(".apk")) {
                return UpdateInfo(
                    versionName = versionName,
                    tagName = tagName,
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = asset.getString("browser_download_url"),
                    apkSizeBytes = asset.getLong("size")
                )
            }
        }
        
        return null
    }

    /**
     * Extrae el nombre de versión del tag (ej: "v1.1.0" -> "1.1.0")
     */
    private fun parseVersionName(tagName: String): String {
        return tagName.removePrefix("v").removePrefix("V")
    }

    /**
     * Obtiene la versión actual instalada de la app.
     */
    private fun getLocalVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "0.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Could not get local version", e)
            "0.0.0"
        }
    }

    /**
     * Compara dos versiones semánticas.
     * @return true si remoteVersion es mayor que localVersion
     */
    private fun isNewerVersion(remoteVersion: String, localVersion: String): Boolean {
        try {
            val remote = remoteVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val local = localVersion.split(".").map { it.toIntOrNull() ?: 0 }
            
            // Comparar major.minor.patch
            for (i in 0 until maxOf(remote.size, local.size)) {
                val r = remote.getOrElse(i) { 0 }
                val l = local.getOrElse(i) { 0 }
                
                if (r > l) return true
                if (r < l) return false
            }
            
            return false // Son iguales
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions: $remoteVersion vs $localVersion", e)
            return false
        }
    }
}
