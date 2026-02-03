package com.televisionalternativa.launcher.screensaver

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Helper para iniciar/detener el screensaver overlay.
 */
object ScreensaverHelper {
    
    private const val TAG = "ScreensaverHelper"
    
    /**
     * Inicia el screensaver overlay.
     */
    fun startScreensaver(context: Context): Boolean {
        return try {
            Log.d(TAG, "Starting screensaver overlay")
            val intent = Intent(context, ScreensaverOverlayService::class.java)
            intent.action = ScreensaverOverlayService.ACTION_START
            context.startService(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting screensaver", e)
            false
        }
    }
    
    /**
     * Detiene el screensaver overlay.
     */
    fun stopScreensaver(context: Context) {
        try {
            Log.d(TAG, "Stopping screensaver overlay")
            val intent = Intent(context, ScreensaverOverlayService::class.java)
            intent.action = ScreensaverOverlayService.ACTION_STOP
            context.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping screensaver", e)
        }
    }
    
    /**
     * Verifica si el screensaver está corriendo.
     */
    fun isRunning(): Boolean = ScreensaverOverlayService.isRunning
}
