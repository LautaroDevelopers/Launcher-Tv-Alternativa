package com.televisionalternativa.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import com.televisionalternativa.launcher.data.LauncherConfigProvider
import com.televisionalternativa.launcher.domain.ActionType
import com.televisionalternativa.launcher.domain.LauncherItem
import com.televisionalternativa.launcher.domain.SectionType

class MainFragment : RowsSupportFragment() {

    private var apps: ArrayList<AppInfo>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apps = arguments?.getParcelableArrayList(ARG_APPS)
        Log.d(TAG, "onCreate - received ${apps?.size ?: 0} apps")

        // Configurar listener de clicks
        onItemViewClickedListener = ItemViewClickedListener()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.d(TAG, "onActivityCreated")
        loadRows()
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Desactivar clipping para que el glow se vea
        disableClippingRecursive(view)
    }
    
    private fun disableClippingRecursive(view: View) {
        if (view is ViewGroup) {
            view.clipChildren = false
            view.clipToPadding = false
            for (i in 0 until view.childCount) {
                disableClippingRecursive(view.getChildAt(i))
            }
        }
    }

    private fun loadRows() {
        Log.d(TAG, "loadRows - apps count: ${apps?.size}")
        val rowsAdapter = ArrayObjectAdapter(NoClipListRowPresenter())

        // --- SECCIONES DINÁMICAS DESDE LA CONFIGURACIÓN REMOTA ---
        try {
            val config = LauncherConfigProvider.loadConfig(requireContext())
            config?.sections?.forEachIndexed { index, section ->
                val header = HeaderItem(section.id.hashCode().toLong(), section.title ?: "")
                when (section.type) {
                    SectionType.HERO_BANNER -> {
                        // TODO: Implementar Hero Banner si es necesario
                        Log.d(TAG, "Hero Banner section found, but not yet implemented.")
                    }
                    SectionType.CONTENT_ROW -> {
                        val contentRowAdapter = ArrayObjectAdapter(ContentCardPresenter())
                        section.items.forEach { item -> contentRowAdapter.add(item) }
                        rowsAdapter.add(ListRow(header, contentRowAdapter))
                        Log.d(TAG, "Added Content Row: ${section.title} with ${section.items.size} items")
                    }
                    // Add other section types here if needed.
                    // For example, if you want to display APP_ROW from config
                    SectionType.APP_ROW -> {
                        // This block would handle apps loaded from config, not local apps.
                        // Currently, local apps are handled below.
                        Log.d(TAG, "App Row section found in config, but local apps are handled separately.")
                    }
                    SectionType.SETTINGS -> {
                        Log.d(TAG, "Settings section found in config, but not displayed as a row.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading dynamic sections", e)
        }

        // 1. APPS INSTALADAS (siempre al final, o donde se defina en config.sections)
        // This block should only run if APP_ROW is not handled by the dynamic sections
        var appRowAlreadyAdded = false
        for (i in 0 until rowsAdapter.size()) {
            val row = rowsAdapter.get(i)
            if (row is ListRow && row.headerItem?.id == 0L) { // Assuming ID 0 is for "Tus Aplicaciones"
                appRowAlreadyAdded = true
                break
            }
        }
        if (!appRowAlreadyAdded) { // Only add if not already added by dynamic config
            apps?.let { appList ->
                Log.d(TAG, "Creating apps row with ${appList.size} apps")
                val header = HeaderItem(0, "Tus Aplicaciones")
                val appListRowAdapter = ArrayObjectAdapter(AppCardPresenter())
                for (app in appList) {
                    appListRowAdapter.add(app)
                }
                rowsAdapter.add(ListRow(header, appListRowAdapter))
            } ?: Log.e(TAG, "Apps list is NULL!")
        }

        adapter = rowsAdapter
        Log.d(TAG, "Adapter set with ${rowsAdapter.size()} rows")
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {
            Log.d(TAG, "Item clicked: $item")

            when (item) {
                is AppInfo -> {
                    // Abrir app instalada
                    val intent = requireContext().packageManager.getLaunchIntentForPackage(item.packageName)
                    if (intent != null) {
                        Log.d(TAG, "Launching app: ${item.packageName}")
                        startActivity(intent)
                    } else {
                        Log.e(TAG, "Could not launch app: ${item.packageName}")
                        Toast.makeText(activity, "No se pudo abrir la aplicación", Toast.LENGTH_SHORT).show()
                    }
                }
                is LauncherItem -> {
                    // Contenido o Settings
                    when (item.actionType) {
                        ActionType.OPEN_APP -> {
                            val intent = requireContext().packageManager.getLaunchIntentForPackage(item.actionData)
                            if (intent != null) {
                                Log.d(TAG, "Launching app: ${item.title}")
                                startActivity(intent)
                            } else {
                                Log.e(TAG, "Could not launch app: ${item.actionData}")
                                Toast.makeText(requireContext(), "No se pudo abrir ${item.title}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        ActionType.DEEP_LINK -> {
                            try {
                                Log.d(TAG, "Attempting to open deep link: ${item.actionData}")
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.actionData))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(intent)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error opening deep link: ${item.actionData}", e)
                                Toast.makeText(requireContext(), "No se pudo abrir el contenido ${item.title}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        ActionType.SYSTEM_INTENT -> {
                            // Abrir settings del sistema (WiFi, Bluetooth, etc.)
                            val action = item.actionData
                            try {
                                Log.d(TAG, "Attempting to open settings: $action")
                                val intent = android.content.Intent(action)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(intent)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error opening specific settings: $action", e)

                                // FALLBACK: Intentar abrir ajustes generales si falla el específico
                                try {
                                    Log.d(TAG, "Trying fallback to general settings")
                                    val fallbackIntent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                                    fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(fallbackIntent)
                                } catch (e2: Exception) {
                                    Log.e(TAG, "Could not open any settings", e2)
                                    Toast.makeText(requireContext(), "No se pudo abrir la configuración", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        else -> {
                            Toast.makeText(requireContext(), "Acción no implementada", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown item type clicked: ${item::class.simpleName}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainFragment"
        private const val ARG_APPS = "apps"

        fun newInstance(apps: ArrayList<AppInfo>): MainFragment {
            Log.d(TAG, "newInstance with ${apps.size} apps")
            val fragment = MainFragment()
            val args = Bundle()
            args.putParcelableArrayList(ARG_APPS, apps)
            fragment.arguments = args
            return fragment
        }
    }
}