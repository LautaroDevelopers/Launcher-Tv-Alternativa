package com.televisionalternativa.launcher.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Detecta qué apps se han usado recientemente usando UsageStatsManager.
 *
 * NOTA: Requiere permiso PACKAGE_USAGE_STATS que se debe otorgar manualmente en Settings > Apps >
 * Special Access > Usage access
 */
object RecentAppsDetector {

  private const val TAG = "RecentAppsDetector"
  private const val OTT_PACKAGE = "studio.scillarium.ottnavigator"

  /** Verifica si tenemos permiso para leer estadísticas de uso */
  fun hasUsageStatsPermission(context: Context): Boolean {
    val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val now = System.currentTimeMillis()
    val stats =
            usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    now - TimeUnit.MINUTES.toMillis(1),
                    now
            )
    return stats != null && stats.isNotEmpty()
  }

  /** Obtiene las apps usadas recientemente (últimas 24 horas) */
  fun getRecentlyUsedApps(context: Context, maxResults: Int = 10): List<RecentAppInfo> {
    if (!hasUsageStatsPermission(context)) {
      Log.w(TAG, "No permission to read usage stats")
      return emptyList()
    }

    val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val now = System.currentTimeMillis()
    val dayAgo = now - TimeUnit.HOURS.toMillis(24)

    val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayAgo, now)

    val recentApps =
            stats
                    .filter { it.lastTimeUsed > 0 }
                    .sortedByDescending { it.lastTimeUsed }
                    .take(maxResults)
                    .mapNotNull { usageStat ->
                      try {
                        val appInfo =
                                context.packageManager.getApplicationInfo(usageStat.packageName, 0)
                        RecentAppInfo(
                                packageName = usageStat.packageName,
                                label =
                                        context.packageManager
                                                .getApplicationLabel(appInfo)
                                                .toString(),
                                lastUsedTime = usageStat.lastTimeUsed,
                                totalTimeInForeground = usageStat.totalTimeInForeground
                        )
                      } catch (e: Exception) {
                        Log.w(TAG, "Error getting info for ${usageStat.packageName}", e)
                        null
                      }
                    }

    Log.d(TAG, "Found ${recentApps.size} recently used apps")
    return recentApps
  }

  /** Verifica si OTT Navigator se usó recientemente (últimas 24h) */
  fun wasOttUsedRecently(context: Context): Boolean {
    val recentApps = getRecentlyUsedApps(context, 50)
    return recentApps.any { it.packageName == OTT_PACKAGE }
  }

  data class RecentAppInfo(
          val packageName: String,
          val label: String,
          val lastUsedTime: Long,
          val totalTimeInForeground: Long
  )
}
