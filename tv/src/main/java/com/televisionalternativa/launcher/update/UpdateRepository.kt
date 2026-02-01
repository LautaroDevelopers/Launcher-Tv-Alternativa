package com.televisionalternativa.launcher.update

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Maneja la persistencia del estado de actualizaciones.
 * - Snooze: "Recordarme en 1 hora"
 * - Cache del último chequeo para no martillar la API
 */
class UpdateRepository(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "UpdateRepository"
        private const val PREFS_NAME = "update_prefs"
        
        private const val KEY_SNOOZE_UNTIL = "snooze_until"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val KEY_CACHED_VERSION = "cached_version"
        private const val KEY_CACHED_APK_URL = "cached_apk_url"
        private const val KEY_CACHED_RELEASE_NOTES = "cached_release_notes"
        private const val KEY_CACHED_APK_SIZE = "cached_apk_size"
        private const val KEY_CACHED_TAG_NAME = "cached_tag_name"
        
        /** Tiempo mínimo entre chequeos a la API (para no abusar) */
        const val CHECK_COOLDOWN_MS = 60 * 60 * 1000L // 1 hora
        
        /** Tiempo del snooze cuando el usuario dice "recordarme después" */
        const val SNOOZE_DURATION_MS = 60 * 60 * 1000L // 1 hora
    }

    /**
     * Verifica si el usuario activó el snooze y todavía está activo.
     */
    fun isUpdateSnoozed(): Boolean {
        val snoozeUntil = prefs.getLong(KEY_SNOOZE_UNTIL, 0)
        val isSnoozed = System.currentTimeMillis() < snoozeUntil
        
        if (isSnoozed) {
            val remainingMinutes = (snoozeUntil - System.currentTimeMillis()) / 1000 / 60
            Log.d(TAG, "Update is snoozed for $remainingMinutes more minutes")
        }
        
        return isSnoozed
    }

    /**
     * Activa el snooze por el tiempo configurado.
     */
    fun snoozeUpdate() {
        val snoozeUntil = System.currentTimeMillis() + SNOOZE_DURATION_MS
        prefs.edit().putLong(KEY_SNOOZE_UNTIL, snoozeUntil).apply()
        Log.d(TAG, "Update snoozed until ${java.util.Date(snoozeUntil)}")
    }

    /**
     * Limpia el snooze (después de una actualización exitosa o para forzar).
     */
    fun clearSnooze() {
        prefs.edit().remove(KEY_SNOOZE_UNTIL).apply()
        Log.d(TAG, "Snooze cleared")
    }

    /**
     * Verifica si pasó suficiente tiempo desde el último chequeo.
     * Esto evita llamar a la API de GitHub cada vez que se abre el launcher.
     */
    fun shouldCheckForUpdates(): Boolean {
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0)
        val shouldCheck = System.currentTimeMillis() - lastCheck > CHECK_COOLDOWN_MS
        
        if (!shouldCheck) {
            val nextCheckIn = (CHECK_COOLDOWN_MS - (System.currentTimeMillis() - lastCheck)) / 1000 / 60
            Log.d(TAG, "Skipping update check, next check in $nextCheckIn minutes")
        }
        
        return shouldCheck
    }

    /**
     * Registra que se hizo un chequeo ahora.
     */
    fun recordCheckTime() {
        prefs.edit().putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply()
    }

    /**
     * Guarda la información de una actualización disponible en cache.
     */
    fun cacheUpdateInfo(updateInfo: UpdateInfo) {
        prefs.edit()
            .putString(KEY_CACHED_VERSION, updateInfo.versionName)
            .putString(KEY_CACHED_TAG_NAME, updateInfo.tagName)
            .putString(KEY_CACHED_APK_URL, updateInfo.apkDownloadUrl)
            .putString(KEY_CACHED_RELEASE_NOTES, updateInfo.releaseNotes)
            .putLong(KEY_CACHED_APK_SIZE, updateInfo.apkSizeBytes)
            .apply()
        Log.d(TAG, "Cached update info for version ${updateInfo.versionName}")
    }

    /**
     * Obtiene la información cacheada de una actualización.
     * Útil si el usuario cerró el dialog y vuelve a abrir el launcher.
     */
    fun getCachedUpdateInfo(): UpdateInfo? {
        val version = prefs.getString(KEY_CACHED_VERSION, null) ?: return null
        val tagName = prefs.getString(KEY_CACHED_TAG_NAME, null) ?: return null
        val apkUrl = prefs.getString(KEY_CACHED_APK_URL, null) ?: return null
        val releaseNotes = prefs.getString(KEY_CACHED_RELEASE_NOTES, "") ?: ""
        val apkSize = prefs.getLong(KEY_CACHED_APK_SIZE, 0)
        
        return UpdateInfo(
            versionName = version,
            tagName = tagName,
            releaseNotes = releaseNotes,
            apkDownloadUrl = apkUrl,
            apkSizeBytes = apkSize
        )
    }

    /**
     * Limpia el cache de actualización (después de instalar o si ya no aplica).
     */
    fun clearCachedUpdateInfo() {
        prefs.edit()
            .remove(KEY_CACHED_VERSION)
            .remove(KEY_CACHED_TAG_NAME)
            .remove(KEY_CACHED_APK_URL)
            .remove(KEY_CACHED_RELEASE_NOTES)
            .remove(KEY_CACHED_APK_SIZE)
            .apply()
        Log.d(TAG, "Cleared cached update info")
    }

    /**
     * Limpia todo (para testing o reset).
     */
    fun clearAll() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Cleared all update preferences")
    }
}
