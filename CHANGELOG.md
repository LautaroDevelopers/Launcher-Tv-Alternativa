# Changelog

Todos los cambios notables de este proyecto se documentan en este archivo.

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/).

---

## [1.2.0] - 2026-02-03

### Agregado
- **Widget de Clima**: Muestra temperatura actual e icono del clima en el header
- Ubicación por GPS para obtener clima preciso
- 10 iconos de clima (soleado, nublado, lluvia, nieve, tormenta, etc.)
- Cache de clima de 15 minutos para optimizar llamadas a la API

### Técnico
- Integración con Open-Meteo API (gratis, sin API key)
- `LocationHelper` para obtener coordenadas GPS
- `WeatherRepository` para consultar y cachear datos del clima
- `minSdk` subido a 23 (Android 6.0+) para compatibilidad con APIs modernas

### Corregido
- Errores de lint por APIs que requerían SDK 23+
- Fix en `RequestUsageStatsActivity` para compatibilidad con API < 29

---

## [1.1.0] - 2026-02-02

### Agregado
- **Overlay Global**: Panel de configuración que aparece sobre cualquier app
- **AccessibilityService**: Intercepta teclas globalmente (TV_INPUT = 178)
- **Sistema de Permisos**: Modal obligatorio para SYSTEM_ALERT_WINDOW y Accessibility
- Panel lateral con opciones: WiFi, Acerca del Launcher
- Tecla BACK cierra el overlay correctamente
- Mismo botón (178) abre/cierra el panel (toggle)

### Técnico
- `GlobalKeyService` para interceptar keyevents globalmente
- `SettingsOverlayService` con TYPE_APPLICATION_OVERLAY
- `PermissionsDialogFragment` no cancelable hasta otorgar permisos
- Variable `isOverlayVisible` compartida para coordinar BACK

---

## [1.0.0] - 2026-02-01

### Agregado
- Launcher inicial para Android TV
- Header compacto: título, chip versión, reloj, fecha
- Botones WiFi (con estado de conexión) y Ajustes
- Cajón de apps con detección automática de apps TV
- Blacklist para ocultar apps del sistema
- Cards con efecto glow azul en focus
- Sistema de actualizaciones desde GitHub Releases
- Dialog modal de actualización (no se cierra con BACK)
- Panel "Acerca del Launcher" al clickear chip de versión
- Paleta de colores azul Material Design

### Técnico
- NetworkCallback moderno (sin BroadcastReceiver deprecado)
- FileProvider para instalación de APKs (Android 7+)
- Guideline al 38% para posicionar el cajón de apps
