package com.televisionalternativa.launcher.data

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Log
import com.televisionalternativa.launcher.domain.ActionType
import com.televisionalternativa.launcher.domain.LauncherItem

/**
 * Integración con OTT Navigator IPTV
 */
object OttNavigatorIntegration {

    private const val TAG = "OttNavigatorIntegration"
    private const val PACKAGE_NAME = "studio.scillarium.ottnavigator"

    /**
     * Verifica si OTT Navigator está instalado
     */
    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Obtiene el icono de OTT Navigator
     */
    fun getAppIcon(context: Context): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(PACKAGE_NAME)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting OTT icon", e)
            null
        }
    }

    /**
     * Crea items de launcher para acceder a OTT Navigator
     */
    fun createLauncherItems(context: Context): List<LauncherItem> {
        if (!isInstalled(context)) {
            Log.w(TAG, "OTT Navigator not installed")
            return emptyList()
        }

        return listOf(
            LauncherItem(
                id = "ott_main",
                title = "Abrir OTT Navigator",
                imageUrl = null,
                actionType = ActionType.OPEN_APP,
                actionData = PACKAGE_NAME
            ),
            LauncherItem(
                id = "ott_favorites",
                title = "Favoritos",
                imageUrl = null,
                actionType = ActionType.OPEN_APP,
                actionData = PACKAGE_NAME
            ),
            LauncherItem(
                id = "ott_recent",
                title = "Visto Recientemente",
                imageUrl = null,
                actionType = ActionType.OPEN_APP,
                actionData = PACKAGE_NAME
            ),
            LauncherItem(
                id = "ott_live",
                title = "TV en Vivo",
                imageUrl = null,
                actionType = ActionType.OPEN_APP,
                actionData = PACKAGE_NAME
            ),
            LauncherItem(
                id = "ott_movies",
                title = "Películas",
                imageUrl = null,
                actionType = ActionType.OPEN_APP,
                actionData = PACKAGE_NAME
            ),
            LauncherItem(
                id = "ott_series",
                title = "Series",
                imageUrl = null,
                actionType = ActionType.OPEN_APP,
                actionData = PACKAGE_NAME
            )
        )
    }

    /**
     * Abre OTT Navigator
     */
    fun launch(context: Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
            if (intent != null) {
                context.startActivity(intent)
                true
            } else {
                Log.w(TAG, "No launch intent for OTT Navigator")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching OTT Navigator", e)
            false
        }
    }

    /**
     * TODO: Investigar deep links de OTT Navigator para abrir secciones específicas
     * Posibles esquemas:
     * - ottnavigator://favorites
     * - ottnavigator://recent
     * - ottnavigator://live
     * 
     * Necesitamos investigar si OTT Navigator soporta deep links.
     */
}
