package com.televisionalternativa.launcher.permissions

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.televisionalternativa.launcher.R
import com.televisionalternativa.launcher.service.GlobalKeyService

/**
 * Dialog modal OBLIGATORIO que pide los permisos necesarios.
 * No se puede cerrar hasta que se otorguen todos los permisos.
 */
class PermissionsDialogFragment : DialogFragment() {

    private lateinit var overlayStatus: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var btnOverlay: Button
    private lateinit var btnAccessibility: Button
    private lateinit var btnContinue: Button

    companion object {
        const val TAG = "PermissionsDialog"

        fun newInstance(): PermissionsDialogFragment {
            return PermissionsDialogFragment()
        }

        /**
         * Verifica si todos los permisos están otorgados.
         */
        fun hasAllPermissions(context: Context): Boolean {
            return hasOverlayPermission(context) && hasAccessibilityPermission(context)
        }

        fun hasOverlayPermission(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }

        fun hasAccessibilityPermission(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            
            for (service in enabledServices) {
                if (service.id.contains(context.packageName) && 
                    service.id.contains("GlobalKeyService")) {
                    return true
                }
            }
            return false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.Theme_Launcher_PermissionsDialog)
        isCancelable = false // NO se puede cerrar con BACK
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_permissions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        overlayStatus = view.findViewById(R.id.overlay_status)
        accessibilityStatus = view.findViewById(R.id.accessibility_status)
        btnOverlay = view.findViewById(R.id.btn_overlay)
        btnAccessibility = view.findViewById(R.id.btn_accessibility)
        btnContinue = view.findViewById(R.id.btn_continue)

        btnOverlay.setOnClickListener {
            openOverlaySettings()
        }

        btnAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        btnContinue.setOnClickListener {
            if (hasAllPermissions(requireContext())) {
                dismiss()
            }
        }

        updateUI()

        // Bloquear BACK
        dialog?.setOnKeyListener { _, keyCode, _ ->
            keyCode == KeyEvent.KEYCODE_BACK
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    private fun updateUI() {
        val hasOverlay = hasOverlayPermission(requireContext())
        val hasAccessibility = hasAccessibilityPermission(requireContext())

        // Overlay status
        if (hasOverlay) {
            overlayStatus.text = "✓ Permitido"
            overlayStatus.setTextColor(resources.getColor(R.color.accent_green, null))
            btnOverlay.isEnabled = false
            btnOverlay.alpha = 0.5f
        } else {
            overlayStatus.text = "✗ Requerido"
            overlayStatus.setTextColor(resources.getColor(R.color.accent_red, null))
            btnOverlay.isEnabled = true
            btnOverlay.alpha = 1f
        }

        // Accessibility status
        if (hasAccessibility) {
            accessibilityStatus.text = "✓ Habilitado"
            accessibilityStatus.setTextColor(resources.getColor(R.color.accent_green, null))
            btnAccessibility.isEnabled = false
            btnAccessibility.alpha = 0.5f
        } else {
            accessibilityStatus.text = "✗ Requerido"
            accessibilityStatus.setTextColor(resources.getColor(R.color.accent_red, null))
            btnAccessibility.isEnabled = true
            btnAccessibility.alpha = 1f
        }

        // Continue button
        if (hasOverlay && hasAccessibility) {
            btnContinue.isEnabled = true
            btnContinue.alpha = 1f
            btnContinue.requestFocus()
        } else {
            btnContinue.isEnabled = false
            btnContinue.alpha = 0.5f
            
            // Focus en el primer botón que necesita acción
            if (!hasOverlay) {
                btnOverlay.requestFocus()
            } else {
                btnAccessibility.requestFocus()
            }
        }
    }

    private fun openOverlaySettings() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${requireContext().packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback a settings general
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
