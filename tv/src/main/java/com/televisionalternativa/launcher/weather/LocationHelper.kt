package com.televisionalternativa.launcher.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Helper para obtener la ubicación del dispositivo.
 * Usa GPS o Network provider según disponibilidad.
 */
class LocationHelper(private val context: Context) {

    companion object {
        private const val TAG = "LocationHelper"
        private const val LOCATION_UPDATE_INTERVAL = 30 * 60 * 1000L // 30 minutos
        private const val LOCATION_UPDATE_DISTANCE = 1000f // 1 km
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    // Cache de última ubicación conocida
    private var cachedLocation: Location? = null
    private var lastUpdateTime: Long = 0

    /**
     * Verifica si tenemos permisos de ubicación.
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Verifica si el GPS está habilitado.
     */
    fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Obtiene la última ubicación conocida (rápido, puede ser vieja).
     */
    fun getLastKnownLocation(): Location? {
        if (!hasLocationPermission()) {
            Log.w(TAG, "No location permission")
            return null
        }

        try {
            // Intentar GPS primero
            var location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            
            // Si no hay GPS, intentar Network
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }

            if (location != null) {
                cachedLocation = location
                lastUpdateTime = System.currentTimeMillis()
                Log.d(TAG, "Got last known location: ${location.latitude}, ${location.longitude}")
            }

            return location
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException getting location", e)
            return null
        }
    }

    /**
     * Solicita una actualización de ubicación (async).
     * Llama al callback cuando obtiene la ubicación.
     */
    fun requestLocationUpdate(callback: (Location?) -> Unit) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "No location permission for update")
            callback(null)
            return
        }

        if (!isLocationEnabled()) {
            Log.w(TAG, "Location is disabled")
            // Devolver cached si existe
            callback(cachedLocation)
            return
        }

        try {
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> {
                    callback(cachedLocation)
                    return
                }
            }

            Log.d(TAG, "Requesting location update from $provider")

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")
                    cachedLocation = location
                    lastUpdateTime = System.currentTimeMillis()
                    callback(location)
                    // Remover listener después de obtener ubicación
                    try {
                        locationManager.removeUpdates(this)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing location updates", e)
                    }
                }

                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {
                    callback(cachedLocation)
                }
            }

            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException requesting location", e)
            callback(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting location", e)
            callback(cachedLocation)
        }
    }

    /**
     * Obtiene la ubicación cacheada.
     */
    fun getCachedLocation(): Location? = cachedLocation

    /**
     * Verifica si la ubicación cacheada es reciente (menos de 30 min).
     */
    fun isCachedLocationFresh(): Boolean {
        return cachedLocation != null && 
               (System.currentTimeMillis() - lastUpdateTime) < LOCATION_UPDATE_INTERVAL
    }
}
