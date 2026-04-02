# Changelog

Todos los cambios notables de este proyecto se documentan en este archivo.

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/).

---

## [1.3.1] - 2026-04-02

### Corregido
- **Bug permisos fantasma**: Modal de permisos aparecía después de suspensión aunque los permisos estuvieran otorgados
  - Agregado delay de 500ms para dar tiempo a AccessibilityService a reactivarse
- **Bug iconos en negro**: Iconos de apps se mostraban en negro después de suspensión prolongada
  - Validación automática y recarga de iconos corruptos en `onResume()`
- **Seguridad**: Credenciales de firma removidas del código fuente
  - Migradas a `keystore.properties` local (no trackeado por git)
  - Soporte para variables de entorno en CI/CD

### Técnico
- Delay en validación de permisos para evitar race conditions
- Validación de referencias de `Drawable` invalidadas por suspensión del sistema
- Refactor de configuración de signing para mejorar seguridad

---

## [1.3.0] - 2026-02-22

### Agregado
- **Screensaver**: Slideshow de fotos con timer de inactividad de 5 minutos
- DreamService para activación automática del sistema
- Overlay manual para iniciar screensaver desde el launcher

### Técnico
- Timer de inactividad configurable en MainActivity
- ScreensaverHelper para coordinación entre DreamService y overlay

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
