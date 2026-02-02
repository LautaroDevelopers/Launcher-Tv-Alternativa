package com.televisionalternativa.launcher.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * AccessibilityService que intercepta teclas globalmente,
 * incluso cuando el launcher no está en primer plano.
 */
class GlobalKeyService : AccessibilityService() {

    companion object {
        private const val TAG = "GlobalKeyService"
        
        // Keycode del botón que abre el panel (TV_INPUT = 178)
        private const val SETTINGS_KEYCODE = KeyEvent.KEYCODE_TV_INPUT
        
        // Action para el broadcast
        const val ACTION_SHOW_SETTINGS_PANEL = "com.televisionalternativa.launcher.SHOW_SETTINGS_PANEL"
        
        // Para saber si el servicio está activo
        var isServiceRunning = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "GlobalKeyService connected")
        isServiceRunning = true
        
        // Configurar para recibir eventos de teclado
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No nos interesan los eventos de accesibilidad, solo las teclas
    }

    override fun onInterrupt() {
        Log.d(TAG, "GlobalKeyService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        Log.d(TAG, "GlobalKeyService destroyed")
    }

    /**
     * Intercepta eventos de teclado GLOBALMENTE
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            Log.d(TAG, "Global key event: keyCode=${event.keyCode}")
            
            // Tecla para abrir el panel
            if (event.keyCode == SETTINGS_KEYCODE) {
                Log.d(TAG, "Settings key pressed - toggling overlay panel")
                toggleSettingsOverlay()
                return true // Consumir el evento
            }
            
            // BACK cierra el overlay si está visible
            if (event.keyCode == KeyEvent.KEYCODE_BACK && SettingsOverlayService.isOverlayVisible) {
                Log.d(TAG, "BACK pressed while overlay visible - hiding overlay")
                hideSettingsOverlay()
                return true // Consumir el evento
            }
        }
        return super.onKeyEvent(event)
    }

    private fun toggleSettingsOverlay() {
        val serviceIntent = Intent(this, SettingsOverlayService::class.java)
        serviceIntent.action = SettingsOverlayService.ACTION_TOGGLE
        startService(serviceIntent)
    }

    private fun showSettingsOverlay() {
        val serviceIntent = Intent(this, SettingsOverlayService::class.java)
        serviceIntent.action = SettingsOverlayService.ACTION_SHOW
        startService(serviceIntent)
    }

    private fun hideSettingsOverlay() {
        val serviceIntent = Intent(this, SettingsOverlayService::class.java)
        serviceIntent.action = SettingsOverlayService.ACTION_HIDE
        startService(serviceIntent)
    }
}
