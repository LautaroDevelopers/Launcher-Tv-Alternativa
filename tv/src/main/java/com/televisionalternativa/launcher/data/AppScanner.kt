package com.televisionalternativa.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.util.Log
import com.televisionalternativa.launcher.AppInfo

/**
 * Escanea las aplicaciones instaladas en el dispositivo. Filtra apps de sistema que no son
 * relevantes para el usuario.
 */
object AppScanner {

  private const val TAG = "AppScanner"

  /** Tipo de launcher para filtrar apps */
  enum class LauncherType {
    /** Apps de celular/tablet (CATEGORY_LAUNCHER) */
    MOBILE,
    /** Apps de Android TV (CATEGORY_LEANBACK_LAUNCHER) */
    TV
  }

  data class InstalledApp(
          val packageName: String,
          val label: String,
          val icon: Drawable,
          val isSystemApp: Boolean
  )

  /**
   * Obtiene apps para Android TV como ArrayList<AppInfo>.
   * Usa LEANBACK_LAUNCHER y carga banners cuando están disponibles.
   * Este es el método principal para el Launcher de TV.
   */
  fun getTvApps(context: Context): ArrayList<AppInfo> {
    val pm = context.packageManager
    val apps = ArrayList<AppInfo>()

    try {
      val intent = Intent(Intent.ACTION_MAIN, null).apply { 
        addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) 
      }
      val resolveInfoList = pm.queryIntentActivities(intent, 0)
      
      Log.d(TAG, "Total TV apps found by PackageManager: ${resolveInfoList.size}")

      for (resolveInfo in resolveInfoList) {
        val packageName = resolveInfo.activityInfo.packageName

        // Salteamos nuestra propia app
        if (packageName == context.packageName) continue

        try {
          val applicationInfo = pm.getApplicationInfo(packageName, 0)
          val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

          // Aplicamos el filtro de blacklist/whitelist
          if (shouldIncludeApp(packageName, isSystemApp)) {
            val label = resolveInfo.loadLabel(pm).toString()
            val appInfoData = AppInfo(packageName = packageName, label = label)

            // Preferimos banner para TV, sino usamos el icono normal
            appInfoData.icon = if (applicationInfo.banner != 0) {
              pm.getDrawable(packageName, applicationInfo.banner, applicationInfo)
            } else {
              resolveInfo.loadIcon(pm)
            }

            apps.add(appInfoData)
          }
        } catch (e: PackageManager.NameNotFoundException) {
          Log.w(TAG, "Package not found: $packageName")
        } catch (e: Exception) {
          Log.e(TAG, "Error loading app info for $packageName", e)
        }
      }

      // Ordenamos alfabéticamente por nombre
      apps.sortBy { it.label.lowercase() }
      
    } catch (e: Exception) {
      Log.e(TAG, "Error scanning TV apps", e)
    }

    Log.d(TAG, "Final TV app count after filtering: ${apps.size}")
    return apps
  }

  /**
   * Obtiene todas las apps que tienen Launcher (apps que el usuario puede abrir). Filtra apps de
   * sistema que no son útiles.
   * 
   * @param launcherType Tipo de launcher (MOBILE o TV)
   */
  fun getInstalledApps(context: Context, launcherType: LauncherType = LauncherType.MOBILE): List<InstalledApp> {
    val pm = context.packageManager
    val apps = mutableListOf<InstalledApp>()

    try {
      val category = when (launcherType) {
        LauncherType.MOBILE -> Intent.CATEGORY_LAUNCHER
        LauncherType.TV -> Intent.CATEGORY_LEANBACK_LAUNCHER
      }
      
      val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(category) }
      val resolveInfoList = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)

      for (resolveInfo in resolveInfoList) {
        val packageName = resolveInfo.activityInfo.packageName

        // Salteamos nuestra propia app
        if (packageName == context.packageName) continue

        try {
          val appInfo = pm.getApplicationInfo(packageName, 0)
          val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

          // Filtramos apps de sistema aburridas (settings, launchers, etc.)
          if (shouldIncludeApp(packageName, isSystemApp)) {
            apps.add(
                    InstalledApp(
                            packageName = packageName,
                            label = pm.getApplicationLabel(appInfo).toString(),
                            icon = pm.getApplicationIcon(packageName),
                            isSystemApp = isSystemApp
                    )
            )
          }
        } catch (e: PackageManager.NameNotFoundException) {
          Log.w(TAG, "Package not found: $packageName")
        }
      }

      // Ordenamos: primero apps de usuario, después por nombre
      apps.sortWith(compareBy({ it.isSystemApp }, { it.label }))
    } catch (e: Exception) {
      Log.e(TAG, "Error scanning apps", e)
    }

    Log.d(TAG, "Found ${apps.size} apps")
    return apps
  }

  /**
   * Decide si una app debe mostrarse en el launcher. Excluye launchers, settings, y otras apps de
   * sistema inútiles.
   */
  private fun shouldIncludeApp(packageName: String, isSystemApp: Boolean): Boolean {
    // Apps que SIEMPRE excluimos (launchers, settings, etc.)
    val blacklist =
            listOf(
                    "com.android.launcher",
                    "com.google.android.launcher",
                    "com.android.systemui",
                    "com.android.vending", // Play Store en TV a veces es inútil
                    "com.google.android.inputmethod",
                    "com.google.android.tvlauncher",
                    "com.google.android.tvrecommendations",
                    "com.tcl.browser"
            )

    if (blacklist.any { packageName.contains(it) }) {
      return false
    }

    // Apps de streaming/contenido que SÍ queremos mostrar aunque sean de sistema
    val whitelistKeywords =
            listOf(
                    "youtube",
                    "netflix",
                    "prime",
                    "disney",
                    "hbo",
                    "spotify",
                    "twitch",
                    "kodi",
                    "plex",
                    "vlc",
                    "mx.player"
            )

    if (whitelistKeywords.any { packageName.lowercase().contains(it) }) {
      return true
    }

    // Si es app de usuario (no sistema), la mostramos
    return !isSystemApp
  }

  /** Abre una aplicación por su package name. */
  fun launchApp(context: Context, packageName: String): Boolean {
    return try {
      val intent = context.packageManager.getLaunchIntentForPackage(packageName)
      if (intent != null) {
        context.startActivity(intent)
        true
      } else {
        Log.w(TAG, "No launch intent for $packageName")
        false
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error launching $packageName", e)
      false
    }
  }
}
