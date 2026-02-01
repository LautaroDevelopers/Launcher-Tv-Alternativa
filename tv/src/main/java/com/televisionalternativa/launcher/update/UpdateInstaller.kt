package com.televisionalternativa.launcher.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Maneja la instalación del APK descargado.
 * Usa FileProvider para Android 7+ (API 24+).
 */
object UpdateInstaller {
    
    private const val TAG = "UpdateInstaller"
    
    /**
     * Inicia el proceso de instalación del APK.
     * Abre el instalador del sistema donde el usuario confirma.
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            Log.d(TAG, "Installing APK: ${apkFile.absolutePath}")
            
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file does not exist")
                return false
            }
            
            val uri = getApkUri(context, apkFile)
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(intent)
            Log.d(TAG, "Install intent launched")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install APK", e)
            false
        }
    }

    /**
     * Obtiene el URI del APK usando FileProvider (necesario para Android 7+).
     */
    private fun getApkUri(context: Context, apkFile: File): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }
    }

    /**
     * Verifica si la app tiene permiso para instalar APKs.
     * En Android 8+ necesita REQUEST_INSTALL_PACKAGES.
     */
    fun canInstallApks(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Abre la pantalla de configuración para permitir instalar APKs.
     * Solo necesario en Android 8+.
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
