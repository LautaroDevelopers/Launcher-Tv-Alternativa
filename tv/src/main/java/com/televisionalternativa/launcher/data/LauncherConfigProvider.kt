package com.televisionalternativa.launcher.data

import android.content.Context
import com.televisionalternativa.launcher.domain.*
import org.json.JSONObject
import java.io.IOException

/**
 * Proveedor de configuración del Launcher.
 * 
 * AHORA: Lee de assets/launcher_config.json (Mock local)
 * DESPUES: Lo cambiás a Retrofit/Ktor y lee de tu API real
 * 
 * El resto del código no se entera del cambio.
 */
object LauncherConfigProvider {

    private const val CONFIG_FILE = "launcher_config.json"

    fun loadConfig(context: Context): LauncherConfig? {
        return try {
            val jsonString = loadJsonFromAssets(context, CONFIG_FILE)
            parseConfig(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadJsonFromAssets(context: Context, fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use { it.readText() }
    }

    private fun parseConfig(jsonString: String): LauncherConfig {
        val json = JSONObject(jsonString)

        // Parsear Branding
        val brandingJson = json.getJSONObject("branding")
        val branding = Branding(
            backgroundUrl = brandingJson.optString("backgroundUrl", null),
            logoUrl = brandingJson.optString("logoUrl", null),
            primaryColorHex = brandingJson.optString("primaryColorHex", "#000000")
        )

        // Parsear Maintenance (opcional)
        val maintenance = if (json.has("maintenance") && !json.isNull("maintenance")) {
            val maintJson = json.getJSONObject("maintenance")
            MaintenanceInfo(
                isEnabled = maintJson.optBoolean("isEnabled", false),
                title = maintJson.optString("title", null),
                message = maintJson.optString("message", null),
                canSkip = maintJson.optBoolean("canSkip", true)
            )
        } else null

        // Parsear Sections
        val sectionsArray = json.getJSONArray("sections")
        val sections = mutableListOf<LauncherSection>()

        for (i in 0 until sectionsArray.length()) {
            val sectionJson = sectionsArray.getJSONObject(i)
            
            val sectionType = try {
                SectionType.valueOf(sectionJson.getString("type"))
            } catch (e: IllegalArgumentException) {
                SectionType.CONTENT_ROW // Default si no reconoce el tipo
            }

            val itemsArray = sectionJson.getJSONArray("items")
            val items = mutableListOf<LauncherItem>()

            for (j in 0 until itemsArray.length()) {
                val itemJson = itemsArray.getJSONObject(j)
                
                val actionType = try {
                    ActionType.valueOf(itemJson.getString("actionType"))
                } catch (e: IllegalArgumentException) {
                    ActionType.DEEP_LINK
                }

                items.add(
                    LauncherItem(
                        id = itemJson.getString("id"),
                        title = itemJson.getString("title"),
                        imageUrl = itemJson.optString("imageUrl", null),
                        actionType = actionType,
                        actionData = itemJson.getString("actionData")
                    )
                )
            }

            sections.add(
                LauncherSection(
                    id = sectionJson.getString("id"),
                    title = sectionJson.optString("title", null),
                    type = sectionType,
                    items = items
                )
            )
        }

        return LauncherConfig(
            branding = branding,
            maintenance = maintenance,
            sections = sections
        )
    }
}
