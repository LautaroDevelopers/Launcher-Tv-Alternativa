package com.televisionalternativa.launcher

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.fragment.app.FragmentActivity

/**
 * Activity que solicita el permiso de Usage Stats
 */
class RequestUsageStatsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (!hasUsageStatsPermission()) {
            // Abrir settings para que el usuario otorgue el permiso
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
            
            Toast.makeText(
                this,
                "Por favor, habilita el acceso de uso para LauncherTV",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "Permiso ya otorgado", Toast.LENGTH_SHORT).show()
        }
        
        finish()
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
