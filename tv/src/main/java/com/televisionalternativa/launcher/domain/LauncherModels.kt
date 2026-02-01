package com.televisionalternativa.launcher.domain

import java.io.Serializable

/**
 * La respuesta principal de tu API.
 * Controla todo el aspecto y contenido del Launcher.
 */
data class LauncherConfig(
    val branding: Branding,
    val maintenance: MaintenanceInfo?,
    val sections: List<LauncherSection>
) : Serializable

data class Branding(
    val backgroundUrl: String?,
    val logoUrl: String?,
    val primaryColorHex: String? = "#000000"
) : Serializable

data class MaintenanceInfo(
    val isEnabled: Boolean = false,
    val title: String?,
    val message: String?,
    val canSkip: Boolean = true
) : Serializable

/**
 * Define una "Fila" o sección en tu pantalla de TV.
 * Usamos un Enum o String para el tipo (HERO, APPS, VOD, LIVE).
 */
data class LauncherSection(
    val id: String,
    val title: String?,
    val type: SectionType,
    val items: List<LauncherItem>
) : Serializable

enum class SectionType {
    HERO_BANNER,  // El carrusel grande arriba
    APP_ROW,      // Fila de iconos de apps (Netflix, YT)
    CONTENT_ROW,  // Fila de contenido (Pelis, Canales)
    SETTINGS      // Fila de ajustes
}

/**
 * Un item individual en la pantalla.
 * Puede ser una App para abrir o una Película para reproducir.
 */
data class LauncherItem(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val actionType: ActionType,
    val actionData: String // El package name o el Deep Link
) : Serializable

enum class ActionType {
    OPEN_APP,       // Abrir una app instalada (ej: com.netflix.ninja)
    DEEP_LINK,      // Navegar dentro de tu app de TV (ej: tv://channel/13)
    SYSTEM_INTENT   // Abrir settings de android
}
